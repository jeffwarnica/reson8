# Operator install

**Audience:** Operator

Install Reson8 in production posture using **Helm OCI only**. No git clone required. No Quarkus `run.sh` deploy.

**You need:**

- OpenShift cluster, `oc`, `helm` 3.8+
- Namespace admin on target namespace
- Pull access to Quay images and OCI chart
- IdP group names matching your cluster

**Prerequisites:**

Complete all [production readiness gates](../reference/platform/prerequisites.md#production-readiness-gates-operator):

- [Cluster CA setup](../reference/platform/cluster-ca-setup.md)
- [RBAC](../reference/platform/rbac.md) applied or pre-provisioned
- IdP groups customized in `helm/reson8/files/application-production.yaml` or via `appConfig.inlineYaml`

## Happy path

1. Customize production groups (from clone or fork) in `files/application-production.yaml`, or prepare `appConfig.inlineYaml` override.

2. Log in to Quay for Helm:

```bash
helm registry login quay.io -u 'your_username+your_username_robot' --password-stdin
```

3. Install from OCI with production values overlay (example version **0.1.1**):

```bash
helm upgrade --install reson8 oci://quay.io/rhn_gps_jwarnica/reson8-helm \
  --version 0.1.1 \
  -n reson8 \
  --create-namespace \
  -f helm/reson8/values-production.yaml \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=v0.1.1 \
  --set disson8.image.repository=quay.io/rhn_gps_jwarnica/disson8 \
  --set disson8.image.tag=v0.1.1
```

When installing without a git clone, copy `values-production.yaml` content into `-f` path or use `--set appConfig.profile=production` plus `appConfig.inlineYaml` for group names.

4. Verify:

```bash
oc rollout status deployment/reson8-reson8 -n reson8
oc get route reson8-reson8 -n reson8
```

Stream: `https://<route-host>/audio/stream`

## Production checklist

- [ ] Cluster CA setup complete
- [ ] Cluster RBAC in place (`rbac.clusterScoped.create=false` if pre-provisioned)
- [ ] `appConfig.profile: production` (via `-f values-production.yaml`)
- [ ] Admin/viewer/stream group names match IdP
- [ ] `dev-tier-cookie-enabled: false` in production config
- [ ] Resource limits acceptable for namespace quota
- [ ] NetworkPolicies compatible with cluster CNI (or disabled per platform policy)
- [ ] Single replica acknowledged — [readiness](../reference/platform/readiness.md)

## Useful variants

- **Pre-provisioned RBAC:** `--set rbac.clusterScoped.create=false`
- **Disable disson8:** `--set disson8.enabled=false`
- **Upgrade image tag:** `helm upgrade` with new `--set image.tag=vX.Y.Z` and matching chart `--version`

## Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Pod NotReady | Thanos check on + RBAC/monitoring gap | [rbac](../reference/platform/rbac.md), [readiness](../reference/platform/readiness.md) |
| Login/403 issues | OIDC or tier config | [oidc](../reference/configuration/oidc.md), [security-tiers](../reference/configuration/security-tiers.md) |
| Evaluator-like behavior | Wrong values file | Must use `values-production.yaml` |

Full index: [troubleshooting](../reference/troubleshooting.md).

## Reference

- [helm-values](../reference/configuration/helm-values.md)
- [security-tiers](../reference/configuration/security-tiers.md)
- Chart packaging details: [helm/reson8/README.md](../../../helm/reson8/README.md)
