# Cluster test deploy

**Audience:** Developer

**You need:**

- `oc` logged in, `./mvnw`
- Project-admin on `${BASE_NS:-reson8}-dev-${USER}` (auto-created by default)
- Optional: builder namespace access or local RHSM host for `reson8-base`

**Prerequisites:**

- [Cluster CA setup](../reference/platform/cluster-ca-setup.md) (one-time, cluster-admin)
- [RBAC](../reference/platform/rbac.md) — bindings must cover your test namespace SA or use shared bindings
- [Bootstrap builder](../reference/platform/bootstrap-builder.md) for first-time base image

## Happy path

1. Optional: copy cluster overlay for OIDC/groups in test namespace:

```bash
cp deploy/openshift/runtime_config/application-cluster-overlay.example.yaml \
   deploy/openshift/runtime_config/application-cluster-overlay.yaml
```

2. Deploy from repo root:

```bash
./deploy/run.sh cluster-test
```

3. Get route and open stream:

```bash
TEST_NS="${BASE_NS:-reson8}-dev-${USER}"
oc get route reson8 -n "${TEST_NS}"
```

Stream: `https://<route-host>/audio/stream`

`disson8` deploys in the same namespace by default for synthetic HTTP traffic — see [disson8](../../../disson8/README.md).

## Useful variants

- **Preview only:** `./deploy/run.sh cluster-test --dry-run` (oc diff, no apply)
- **Force local base build:** `BASE_IMAGE_SOURCE=local ./deploy/run.sh cluster-test`
- **In-cluster base only:** `BASE_IMAGE_SOURCE=cluster ALLOW_LOCAL_BASE_FALLBACK=0 ./deploy/run.sh cluster-test`

Run `deploy/run.sh` for all modes and flags.

## Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Pod NotReady | RBAC or Thanos check | See [troubleshooting#symptom-index](../reference/troubleshooting.md#symptom-index) |
| Base build fails | Missing GStreamer RPMs in cluster | Use `BASE_IMAGE_SOURCE=local` — [bootstrap-builder](../reference/platform/bootstrap-builder.md) |
| Wrong namespace | `BASE_NS` / `USER` | `echo "${BASE_NS:-reson8}-dev-${USER}"` |

Full index: [troubleshooting](../reference/troubleshooting.md).

## Reference

- [cluster-overlay](../reference/configuration/cluster-overlay.md)
- [readiness](../reference/platform/readiness.md)
- Shared namespace deploy: [quarkus-openshift-deploy](quarkus-openshift-deploy.md)
