# Quarkus OpenShift deploy

**Audience:** Developer

Deploy to the shared `reson8` namespace from a git clone using Quarkus OpenShift extension and in-cluster image build. **This is not the Operator path** — production installs use [operator-install](operator-install.md) (Helm OCI only).

**You need:**

- `oc` logged in, `./mvnw`
- Project-admin on `reson8` runtime namespace
- `CONFIRM_PROD_DEPLOY=1` intentional guard

**Prerequisites:**

- [Bootstrap builder](../reference/platform/bootstrap-builder.md)
- [RBAC](../reference/platform/rbac.md)
- [Cluster CA setup](../reference/platform/cluster-ca-setup.md)
- [Cluster overlay](../reference/configuration/cluster-overlay.md) copied and edited

## Happy path

1. One-time overlay setup:

```bash
cp deploy/openshift/runtime_config/application-cluster-overlay.example.yaml \
   deploy/openshift/runtime_config/application-cluster-overlay.yaml
# Edit group names, quarkus.oidc.enabled, etc.
```

2. Deploy:

```bash
CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod
```

This deep-merges overlay + `application.yml`, syncs `reson8-config`, runs Maven with `-Dquarkus.openshift.deploy=true`, and triggers in-cluster image build.

3. Verify:

```bash
oc rollout status deployment/reson8 -n reson8
oc get route reson8 -n reson8
```

## Useful variants

- **ConfigMap only:** `SYNC_CONFIGMAP=1 CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod -DskipTests`
- **Raw Maven:** `./mvnw package -pl reson8 -Dquarkus.openshift.deploy=true`
- **Personal test namespace:** use [cluster-test](cluster-test.md) instead

## Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| OIDC/login broken after deploy | Overlay not merged | Use `run.sh` not manual ConfigMap — [cluster-overlay](../reference/configuration/cluster-overlay.md) |
| `containers: Required value` | Bad openshift.yml fragment | [troubleshooting](../reference/troubleshooting.md) |
| Config not picked up | Pod not restarted | `SYNC_CONFIGMAP=1` or `oc rollout restart deployment/reson8` |

Full index: [troubleshooting](../reference/troubleshooting.md).

## Reference

- [application-properties](../reference/configuration/application-properties.md) — OpenShift block
- [oidc](../reference/configuration/oidc.md)
- [image-layout](../reference/platform/image-layout.md)
