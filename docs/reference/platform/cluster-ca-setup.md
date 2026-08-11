# Cluster CA setup

Reson8 pods require three CA bundles projected at startup. The entrypoint (`run-with-ca-update.sh`) runs `update-ca-trust extract` before the JVM starts. **No application-level TLS configuration is permitted.**

| Anchors file | Source ConfigMap | Setup |
| --- | --- | --- |
| `service-ca.crt` | `openshift-service-ca.crt` | Auto-created in every namespace |
| `kube-ca.crt` | `kube-root-ca.crt` | Auto-created in every namespace |
| `ingress-ca.crt` | `reson8-trusted-ca-bundle` key `ca-bundle.crt` | **One-time cluster-admin setup below** |

If any file is missing or empty, the pod exits with a FATAL message and does not start the JVM.

## One-time cluster-admin setup (ingress CA)

`openshift-service-ca.crt` and `kube-root-ca.crt` require no action. The router/ingress CA does:

```bash
# 1. Extract the router CA
oc get secret router-ca -n openshift-ingress-operator \
  -o jsonpath='{.data.tls\.crt}' | base64 -d > router-ca.crt

# 2. Create a ConfigMap in openshift-config
oc create configmap custom-ca --from-file=ca-bundle.crt=router-ca.crt -n openshift-config

# 3. Patch the cluster proxy
oc patch proxy cluster --type=merge -p '{"spec":{"trustedCA":{"name":"custom-ca"}}}'
```

After step 3, the cluster-network-operator injects the router CA into namespace ConfigMaps labelled `config.openshift.io/inject-trusted-cabundle: "true"` — including `reson8-trusted-ca-bundle` — as key `ca-bundle.crt`.

Verify in your target namespace:

```bash
oc get configmap reson8-trusted-ca-bundle -n <namespace> \
  -o jsonpath='{.data.ca-bundle\.crt}' | openssl x509 -noout -subject
```

This is a **one-time cluster operation**. Do not disable TLS or add per-client trust-store overrides as a workaround.
