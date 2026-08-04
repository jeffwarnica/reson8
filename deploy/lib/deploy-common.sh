#!/usr/bin/env bash

# Shared deploy helpers for OpenShift lane scripts.
# This file is sourced by deploy/run.sh and deploy/openshift/* wrappers.

if [[ -z "${ROOT:-}" ]]; then
  ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi

deploy_require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: required command not found: ${cmd}" >&2
    exit 1
  fi
}

deploy_ensure_oc_login() {
  if ! oc whoami >/dev/null 2>&1; then
    echo "ERROR: oc is not logged in. Run 'oc login' first." >&2
    exit 1
  fi
}

deploy_run_oc_diff() {
  local exit_code=0
  set +e
  oc diff "$@"
  exit_code=$?
  set -e
  if [[ "${exit_code}" -gt 1 ]]; then
    echo "ERROR: oc diff failed with exit code ${exit_code}." >&2
    exit "${exit_code}"
  fi
}

deploy_oc_apply_or_diff_stream() {
  local ns="$1"
  local dry_run="${2:-0}"
  if [[ "${dry_run}" == "1" ]]; then
    deploy_run_oc_diff -n "${ns}" -f -
  else
    oc apply -n "${ns}" -f -
  fi
}

deploy_cluster_test_namespace() {
  local base_ns="$1"
  if [[ -z "${USER:-}" ]]; then
    echo "ERROR: USER is not set; cannot derive cluster-test namespace." >&2
    exit 1
  fi
  echo "${base_ns}-dev-${USER}"
}

deploy_ensure_namespace() {
  local ns="$1"
  local auto_create="${2:-1}"
  local dry_run="${3:-0}"
  if oc get namespace "${ns}" >/dev/null 2>&1; then
    return
  fi
  if [[ "${dry_run}" == "1" ]]; then
    echo "ERROR: namespace ${ns} does not exist. --dry-run will not create namespaces." >&2
    echo "       Create it first or run without --dry-run." >&2
    exit 1
  fi
  if [[ "${auto_create}" == "1" ]]; then
    echo "Creating namespace ${ns}"
    oc create namespace "${ns}" >/dev/null
    return
  fi
  echo "ERROR: namespace ${ns} does not exist and auto-create is disabled." >&2
  exit 1
}

