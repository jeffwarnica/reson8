# Evaluator install

**Audience:** Evaluator

Try Reson8 with prebuilt Quay images and Helm. For concept feedback — not contributor workflow validation. **disson8** is included by default for synthetic HTTP traffic.

**You need:**

- OpenShift cluster, `oc`, `helm` 3.8+
- Namespace create permission in target namespace
- Pull access to Quay images and OCI chart

**Prerequisites:**

- [Cluster CA setup](../reference/platform/cluster-ca-setup.md) (one-time, cluster-admin)
- [RBAC](../reference/platform/rbac.md) or pre-provisioned cluster RBAC

## Happy path

1. Log in to Quay for Helm OCI (separate from Podman auth):

```bash
helm registry login quay.io -u 'your_username+your_username_robot' --password-stdin
```

2. Install from OCI chart (example version **0.1.1** — use current release tag):

```bash
helm install reson8-eval oci://quay.io/rhn_gps_jwarnica/reson8-helm \
  --version 0.1.1 \
  -n reson8-eval \
  --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=v0.1.1 \
  --set disson8.image.repository=quay.io/rhn_gps_jwarnica/disson8 \
  --set disson8.image.tag=v0.1.1
```

3. Verify and open stream:

```bash
oc rollout status deployment/reson8-eval-reson8 -n reson8-eval
oc get route reson8-eval-reson8 -n reson8-eval
```

`https://<route-host>/audio/stream`

## 15-minute challenge

1. Install the chart.
2. Open the stream.
3. Listen 5+ minutes during normal cluster activity (optionally drive traffic via disson8 route).
4. Share feedback on signal quality, noise, and operator usefulness.

## Useful variants

- **Pre-provisioned RBAC:** add `--set rbac.clusterScoped.create=false`
- **From git clone:** `helm upgrade --install reson8-eval ./helm/reson8` with same `--set image.*` flags
- **Production install:** use [operator-install](operator-install.md) instead — not this flow

## Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Pod FATAL on CA | Ingress CA not set up | [cluster-ca-setup](../reference/platform/cluster-ca-setup.md) |
| Pod NotReady (Thanos) | Wrong posture expectation | Evaluator disables Thanos readiness — if enabled, check [helm-values](../reference/configuration/helm-values.md) |
| Chart pull fails | Helm not logged in to Quay | `helm registry login quay.io` |

Full index: [troubleshooting](../reference/troubleshooting.md).

## Reference

- [helm-values](../reference/configuration/helm-values.md) — evaluator posture
- [disson8](../../../disson8/README.md)
- Production path: [operator-install](operator-install.md)
