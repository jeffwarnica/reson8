#!/usr/bin/env bash
# Refresh workloads Quarkus OpenShift deploy does not manage, then run Maven deploy.
#
# Maven applies target/kubernetes/openshift.yml (merged fragments under
# reson8/src/main/kubernetes/, plus generated Deployment / Route / BuildConfig / …).
# It does NOT apply cluster RBAC or the application ConfigMap reson8-config.
#
# ConfigMap sync merges application.yml with an optional cluster overlay:
#   deploy/openshift/application-cluster-overlay.yaml  (gitignored — copy from *.example.yaml)
# The overlay holds cluster-specific keys such as quarkus.oidc.enabled, security groups, etc.
# Without it every deploy would clobber any manual additions made in the cluster.
#
# After Maven, the script verifies openshift-service-ca.crt exists and waits for ca-bundle.crt
# to be injected into reson8-trusted-ca-bundle.  The pod entrypoint (run-with-ca-update.sh)
# calls update-ca-trust before the JVM starts; pods fail hard if either bundle is absent.
#
# Env:
#   OPENSHIFT_PROJECT   Namespace (default: reson8).
#   APPLY_CLUSTER_RBAC  Set to 1 to oc apply deploy/rbac.yml (needs cluster-admin).
#   SKIP_CONFIGMAP      Set to 1 to skip syncing reson8-config from application.yml.
#   CA_WAIT_ATTEMPTS    Poll iterations for CA bundle readiness (default: 30, ~90 s).
#
# Extra arguments are forwarded to Maven (e.g. -DskipTests).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NS="${OPENSHIFT_PROJECT:-reson8}"
OVERLAY="${ROOT}/deploy/openshift/runtime_config/application-cluster-overlay.yaml"
SOURCE_YML="${ROOT}/reson8/src/main/resources/application.yml"
CA_WAIT_ATTEMPTS="${CA_WAIT_ATTEMPTS:-30}"

export SOURCE_YML OVERLAY

cd "$ROOT"

oc project "$NS" >/dev/null

if [[ "${APPLY_CLUSTER_RBAC:-0}" == "1" ]]; then
  oc apply -f "${ROOT}/deploy/rbac.yml"
fi

if [[ "${SKIP_CONFIGMAP:-0}" != "1" ]]; then
  if [[ -f "$OVERLAY" ]]; then
    echo "Merging cluster overlay into reson8-config: $OVERLAY"
    MERGED_YML="$(python3 - <<'PYEOF'
import sys, yaml, os

source = os.environ.get("SOURCE_YML")
overlay = os.environ.get("OVERLAY")

with open(source) as f:
    base = yaml.safe_load(f) or {}

with open(overlay) as f:
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
    echo "$MERGED_YML" | oc create configmap reson8-config \
      --from-file=application.yaml=/dev/stdin \
      -n "${NS}" \
      --dry-run=client -o yaml | oc apply -f -
  else
    echo "No cluster overlay found at $OVERLAY — syncing application.yml only."
    echo "(Copy deploy/openshift/application-cluster-overlay.example.yaml to $OVERLAY and fill in cluster values.)"
    oc create configmap reson8-config \
      --from-file=application.yaml="${SOURCE_YML}" \
      -n "${NS}" \
      --dry-run=client -o yaml | oc apply -f -
  fi
fi

./mvnw package -pl reson8 -Dquarkus.openshift.deploy=true "$@"

# CA trust uses OS-level update-ca-trust (run-with-ca-update.sh entrypoint).
# Three ConfigMaps are projected into /etc/pki/ca-trust/source/anchors/:
#   openshift-service-ca.crt  — auto-created by OpenShift in every namespace (always present)
#   kube-root-ca.crt          — auto-created by OpenShift in every namespace (always present)
#   reson8-trusted-ca-bundle  — label config.openshift.io/inject-trusted-cabundle: "true"
#                               key ca-bundle.crt (ingress/router CA; requires one-time cluster-admin
#                               setup described in DEPLOY.md "Cluster CA Setup")
#
# Verify openshift-service-ca.crt exists (it always does; absence means cluster/namespace problem).
echo "Verifying openshift-service-ca.crt ConfigMap (auto-created by OpenShift)..."
if ! oc get configmap openshift-service-ca.crt -n "${NS}" &>/dev/null; then
  echo "ERROR: ConfigMap openshift-service-ca.crt not found in namespace ${NS}." >&2
  echo "       This ConfigMap is auto-created by OpenShift in every namespace." >&2
  echo "       Its absence indicates a cluster or namespace setup problem." >&2
  exit 1
fi
echo "openshift-service-ca.crt is present."

# Wait for ca-bundle.crt to be injected into reson8-trusted-ca-bundle by the
# cluster-network-operator (requires the one-time cluster-admin proxy setup in DEPLOY.md).
echo "Waiting for reson8-trusted-ca-bundle ingress CA injection (up to $((CA_WAIT_ATTEMPTS * 3)) s)..."
INJECTED=0
for i in $(seq 1 "${CA_WAIT_ATTEMPTS}"); do
  ca_bundle=$(oc get configmap reson8-trusted-ca-bundle -n "${NS}" \
    -o jsonpath='{.data.ca-bundle\.crt}' 2>/dev/null || true)
  if [[ -n "$ca_bundle" ]]; then
    echo "reson8-trusted-ca-bundle ca-bundle.crt is populated (attempt $i)."
    INJECTED=1
    break
  fi
  echo "  attempt $i/${CA_WAIT_ATTEMPTS} — ca-bundle.crt not yet injected, retrying in 3 s..."
  sleep 3
done

if [[ "$INJECTED" == "0" ]]; then
  echo "ERROR: reson8-trusted-ca-bundle ca-bundle.crt was not populated after $((CA_WAIT_ATTEMPTS * 3)) s." >&2
  echo "       Ensure the one-time cluster-admin CA setup has been run (see DEPLOY.md):" >&2
  echo "       oc describe configmap reson8-trusted-ca-bundle -n ${NS}" >&2
  exit 1
fi
