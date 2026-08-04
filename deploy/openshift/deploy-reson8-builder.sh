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
TARGET_NS="${OPENSHIFT_BUILD_PROJECT:-reson8-build}"
RUNTIME_NS="${OPENSHIFT_PROJECT:-reson8}"
ENTITLEMENT_SECRET="${ENTITLEMENT_SECRET:-etc-pki-entitlement}"
SYNC_CRONJOB_NS="${SYNC_CRONJOB_NS:-openshift-config-managed}"
SYNC_CRONJOB_NAME="${SYNC_CRONJOB_NAME:-sync-entitlements}"
ENTITLEMENT_SYNC_TIMEOUT="${ENTITLEMENT_SYNC_TIMEOUT:-180}"
# shellcheck disable=SC1091
source "${ROOT}/deploy/lib/deploy-common.sh"

deploy_builder_bootstrap \
  "${TARGET_NS}" \
  "${RUNTIME_NS}" \
  "${SKIP_INITIAL_BUILD:-0}" \
  "${ENTITLEMENT_SECRET}" \
  "${SYNC_CRONJOB_NS}" \
  "${SYNC_CRONJOB_NAME}" \
  "${ENTITLEMENT_SYNC_TIMEOUT}"

echo "Builder namespace bootstrap complete."
echo "Check status:"
echo "  oc get is -n ${TARGET_NS}"
echo "  oc get bc -n ${TARGET_NS}"
echo "  oc get builds -n ${TARGET_NS} --sort-by=.metadata.creationTimestamp | tail -n 5"
