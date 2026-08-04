#!/usr/bin/env bash
# Reson8-specific deploy callbacks used by deploy/run.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck disable=SC1091
source "${ROOT}/deploy/lib/deploy-common.sh"

reson8_sync_configmap_if_enabled() {
  local ns="$1"
  local dry_run="${2:-0}"
  local sync_configmap="${3:-1}"
  local overlay="${ROOT}/deploy/openshift/runtime_config/application-cluster-overlay.yaml"
  local source_yml="${ROOT}/reson8/src/main/resources/application.yml"
  local merged_yml=""

  if [[ "${sync_configmap}" != "1" ]]; then
    return
  fi

  export SOURCE_YML="${source_yml}" OVERLAY="${overlay}"
  if [[ -f "${overlay}" ]]; then
    echo "Merging cluster overlay into reson8-config: ${overlay}"
    merged_yml="$(python3 - <<'PYEOF'
import yaml, os

source = os.environ.get("SOURCE_YML")
overlay = os.environ.get("OVERLAY")

with open(source, encoding="utf-8") as f:
    base = yaml.safe_load(f) or {}

with open(overlay, encoding="utf-8") as f:
    over = yaml.safe_load(f) or {}

def deep_merge(b, o):
    for k, v in o.items():
        if k in b and isinstance(b[k], dict) and isinstance(v, dict):
            deep_merge(b[k], v)
        else:
            b[k] = v

deep_merge(base, over)
print(yaml.dump(base, default_flow_style=False, allow_unicode=True))
PYEOF
)"
    echo "${merged_yml}" | oc create configmap reson8-config \
      --from-file=application.yaml=/dev/stdin \
      -n "${ns}" \
      --dry-run=client -o yaml | deploy_oc_apply_or_diff_stream "${ns}" "${dry_run}"
  else
    echo "No cluster overlay found at ${overlay} — syncing application.yml only."
    echo "(Copy deploy/openshift/runtime_config/application-cluster-overlay.example.yaml to ${overlay} and fill in cluster values.)"
    oc create configmap reson8-config \
      --from-file=application.yaml="${source_yml}" \
      -n "${ns}" \
      --dry-run=client -o yaml | deploy_oc_apply_or_diff_stream "${ns}" "${dry_run}"
  fi
}

reson8_verify_ca_bundle_if_enabled() {
  local ns="$1"
  local dry_run="${2:-0}"
  local ca_wait_attempts="${3:-30}"
  local ca_bundle=""
  local injected=0

  if [[ "${dry_run}" == "1" ]]; then
    echo "Dry-run enabled: skipping CA readiness checks."
    return
  fi

  echo "Verifying openshift-service-ca.crt ConfigMap (auto-created by OpenShift)..."
  if ! oc get configmap openshift-service-ca.crt -n "${ns}" &>/dev/null; then
    echo "ERROR: ConfigMap openshift-service-ca.crt not found in namespace ${ns}." >&2
    echo "       This ConfigMap is auto-created by OpenShift in every namespace." >&2
    echo "       Its absence indicates a cluster or namespace setup problem." >&2
    exit 1
  fi
  echo "openshift-service-ca.crt is present."

  echo "Waiting for reson8-trusted-ca-bundle ingress CA injection (up to $((ca_wait_attempts * 3)) s)..."
  for i in $(seq 1 "${ca_wait_attempts}"); do
    ca_bundle="$(oc get configmap reson8-trusted-ca-bundle -n "${ns}" \
      -o jsonpath='{.data.ca-bundle\.crt}' 2>/dev/null || true)"
    if [[ -n "${ca_bundle}" ]]; then
      echo "reson8-trusted-ca-bundle ca-bundle.crt is populated (attempt ${i})."
      injected=1
      break
    fi
    echo "  attempt ${i}/${ca_wait_attempts} — ca-bundle.crt not yet injected, retrying in 3 s..."
    sleep 3
  done

  if [[ "${injected}" == "0" ]]; then
    echo "ERROR: reson8-trusted-ca-bundle ca-bundle.crt was not populated after $((ca_wait_attempts * 3)) s." >&2
    echo "       Ensure the one-time cluster-admin CA setup has been run (see DEPLOY.md):" >&2
    echo "       oc describe configmap reson8-trusted-ca-bundle -n ${ns}" >&2
    exit 1
  fi
}

reson8_pre_deploy_callback() {
  local _module_id="$1"
  local ns="$2"

  if [[ "${APPLY_CLUSTER_RBAC:-0}" == "1" ]]; then
    oc apply -f "${ROOT}/deploy/rbac.yml"
  fi
  reson8_sync_configmap_if_enabled "${ns}" "${DRY_RUN:-0}" "${SYNC_CONFIGMAP:-1}"
}

reson8_post_apply_callback() {
  local _module_id="$1"
  local ns="$2"
  reson8_verify_ca_bundle_if_enabled "${ns}" "${DRY_RUN:-0}" "${CA_WAIT_ATTEMPTS:-30}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "ERROR: direct execution of deploy/openshift/deploy-reson8.sh has been retired." >&2
  echo "Use deploy/run.sh instead:" >&2
  echo "  ./deploy/run.sh local-dev" >&2
  echo "  ./deploy/run.sh cluster-test" >&2
  echo "  CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod" >&2
  exit 1
fi
