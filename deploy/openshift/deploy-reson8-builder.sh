#!/usr/bin/env bash
# Bootstraps/refreshes the in-cluster reson8-base image build namespace.
#
# Creates the builder namespace when missing, then renders/apply all
# deploy/openshift/cluster_build_config/*.yaml.tmpl files via envsubst.
# Templates may use:
#   ${TARGET_NS}  - builder namespace (default: reson8-build)
#   ${RUNTIME_NS} - runtime namespace that must pull reson8-base (default: reson8)
#   ${SYNC_CRONJOB_NS} - namespace where sync cronjob lives (default: openshift-config-managed)
#   ${ENTITLEMENT_SECRET} - secret synced into builder namespace (default: etc-pki-entitlement)
#
# Env:
#   OPENSHIFT_BUILD_PROJECT  Builder namespace (default: reson8-build)
#   OPENSHIFT_PROJECT        Runtime namespace (default: reson8)
#   SKIP_INITIAL_BUILD       Set to 1 to skip initial oc start-build reson8-base
#   ENTITLEMENT_SYNC_TIMEOUT Seconds to wait for one-shot entitlement sync (default: 180)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMPLATE_DIR="${ROOT}/deploy/openshift/cluster_build_config"
TARGET_NS="${OPENSHIFT_BUILD_PROJECT:-reson8-build}"
RUNTIME_NS="${OPENSHIFT_PROJECT:-reson8}"
ENTITLEMENT_SECRET="${ENTITLEMENT_SECRET:-etc-pki-entitlement}"
SYNC_CRONJOB_NS="${SYNC_CRONJOB_NS:-openshift-config-managed}"
SYNC_CRONJOB_NAME="${SYNC_CRONJOB_NAME:-sync-entitlements}"
ENTITLEMENT_SYNC_TIMEOUT="${ENTITLEMENT_SYNC_TIMEOUT:-180}"

if ! command -v oc >/dev/null 2>&1; then
  echo "ERROR: oc CLI not found in PATH." >&2
  exit 1
fi

if ! command -v envsubst >/dev/null 2>&1; then
  echo "ERROR: envsubst not found. Install gettext/runtime package." >&2
  exit 1
fi

cd "${ROOT}"

if oc get namespace "${TARGET_NS}" >/dev/null 2>&1; then
  echo "Builder namespace '${TARGET_NS}' already exists."
else
  echo "Creating builder namespace '${TARGET_NS}'..."
  oc create namespace "${TARGET_NS}"
fi

echo "Applying cluster build templates from ${TEMPLATE_DIR}"
export TARGET_NS RUNTIME_NS SYNC_CRONJOB_NS ENTITLEMENT_SECRET
for template in "${TEMPLATE_DIR}"/*.yaml.tmpl; do
  echo "  - $(basename "${template}")"
  envsubst '${TARGET_NS} ${RUNTIME_NS} ${SYNC_CRONJOB_NS} ${ENTITLEMENT_SECRET}' < "${template}" | oc apply -f -
done

echo "Refreshing imported UBI stream..."
oc import-image ubi10-openjdk-21:latest -n "${TARGET_NS}" --confirm >/dev/null 2>&1 || true

ensure_entitlement_secret_now() {
  if oc get secret "${ENTITLEMENT_SECRET}" -n "${TARGET_NS}" >/dev/null 2>&1; then
    echo "Entitlement secret '${ENTITLEMENT_SECRET}' already present in '${TARGET_NS}'."
    return
  fi

  local job_name="${SYNC_CRONJOB_NAME}-now-$(date +%s)"
  echo "Triggering one-shot entitlement sync job '${job_name}'..."
  oc create job --from=cronjob/"${SYNC_CRONJOB_NAME}" "${job_name}" -n "${SYNC_CRONJOB_NS}"
  oc wait --for=condition=complete --timeout="${ENTITLEMENT_SYNC_TIMEOUT}s" \
    job/"${job_name}" -n "${SYNC_CRONJOB_NS}"

  if ! oc get secret "${ENTITLEMENT_SECRET}" -n "${TARGET_NS}" >/dev/null 2>&1; then
    echo "ERROR: '${ENTITLEMENT_SECRET}' was not copied to '${TARGET_NS}' after one-shot sync." >&2
    echo "       Check job logs: oc logs -n ${SYNC_CRONJOB_NS} job/${job_name}" >&2
    exit 1
  fi
  echo "Entitlement secret '${ENTITLEMENT_SECRET}' synced to '${TARGET_NS}'."
}

ensure_entitlement_secret_now

if [[ "${SKIP_INITIAL_BUILD:-0}" != "1" ]]; then
  echo "Starting initial reson8-base build in '${TARGET_NS}'..."
  oc start-build reson8-base -n "${TARGET_NS}" --wait
else
  echo "Skipping initial build (SKIP_INITIAL_BUILD=1)."
fi

echo "Builder namespace bootstrap complete."
echo "Check status:"
echo "  oc get is -n ${TARGET_NS}"
echo "  oc get bc -n ${TARGET_NS}"
echo "  oc get builds -n ${TARGET_NS} --sort-by=.metadata.creationTimestamp | tail -n 5"
