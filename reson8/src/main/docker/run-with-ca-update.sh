#!/bin/bash
# Startup entrypoint: update OS-level CA trust before launching the JVM.
#
# Three CA cert bundles are projected from Kubernetes ConfigMaps into
# /etc/pki/ca-trust/source/anchors/ at pod startup:
#
#   service-ca.crt  — from ConfigMap openshift-service-ca.crt (auto-created by OpenShift in every
#                     namespace); signs all *.svc.cluster.local serving certs (Thanos, k8s API).
#
#   ingress-ca.crt  — from ConfigMap reson8-trusted-ca-bundle key ca-bundle.crt; injected by the
#                     cluster-network-operator because the ConfigMap carries the label
#                     config.openshift.io/inject-trusted-cabundle=true. Populated with the
#                     router/ingress CA only after a cluster administrator has run the one-time
#                     cluster trust setup documented in DEPLOY.md.
#
#   kube-ca.crt     — from ConfigMap kube-root-ca.crt (auto-created by OpenShift in every
#                     namespace); signs the Kubernetes API server certificate.
#
# Running update-ca-trust extract here means the JVM launched by run-java.sh inherits an
# up-to-date /etc/pki/ca-trust/extracted/java/cacerts and tls-ca-bundle.pem containing all three
# CAs. No application-level TLS configuration is needed or allowed.
#
# Failure policy (no configuration overrides):
#   - service-ca.crt missing/empty → FATAL, namespace is broken (should never happen on OpenShift).
#   - ingress-ca.crt missing/empty → FATAL, cluster-admin setup has not been done (see DEPLOY.md).
#   - kube-ca.crt missing/empty → FATAL, namespace is broken (should never happen on OpenShift).
set -uo pipefail

ANCHORS_DIR="/etc/pki/ca-trust/source/anchors"

if [[ ! -s "${ANCHORS_DIR}/service-ca.crt" ]]; then
    echo "FATAL: OpenShift service CA not available at ${ANCHORS_DIR}/service-ca.crt" >&2
    echo "FATAL: ConfigMap openshift-service-ca.crt must exist in this namespace." >&2
    echo "FATAL: This ConfigMap is auto-created by OpenShift in every namespace; its absence" >&2
    echo "FATAL: indicates a cluster or namespace setup problem." >&2
    exit 1
fi

if [[ ! -s "${ANCHORS_DIR}/ingress-ca.crt" ]]; then
    echo "FATAL: Ingress/router CA not available at ${ANCHORS_DIR}/ingress-ca.crt" >&2
    echo "FATAL: A cluster administrator must run the one-time cluster trust setup." >&2
    echo "FATAL: See DEPLOY.md section 'Cluster CA Setup (one-time, cluster-admin)'." >&2
    exit 1
fi

if [[ ! -s "${ANCHORS_DIR}/kube-ca.crt" ]]; then
    echo "FATAL: Kubernetes API CA not available at ${ANCHORS_DIR}/kube-ca.crt" >&2
    echo "FATAL: ConfigMap kube-root-ca.crt must exist in this namespace." >&2
    echo "FATAL: This ConfigMap is auto-created by OpenShift in every namespace; its absence" >&2
    echo "FATAL: indicates a cluster or namespace setup problem." >&2
    exit 1
fi

# update-ca-trust extract may emit non-fatal permission warnings for the OpenSSL hash
# directory (p11-kit's directory-hash) when running as a non-root user under OpenShift's
# restricted SCC. The JVM only requires java/cacerts, so we tolerate those warnings and
# verify the critical output is present.
set +e
update-ca-trust extract 2>&1
UPDATE_CA_EXIT=$?
set -e

if [[ $UPDATE_CA_EXIT -ne 0 ]]; then
    if [[ ! -s "/etc/pki/ca-trust/extracted/java/cacerts" ]]; then
        echo "FATAL: update-ca-trust extract failed and java/cacerts is missing or empty" >&2
        exit 1
    fi
    echo "WARN: update-ca-trust extract had non-fatal warnings; java/cacerts is present, continuing." >&2
fi

exec /opt/jboss/container/java/run/run-java.sh
