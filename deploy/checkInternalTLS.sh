#!/usr/bin/env bash
# Compare TLS leaf certs for the *in-cluster* Kubernetes API vs Thanos querier.
# Requires: oc (logged in), openssl locally — no RPM installs on the cluster.
#
# svc/kubernetes has no selector, so oc cannot port-forward it. Instead we port-forward a
# kube-apiserver control-plane pod (OpenShift: openshift-kube-apiserver) and probe with SNI
# kubernetes.default.svc.cluster.local — same logical target workloads use.
#
# Typical RBAC: port-forward to openshift-kube-apiserver pods needs cluster-admin (or equivalent).
#
# Usage:
#   ./deploy/checkInternalTLS.sh
# Optional env:
#   APISERVER_NS=openshift-kube-apiserver API_LOCAL_PORT=16443 API_POD_PORT=6443 \
#   THANOS_LOCAL_PORT=19091 MON_NS=openshift-monitoring ./deploy/checkInternalTLS.sh
# Override pod pick:
#   API_POD=kube-apiserver-mycluster-xxxxx ./deploy/checkInternalTLS.sh

set -euo pipefail

APISERVER_NS="${APISERVER_NS:-openshift-kube-apiserver}"
API_LOCAL_PORT="${API_LOCAL_PORT:-16443}"
API_POD_PORT="${API_POD_PORT:-6443}"
THANOS_LOCAL_PORT="${THANOS_LOCAL_PORT:-19091}"
MON_NS="${MON_NS:-openshift-monitoring}"

PF_API_PID=""
PF_THANOS_PID=""

cleanup() {
  [[ -n "${PF_API_PID}" ]] && kill "${PF_API_PID}" 2>/dev/null || true
  [[ -n "${PF_THANOS_PID}" ]] && kill "${PF_THANOS_PID}" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

command -v oc >/dev/null || {
  echo "oc not found in PATH" >&2
  exit 1
}
command -v openssl >/dev/null || {
  echo "openssl not found in PATH (install locally)" >&2
  exit 1
}

wait_tcp() {
  local host="$1" port="$2" tries="${3:-40}"
  local i=0
  while ! bash -c "exec 3<>/dev/tcp/${host}/${port}" 2>/dev/null; do
    i=$((i + 1))
    if [[ "${i}" -ge "${tries}" ]]; then
      echo "timeout waiting for ${host}:${port}" >&2
      return 1
    fi
    sleep 0.25
  done
  return 0
}

resolve_kube_apiserver_pod() {
  if [[ -n "${API_POD:-}" ]]; then
    echo "${API_POD}"
    return 0
  fi
  local p=""
  p="$(oc get pods -n "${APISERVER_NS}" -l apiserver=true -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
  if [[ -n "${p}" ]]; then
    echo "${p}"
    return 0
  fi
  oc get pods -n "${APISERVER_NS}" -o jsonpath='{range .items[?(@.status.phase=="Running")]}{.metadata.name}{"\n"}{end}' 2>/dev/null \
    | grep -E '^kube-apiserver-' | head -1 || true
}

API_POD_RESOLVED="$(resolve_kube_apiserver_pod)"
if [[ -z "${API_POD_RESOLVED}" ]]; then
  echo "Could not find a Running kube-apiserver pod in namespace \"${APISERVER_NS}\"." >&2
  echo "Set APISERVER_NS / API_POD for your cluster (OpenShift: openshift-kube-apiserver)." >&2
  exit 1
fi

echo "Using internal API via port-forward: ns=${APISERVER_NS} pod=${API_POD_RESOLVED} local ${API_LOCAL_PORT}->pod:${API_POD_PORT}"
echo "Thanos via port-forward: ns=${MON_NS} svc/thanos-querier local ${THANOS_LOCAL_PORT}->9091"

echo ""
echo "Starting port-forward: ${APISERVER_NS}/pod/${API_POD_RESOLVED} ..."
oc port-forward -n "${APISERVER_NS}" "pod/${API_POD_RESOLVED}" "${API_LOCAL_PORT}:${API_POD_PORT}" &
PF_API_PID=$!

echo "Starting port-forward: ${MON_NS}/thanos-querier ..."
oc -n "${MON_NS}" port-forward "svc/thanos-querier" "${THANOS_LOCAL_PORT}:9091" &
PF_THANOS_PID=$!

wait_tcp 127.0.0.1 "${API_LOCAL_PORT}"
wait_tcp 127.0.0.1 "${THANOS_LOCAL_PORT}"

echo ""
echo "=== Internal K8s API leaf (localhost:${API_LOCAL_PORT}, SNI kubernetes.default.svc.cluster.local) ==="
echo | openssl s_client -connect "127.0.0.1:${API_LOCAL_PORT}" \
  -servername kubernetes.default.svc.cluster.local 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates -fingerprint -sha256

echo ""
echo "=== Thanos querier leaf (localhost:${THANOS_LOCAL_PORT}, SNI thanos-querier.openshift-monitoring.svc.cluster.local) ==="
echo | openssl s_client -connect "127.0.0.1:${THANOS_LOCAL_PORT}" \
  -servername thanos-querier.openshift-monitoring.svc.cluster.local 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates -fingerprint -sha256

echo ""
echo "=== Cluster kube-root-ca.crt (reference; oc get ConfigMap) ==="
if TMP_CA="$(oc get cm kube-root-ca.crt -n openshift-config-managed -o jsonpath='{.data.ca\.crt}' 2>/dev/null)" &&
  [[ -n "${TMP_CA}" ]]; then
  echo "${TMP_CA}" | openssl x509 -noout -subject -issuer -fingerprint -sha256 2>/dev/null \
    || echo "(bundle / multi-cert ConfigMap — inspect manually)"
else
  echo "(no openshift-config-managed/kube-root-ca.crt)"
fi

echo ""
echo "Tip: compare issuer / SHA256 fingerprint lines — internal API vs Thanos often differ (different signers)."
echo "Tip: workloads trust kubernetes.default via serviceaccount/ca.crt + injected PEM bundles; same idea as this script's API hop."
