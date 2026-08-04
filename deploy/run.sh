#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_FILE="${ROOT}/deploy/deploy.conf"
LOCAL_CONFIG_FILE="${ROOT}/deploy/deploy.local.conf"
COMMON_LIB_FILE="${ROOT}/deploy/lib/deploy-common.sh"
RESON8_CALLBACKS_FILE="${ROOT}/deploy/openshift/deploy-reson8.sh"

if [[ -f "${CONFIG_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${CONFIG_FILE}"
fi
if [[ -f "${LOCAL_CONFIG_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${LOCAL_CONFIG_FILE}"
fi

# shellcheck disable=SC1091
source "${COMMON_LIB_FILE}"
# shellcheck disable=SC1091
source "${RESON8_CALLBACKS_FILE}"

BASE_NS="${BASE_NS:-reson8}"
SHARED_BUILDER_NS="${SHARED_BUILDER_NS:-${BASE_NS}-build}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
CA_WAIT_ATTEMPTS="${CA_WAIT_ATTEMPTS:-30}"
AUTO_CREATE_DEV_NAMESPACE="${AUTO_CREATE_DEV_NAMESPACE:-1}"
FORCE_BASE_REBUILD="${FORCE_BASE_REBUILD:-0}"
SYNC_CONFIGMAP="${SYNC_CONFIGMAP:-1}"
DRY_RUN="${DRY_RUN:-0}"
SYNC_CRONJOB_NS="${SYNC_CRONJOB_NS:-openshift-config-managed}"
SYNC_CRONJOB_NAME="${SYNC_CRONJOB_NAME:-sync-entitlements}"
ENTITLEMENT_SECRET="${ENTITLEMENT_SECRET:-etc-pki-entitlement}"
ENTITLEMENT_SYNC_TIMEOUT="${ENTITLEMENT_SYNC_TIMEOUT:-180}"
BASE_IMAGE_SOURCE="${BASE_IMAGE_SOURCE:-auto}" # auto|cluster|local
ALLOW_LOCAL_BASE_FALLBACK="${ALLOW_LOCAL_BASE_FALLBACK:-1}"
LOCAL_BASE_IMAGE_TAG="${LOCAL_BASE_IMAGE_TAG:-localhost/reson8-base:local}"
BASE_IMAGE_TAG="${BASE_IMAGE_TAG:-reson8-base:latest}"

if [[ -z "${MODULE_IDS+x}" || "${#MODULE_IDS[@]}" -eq 0 ]]; then
  MODULE_IDS=("reson8" "disson8")
fi
LOCAL_DEV_MODULE_ID="${LOCAL_DEV_MODULE_ID:-reson8}"

usage() {
  cat <<'EOF'
Usage:
  deploy/run.sh <mode> [--dry-run] [extra maven args...]

Modes:
  local-dev     Run local Quarkus dev mode
  cluster-test  Deploy test lane to ${BASE_NS}-dev-${USER}
  prod          Deploy to ${BASE_NS} (guarded scaffold)

Examples:
  deploy/run.sh local-dev
  deploy/run.sh cluster-test -DskipTests
  deploy/run.sh cluster-test --dry-run
  FORCE_BASE_REBUILD=1 deploy/run.sh cluster-test
  BASE_IMAGE_SOURCE=local deploy/run.sh cluster-test
  BASE_IMAGE_SOURCE=cluster ALLOW_LOCAL_BASE_FALLBACK=0 deploy/run.sh cluster-test
EOF
}

module_prefix() {
  local module_id="$1"
  echo "MOD_${module_id//-/_}"
}

module_get() {
  local module_id="$1"
  local field="$2"
  local default_value="${3:-}"
  local var_name
  var_name="$(module_prefix "${module_id}")_${field}"
  echo "${!var_name:-${default_value}}"
}

module_set_if_unset() {
  local module_id="$1"
  local field="$2"
  local value="$3"
  local var_name
  var_name="$(module_prefix "${module_id}")_${field}"
  if [[ -z "${!var_name:-}" ]]; then
    printf -v "${var_name}" '%s' "${value}"
  fi
}

module_enabled() {
  local module_id="$1"
  local field="$2"
  local default_value="${3:-0}"
  local value
  value="$(module_get "${module_id}" "${field}" "${default_value}")"
  [[ "${value}" == "1" || "${value}" == "true" || "${value}" == "yes" ]]
}

module_invoke_callback_if_set() {
  local module_id="$1"
  local callback_field="$2"
  shift 2
  local callback_name
  callback_name="$(module_get "${module_id}" "${callback_field}" "")"
  if [[ -z "${callback_name}" ]]; then
    return
  fi
  if ! declare -F "${callback_name}" >/dev/null 2>&1; then
    echo "ERROR: callback '${callback_name}' for module '${module_id}' is not defined." >&2
    exit 1
  fi
  "${callback_name}" "${module_id}" "$@"
}

normalize_module_defaults() {
  module_set_if_unset "reson8" "PATH" "reson8"
  module_set_if_unset "reson8" "APP_NAME" "reson8"
  module_set_if_unset "reson8" "MANIFEST_DIR" ".generated/quarkus"
  module_set_if_unset "reson8" "NEEDS_BUILDER" "1"
  module_set_if_unset "reson8" "NEEDS_ROUTE_CHECK" "1"
  module_set_if_unset "reson8" "WAIT_FOR_ROLLOUT" "1"
  module_set_if_unset "reson8" "PRE_DEPLOY_CB" "reson8_pre_deploy_callback"
  module_set_if_unset "reson8" "POST_APPLY_CB" "reson8_post_apply_callback"
  module_set_if_unset "reson8" "OIDC_CLIENT_ID_FROM_SA" "1"
  module_set_if_unset "reson8" "DEPLOY_MODE" "quarkus-deploy"

  module_set_if_unset "disson8" "PATH" "disson8"
  module_set_if_unset "disson8" "APP_NAME" "disson8"
  module_set_if_unset "disson8" "MANIFEST_DIR" ".generated/quarkus"
  module_set_if_unset "disson8" "NEEDS_BUILDER" "0"
  module_set_if_unset "disson8" "NEEDS_ROUTE_CHECK" "1"
  module_set_if_unset "disson8" "WAIT_FOR_ROLLOUT" "1"
  module_set_if_unset "disson8" "OIDC_CLIENT_ID_FROM_SA" "0"
  module_set_if_unset "disson8" "DEPLOY_MODE" "quarkus-deploy"
}

validate_module_registry() {
  local module_id
  local module_path
  local app_name
  local manifest_dir
  for module_id in "${MODULE_IDS[@]}"; do
    module_path="$(module_get "${module_id}" "PATH" "")"
    app_name="$(module_get "${module_id}" "APP_NAME" "")"
    manifest_dir="$(module_get "${module_id}" "MANIFEST_DIR" "")"
    if [[ -z "${module_path}" || -z "${app_name}" || -z "${manifest_dir}" ]]; then
      echo "ERROR: module '${module_id}' must define PATH, APP_NAME, and MANIFEST_DIR." >&2
      exit 1
    fi
    module_set_if_unset "${module_id}" "MANIFEST_DIR" "$(deploy_normalize_manifest_dir "${module_path}" "${manifest_dir}")"
  done
}

cluster_lane_requires_builder() {
  local module_id
  for module_id in "${MODULE_IDS[@]}"; do
    if module_enabled "${module_id}" "NEEDS_BUILDER" "0"; then
      return 0
    fi
  done
  return 1
}

validate_base_image_source() {
  case "${BASE_IMAGE_SOURCE}" in
    auto|cluster|local)
      ;;
    *)
      echo "ERROR: BASE_IMAGE_SOURCE must be one of: auto, cluster, local (got '${BASE_IMAGE_SOURCE}')." >&2
      exit 1
      ;;
  esac
}

reconcile_base_image() {
  local runtime_ns="$1"

  if [[ "${DRY_RUN}" == "1" ]]; then
    case "${BASE_IMAGE_SOURCE}" in
      cluster|auto)
        deploy_preview_base_builder_changes "${runtime_ns}" "${SHARED_BUILDER_NS}" "${SYNC_CRONJOB_NS}" "${ENTITLEMENT_SECRET}"
        ;;
      local)
        echo "Dry-run: local base image build would run:"
        echo "  podman build -f reson8/src/main/docker/Containerfile.gstreamer-base -t ${LOCAL_BASE_IMAGE_TAG} reson8"
        echo "  podman push <internal-registry-host>/${SHARED_BUILDER_NS}/${BASE_IMAGE_TAG}"
        ;;
    esac
    return
  fi

  case "${BASE_IMAGE_SOURCE}" in
    cluster)
      deploy_reconcile_base_builder \
        "${runtime_ns}" \
        "${SHARED_BUILDER_NS}" \
        "${FORCE_BASE_REBUILD}" \
        "${SYNC_CRONJOB_NS}" \
        "${ENTITLEMENT_SECRET}" \
        "${SYNC_CRONJOB_NAME}" \
        "${ENTITLEMENT_SYNC_TIMEOUT}"
      ;;
    local)
      deploy_build_and_push_base_locally "${SHARED_BUILDER_NS}" "${LOCAL_BASE_IMAGE_TAG}" "${BASE_IMAGE_TAG}"
      ;;
    auto)
      if ! (
        deploy_reconcile_base_builder \
          "${runtime_ns}" \
          "${SHARED_BUILDER_NS}" \
          "${FORCE_BASE_REBUILD}" \
          "${SYNC_CRONJOB_NS}" \
          "${ENTITLEMENT_SECRET}" \
          "${SYNC_CRONJOB_NAME}" \
          "${ENTITLEMENT_SYNC_TIMEOUT}"
      ); then
        if [[ "${ALLOW_LOCAL_BASE_FALLBACK}" == "1" || "${ALLOW_LOCAL_BASE_FALLBACK}" == "true" || "${ALLOW_LOCAL_BASE_FALLBACK}" == "yes" ]]; then
          echo "Cluster base build failed; falling back to local subscribed-host base build+push."
          deploy_build_and_push_base_locally "${SHARED_BUILDER_NS}" "${LOCAL_BASE_IMAGE_TAG}" "${BASE_IMAGE_TAG}"
        else
          echo "ERROR: cluster base build failed and local fallback is disabled (ALLOW_LOCAL_BASE_FALLBACK=${ALLOW_LOCAL_BASE_FALLBACK})." >&2
          exit 1
        fi
      fi
      ;;
  esac
}