deploy_normalize_manifest_dir() {
  local module_path="$1"
  local raw_dir="$2"
  if [[ -z "${raw_dir}" ]]; then
    echo ".generated/quarkus"
    return
  fi
  if [[ "${raw_dir}" == "${module_path}/"* ]]; then
    echo "${raw_dir#"${module_path}/"}"
    return
  fi
  if [[ "${raw_dir}" == ./* ]]; then
    echo "${raw_dir#./}"
    return
  fi
  echo "${raw_dir}"
}

deploy_resolve_manifest_file() {
  local root="$1"
  local module_path="$2"
  local manifest_rel_dir="$3"
  local module_base="${root}/${module_path}/${manifest_rel_dir}"

  if [[ -f "${module_base}/openshift.yml" ]]; then
    echo "${module_base}/openshift.yml"
    return
  fi
  if [[ -f "${module_base}/kubernetes/openshift.yml" ]]; then
    echo "${module_base}/kubernetes/openshift.yml"
    return
  fi

  echo ""
}

deploy_wait_for_rollout() {
  local ns="$1"
  local app_name="$2"
  local rollout_timeout="$3"
  oc rollout status "deployment/${app_name}" -n "${ns}" --timeout "${rollout_timeout}"
  oc wait --for=condition=Available "deployment/${app_name}" -n "${ns}" --timeout "${rollout_timeout}" >/dev/null
}

deploy_check_route_health() {
  local ns="$1"
  local app_name="$2"
  local label="${3:-Route}"
  local route_host
  route_host="$(oc get route "${app_name}" -n "${ns}" -o jsonpath='{.spec.host}' 2>/dev/null || true)"
  if [[ -n "${route_host}" ]] && command -v curl >/dev/null 2>&1; then
    echo "${label} health check: https://${route_host}/q/health/ready"
    curl -skf "https://${route_host}/q/health/ready" >/dev/null
  elif [[ -n "${route_host}" ]]; then
    echo "curl not found; skipping ${label} health check for ${route_host}."
  fi
}

deploy_apply_builder_templates() {
  local target_ns="$1"
  local runtime_ns="$2"
  local sync_cronjob_ns="$3"
  local entitlement_secret="$4"
  local dry_run="${5:-0}"
  local template_dir="${ROOT}/deploy/openshift/cluster_build_config"
  local template

  echo "Applying cluster build templates from ${template_dir}"
  for template in "${template_dir}"/*.yaml.tmpl; do
    echo "  - $(basename "${template}")"
    if [[ "${dry_run}" == "1" ]]; then
      TARGET_NS="${target_ns}" \
      RUNTIME_NS="${runtime_ns}" \
      SYNC_CRONJOB_NS="${sync_cronjob_ns}" \
      ENTITLEMENT_SECRET="${entitlement_secret}" \
      deploy_run_oc_diff -f <(
        envsubst '${TARGET_NS} ${RUNTIME_NS} ${SYNC_CRONJOB_NS} ${ENTITLEMENT_SECRET}' < "${template}"
      )
    else
      TARGET_NS="${target_ns}" \
      RUNTIME_NS="${runtime_ns}" \
      SYNC_CRONJOB_NS="${sync_cronjob_ns}" \
      ENTITLEMENT_SECRET="${entitlement_secret}" \
      envsubst '${TARGET_NS} ${RUNTIME_NS} ${SYNC_CRONJOB_NS} ${ENTITLEMENT_SECRET}' < "${template}" | oc apply -f -
    fi
  done
}

deploy_ensure_entitlement_secret_now() {
  local target_ns="$1"
  local entitlement_secret="$2"
  local sync_cronjob_ns="$3"
  local sync_cronjob_name="$4"
  local entitlement_sync_timeout="$5"

  if oc get secret "${entitlement_secret}" -n "${target_ns}" >/dev/null 2>&1; then
    echo "Entitlement secret '${entitlement_secret}' already present in '${target_ns}'."
    return
  fi

  local job_name="${sync_cronjob_name}-now-$(date +%s)"
  echo "Triggering one-shot entitlement sync job '${job_name}'..."
  oc create job --from=cronjob/"${sync_cronjob_name}" "${job_name}" -n "${sync_cronjob_ns}"
  oc wait --for=condition=complete --timeout="${entitlement_sync_timeout}s" \
    job/"${job_name}" -n "${sync_cronjob_ns}"

  if ! oc get secret "${entitlement_secret}" -n "${target_ns}" >/dev/null 2>&1; then
    echo "ERROR: '${entitlement_secret}' was not copied to '${target_ns}' after one-shot sync." >&2
    echo "       Check job logs: oc logs -n ${sync_cronjob_ns} job/${job_name}" >&2
    exit 1
  fi
  echo "Entitlement secret '${entitlement_secret}' synced to '${target_ns}'."
}

deploy_builder_bootstrap() {
  local target_ns="$1"
  local runtime_ns="$2"
  local skip_initial_build="${3:-0}"
  local entitlement_secret="${4:-etc-pki-entitlement}"
  local sync_cronjob_ns="${5:-openshift-config-managed}"
  local sync_cronjob_name="${6:-sync-entitlements}"
  local entitlement_sync_timeout="${7:-180}"

  deploy_require_cmd oc
  deploy_require_cmd envsubst

  cd "${ROOT}"

  if oc get namespace "${target_ns}" >/dev/null 2>&1; then
    echo "Builder namespace '${target_ns}' already exists."
  else
    echo "Creating builder namespace '${target_ns}'..."
    oc create namespace "${target_ns}"
  fi

  deploy_apply_builder_templates "${target_ns}" "${runtime_ns}" "${sync_cronjob_ns}" "${entitlement_secret}" "0"

  echo "Refreshing imported UBI stream..."
  oc import-image ubi10-openjdk-21:latest -n "${target_ns}" --confirm >/dev/null 2>&1 || true

  deploy_ensure_entitlement_secret_now \
    "${target_ns}" "${entitlement_secret}" "${sync_cronjob_ns}" "${sync_cronjob_name}" "${entitlement_sync_timeout}"

  if [[ "${skip_initial_build}" != "1" ]]; then
    echo "Starting initial reson8-base build in '${target_ns}'..."
    oc start-build reson8-base -n "${target_ns}" --wait
  else
    echo "Skipping initial build (SKIP_INITIAL_BUILD=1)."
  fi
}

deploy_compute_base_template_hash() {
  local runtime_ns="$1"
  local builder_ns="$2"
  local sync_cronjob_ns="$3"
  local entitlement_secret="$4"
  local template="${ROOT}/deploy/openshift/cluster_build_config/020-reson8-base-build.yaml.tmpl"

  TARGET_NS="${builder_ns}" \
  RUNTIME_NS="${runtime_ns}" \
  SYNC_CRONJOB_NS="${sync_cronjob_ns}" \
  ENTITLEMENT_SECRET="${entitlement_secret}" \
  envsubst '${TARGET_NS} ${RUNTIME_NS} ${SYNC_CRONJOB_NS} ${ENTITLEMENT_SECRET}' < "${template}" \
    | sha256sum | awk '{print $1}'
}

deploy_preview_base_builder_changes() {
  local runtime_ns="$1"
  local builder_ns="$2"
  local sync_cronjob_ns="$3"
  local entitlement_secret="$4"
  local local_hash cluster_hash
  local image_tag_exists=0
  local template_dir="${ROOT}/deploy/openshift/cluster_build_config"
  local template

  echo "Dry-run: skipping server-side builder template diffs (no create checks)."
  echo "Dry-run: builder templates that would be reconciled from ${template_dir}:"
  for template in "${template_dir}"/*.yaml.tmpl; do
    echo "  - $(basename "${template}")"
  done

  local_hash="$(deploy_compute_base_template_hash "${runtime_ns}" "${builder_ns}" "${sync_cronjob_ns}" "${entitlement_secret}")"
  cluster_hash="$(oc get buildconfig reson8-base -n "${builder_ns}" \
    -o jsonpath='{.metadata.annotations.reson8\/base-template-sha}' 2>/dev/null || true)"
  if oc get istag reson8-base:latest -n "${builder_ns}" >/dev/null 2>&1; then
    image_tag_exists=1
  fi

  if [[ -z "${cluster_hash}" || "${cluster_hash}" != "${local_hash}" || "${image_tag_exists}" != "1" ]]; then
    if [[ "${image_tag_exists}" != "1" ]]; then
      echo "Dry-run: base build WOULD run because reson8-base:latest image tag is missing."
    fi
    echo "Dry-run: base build WOULD run (template hash changed: '${cluster_hash:-<none>}' -> '${local_hash}')."
  else
    echo "Dry-run: base build would be skipped (template hash unchanged: '${local_hash}')."
  fi
}

deploy_reconcile_base_builder() {
  local runtime_ns="$1"
  local builder_ns="$2"
  local force_base_rebuild="${3:-0}"
  local sync_cronjob_ns="$4"
  local entitlement_secret="$5"
  local sync_cronjob_name="${6:-sync-entitlements}"
  local entitlement_sync_timeout="${7:-180}"
  local local_hash cluster_hash
  local image_tag_exists=0

  deploy_builder_bootstrap \
    "${builder_ns}" "${runtime_ns}" "1" "${entitlement_secret}" \
    "${sync_cronjob_ns}" "${sync_cronjob_name}" "${entitlement_sync_timeout}"

  local_hash="$(deploy_compute_base_template_hash "${runtime_ns}" "${builder_ns}" "${sync_cronjob_ns}" "${entitlement_secret}")"
  cluster_hash="$(oc get buildconfig reson8-base -n "${builder_ns}" \
    -o jsonpath='{.metadata.annotations.reson8\/base-template-sha}' 2>/dev/null || true)"
  if oc get istag reson8-base:latest -n "${builder_ns}" >/dev/null 2>&1; then
    image_tag_exists=1
  fi

  if [[ "${force_base_rebuild}" == "1" || -z "${cluster_hash}" || "${cluster_hash}" != "${local_hash}" || "${image_tag_exists}" != "1" ]]; then
    if [[ "${image_tag_exists}" != "1" ]]; then
      echo "Rebuilding reson8-base in ${builder_ns} (output image tag reson8-base:latest is missing)."
    else
      echo "Rebuilding reson8-base in ${builder_ns} (template hash changed or forced)."
    fi
    oc start-build reson8-base -n "${builder_ns}" --wait
    oc annotate buildconfig reson8-base -n "${builder_ns}" \
      "reson8/base-template-sha=${local_hash}" --overwrite >/dev/null
  else
    echo "reson8-base is up to date (hash ${local_hash}); skipping base rebuild."
  fi
}

deploy_build_and_push_base_locally() {
  local builder_ns="$1"
  local local_image_tag="${2:-localhost/reson8-base:local}"
  local target_image_tag="${3:-reson8-base:latest}"
  local registry_host=""
  local registry_user=""
  local registry_token=""
  local remote_image=""

  deploy_require_cmd oc
  deploy_require_cmd podman

  cd "${ROOT}"

  echo "Building base image locally from subscribed host repositories..."
  podman build --pull=always --no-cache \
    -f "${ROOT}/reson8/src/main/docker/Containerfile.gstreamer-base" \
    -t "${local_image_tag}" \
    "${ROOT}/reson8"

  registry_host="$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}' 2>/dev/null || true)"
  if [[ -z "${registry_host}" ]]; then
    echo "ERROR: could not resolve internal registry route host (openshift-image-registry/default-route)." >&2
    echo "       Ensure the default route exists or provide an equivalent registry endpoint." >&2
    exit 1
  fi

  registry_user="$(oc whoami)"
  registry_token="$(oc whoami -t)"
  echo "Logging into internal registry ${registry_host} as ${registry_user}..."
  podman login -u "${registry_user}" -p "${registry_token}" "${registry_host}"

  remote_image="${registry_host}/${builder_ns}/${target_image_tag}"
  echo "Pushing local base image to ${remote_image}..."
  podman tag "${local_image_tag}" "${remote_image}"
  podman push "${remote_image}"

  echo "Local base image push complete: ${remote_image}"
}
