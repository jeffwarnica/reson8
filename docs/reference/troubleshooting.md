# Troubleshooting

Consolidated symptom index. Flow-specific shortcuts appear in each [flow doc](../flows/).

Tags: **Dev** = developer flows, **Eval** = evaluator, **Op** = operator, **All** = any install.

## Symptom index

| Symptom | Likely cause | What to do | Flows |
| --- | --- | --- | --- |
| Pod stuck **NotReady** | RBAC missing; Thanos unreachable when check enabled | `oc describe pod`; verify [rbac](platform/rbac.md); `oc auth can-i list nodes --as=system:serviceaccount:<ns>:reson8` | All |
| Pod exits FATAL on CA files | Ingress CA not configured | Complete [cluster-ca-setup](platform/cluster-ca-setup.md) | All |
| **503** from Route, empty app logs | Router cannot reach healthy backends | Check endpoints, readiness, in-pod curl vs Route — see [route-503](#route-503) | All |
| **Forbidden/401** to Thanos | `cluster-monitoring-view` binding missing | `oc get clusterrolebinding reson8-monitoring-view`; re-apply [rbac](platform/rbac.md) | Op, Dev |
| GStreamer WARN / plugin-not-found | Stale base image | Rebuild [bootstrap-builder](platform/bootstrap-builder.md); redeploy | Dev |
| **WavCache** file-not-found | Sounds missing from image | `oc debug deployment/<name> -- ls /opt/reson8/sounds/` | Dev |
| Config change not reflected | ConfigMap not updated or pod not restarted | Re-sync ConfigMap; `oc rollout restart deployment/<name>` | Dev, Op |
| Image build/deploy fails | Registry auth, missing base `FROM` | Verify internal registry, `podman login`, base image in builder NS | Dev |
| **403** on `GET /login` | OIDC off or Authorization header injected | Ensure `quarkus.oidc.enabled: true` in mounted config; see [oidc](configuration/oidc.md) | Op, Dev |
| Login redirect loops | Wrong OAuth redirect URI | Register `https://<route>/q/oidc/*`; check Forwarded headers | Op, Dev |
| Deployment `containers: Required value` | Bad openshift.yml Deployment fragment | Container entry must include `name: reson8` for Dekorate merge | Dev |

## route-503

OpenShift router returns 503 when Service backends are unhealthy — traffic may never reach the JVM.

```bash
oc get endpoints <service> -n <namespace> -o wide
oc describe route <route-name> -n <namespace>
oc exec deploy/<deployment> -n <namespace> -- \
  curl -s -o /dev/null -w "pod-http-status=%{http_code}\n" http://127.0.0.1:8090/
```

If in-pod status is 200/302 but Route returns 503, focus on Endpoints, readiness, and Service selectors.

## Thanos cross-namespace 401

`cluster-monitoring-view` may not cover aggregated queries across namespaces. Grant `view` on source namespaces — see [rbac](platform/rbac.md).

## ConfigMap sync (developer Quarkus deploy)

Preferred — merges overlay automatically:

```bash
SYNC_CONFIGMAP=1 CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod -DskipTests
```

## GStreamer base refresh

```bash
oc start-build reson8-base -n reson8-build --wait
# or ./deploy/openshift/deploy-reson8-builder.sh
```

## Evaluator vs production misconfiguration

If Thanos readiness blocks evaluator demos, confirm `appConfig.profile: evaluator` (default). For production, use `-f values-production.yaml` — see [helm-values](configuration/helm-values.md).

## Local dev unaffected

`quarkus.container-image.*` and `quarkus.openshift.*` apply only when image build or deploy is triggered. `./mvnw quarkus:dev` is unaffected.