deploy_cluster_module() {
  local ns="$1"
  local module_id="$2"
  shift 2
  local extra_maven_args=("$@")
  local module_path
  local app_name
  local manifest_dir
  local manifest_file=""
  local deploy_mode
  local maven_args=()

  module_path="$(module_get "${module_id}" "PATH")"
  app_name="$(module_get "${module_id}" "APP_NAME")"
  manifest_dir="$(module_get "${module_id}" "MANIFEST_DIR")"
  deploy_mode="$(module_get "${module_id}" "DEPLOY_MODE" "manifest-apply")"

  module_invoke_callback_if_set "${module_id}" "PRE_DEPLOY_CB" "${ns}"

  if [[ "${deploy_mode}" == "quarkus-deploy" && "${DRY_RUN}" != "1" ]]; then
    maven_args=(package -pl "${module_path}"
      "-Dquarkus.openshift.deploy=true"
      "-Dquarkus.openshift.namespace=${ns}"
      "-Dquarkus.container-image.group=${ns}"
      "-Dquarkus.openshift.name=${app_name}"
    )
    if module_enabled "${module_id}" "OIDC_CLIENT_ID_FROM_SA" "0"; then
      maven_args+=("-Dquarkus.openshift.env.vars.OIDC_CLIENT_ID=system:serviceaccount:${ns}:${app_name}")
    fi
    "${ROOT}/mvnw" "${maven_args[@]}" "${extra_maven_args[@]}"
  else
    maven_args=(package -pl "${module_path}"
      "-Dquarkus.openshift.deploy=false"
      "-Dquarkus.container-image.build=false"
      "-Dquarkus.container-image.push=false"
      "-Dquarkus.kubernetes.output-directory=${manifest_dir}"
      "-Dquarkus.openshift.namespace=${ns}"
      "-Dquarkus.container-image.group=${ns}"
      "-Dquarkus.openshift.name=${app_name}"
    )
    if module_enabled "${module_id}" "OIDC_CLIENT_ID_FROM_SA" "0"; then
      maven_args+=("-Dquarkus.openshift.env.vars.OIDC_CLIENT_ID=system:serviceaccount:${ns}:${app_name}")
    fi
    "${ROOT}/mvnw" "${maven_args[@]}" "${extra_maven_args[@]}"

    manifest_file="$(deploy_resolve_manifest_file "${ROOT}" "${module_path}" "${manifest_dir}")"
    if [[ -z "${manifest_file}" ]]; then
      echo "ERROR: generated OpenShift manifest not found for module '${module_id}' under ${module_path}/${manifest_dir}" >&2
      exit 1
    fi

    if [[ "${DRY_RUN}" == "1" ]]; then
      echo "Dry-run: diffing ${module_id} manifest ${manifest_file}"
      deploy_run_oc_diff -n "${ns}" -f "${manifest_file}"
    else
      oc apply -n "${ns}" -f "${manifest_file}"
    fi
  fi

  if [[ "${DRY_RUN}" != "1" ]]; then
    if module_enabled "${module_id}" "WAIT_FOR_ROLLOUT" "1"; then
      deploy_wait_for_rollout "${ns}" "${app_name}" "${ROLLOUT_TIMEOUT}"
    fi
    if module_enabled "${module_id}" "NEEDS_ROUTE_CHECK" "0"; then
      deploy_check_route_health "${ns}" "${app_name}" "${module_id} route"
    fi
  fi

  module_invoke_callback_if_set "${module_id}" "POST_APPLY_CB" "${ns}"
}

