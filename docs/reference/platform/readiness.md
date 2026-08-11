# Readiness and single-replica model

## Readiness probe

The pod readiness probe hits `/q/health/ready`, which aggregates:

- **Kubernetes API** — lists cluster nodes; pod stays DOWN until RBAC and ServiceAccount are in place.
- **Thanos querier** — `GET /api/v1/labels` against the configured Thanos API (when enabled). Uses JVM default TLS trust after `update-ca-trust` in the entrypoint.

Disable the Thanos check only when necessary: `reson8.k8s.thanos.readiness-check=false` (`%test` sets this by default).

Until readiness is UP:

- `reson8` ServiceAccount exists
- ClusterRoleBinding from [rbac.md](rbac.md) is in place
- (When enabled) Thanos is reachable with `cluster-monitoring-view`
- [Cluster CA setup](cluster-ca-setup.md) completed

Traffic is not routed until the app can reach the cluster and (in production posture) monitoring.

## Posture differences

| Posture | Thanos readiness check |
| --- | --- |
| Evaluator (Helm default) | Off |
| Production (Helm `-f values-production.yaml`) | On |
| Developer Quarkus deploy | On when overlay enables it |

## Single-replica operating model

Reson8 is **not horizontally scalable**: one GStreamer pipeline and one audio stream per deployment.

- Keep `replicas: 1` (chart default; Quarkus deploy sets `quarkus.openshift.replicas=1`).
- Upgrades: use rolling update; expect a brief stream interruption during pod replacement.
- Do not scale the Deployment beyond one replica — multiple pods would each emit independent streams.
- Safe restart: `oc rollout restart deployment/<name> -n <namespace>` after ConfigMap changes.

For scaling constraints and future work, see module roadmap in [reson8/README.md](../../../reson8/README.md).