run_cluster_lane() {
  local ns="$1"
  shift
  local extra_maven_args=("$@")
  local module_id

  deploy_ensure_namespace "${ns}" "${AUTO_CREATE_DEV_NAMESPACE}" "${DRY_RUN}"
  oc project "${ns}" >/dev/null

  if cluster_lane_requires_builder; then
    reconcile_base_image "${ns}"
  fi

  for module_id in "${MODULE_IDS[@]}"; do
    deploy_cluster_module "${ns}" "${module_id}" "${extra_maven_args[@]}"
  done
  echo "Cluster lane deploy complete for namespace ${ns} (${MODULE_IDS[*]})."
}

main() {
  if [[ $# -lt 1 ]]; then
    usage
    exit 1
  fi

  local mode="$1"
  shift || true
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run)
        DRY_RUN=1
        shift
        ;;
      --)
        shift
        break
        ;;
      *)
        break
        ;;
    esac
  done

  normalize_module_defaults
  validate_module_registry
  validate_base_image_source

  deploy_require_cmd oc
  deploy_ensure_oc_login

  case "${mode}" in
    local-dev)
      local local_dev_module_path
      local_dev_module_path="$(module_get "${LOCAL_DEV_MODULE_ID}" "PATH" "reson8")"
      echo "Running local dev mode."
      exec "${ROOT}/mvnw" -pl "${local_dev_module_path}" quarkus:dev "$@"
      ;;
    cluster-test)
      run_cluster_lane "$(deploy_cluster_test_namespace "${BASE_NS}")" "$@"
      ;;
    prod)
      if [[ "${CONFIRM_PROD_DEPLOY:-0}" != "1" ]]; then
        echo "ERROR: prod mode is scaffolded only for now." >&2
        echo "Set CONFIRM_PROD_DEPLOY=1 to intentionally run the current shared flow." >&2
        exit 1
      fi
      run_cluster_lane "${BASE_NS}" "$@"
      ;;
    *)
      echo "ERROR: unknown mode '${mode}'" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
