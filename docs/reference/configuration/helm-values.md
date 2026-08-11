# Helm values

Chart path: `helm/reson8/`. Reference README: [helm/reson8/README.md](../../../helm/reson8/README.md).

## Posture comparison

| | Evaluator (default) | Production (`-f values-production.yaml`) |
| --- | --- | --- |
| `appConfig.profile` | `evaluator` | `production` |
| Thanos readiness check | Off | On |
| Security groups | Demo defaults | Explicit production groups in `files/application-production.yaml` |
| Logging | DEBUG | INFO |
| Safe for production | No | Yes (after IdP group customization) |

## Important values

| Key | Purpose |
| --- | --- |
| `image.repository`, `image.tag` | reson8 runtime image |
| `disson8.enabled` | Include disson8 deployment (default `true`) |
| `disson8.image.repository`, `disson8.image.tag` | disson8 image |
| `rbac.clusterScoped.create` | Set `false` if RBAC pre-provisioned |
| `appConfig.profile` | `evaluator` or `production` |
| `appConfig.inlineYaml` | Full override for mounted `application.yaml` |
| `networkPolicy.enabled` | Baseline NetworkPolicies (default `true`) |
| `resources` / `disson8.resources` | CPU/memory requests and limits |

## Included resources

ServiceAccount, OAuth redirect annotation, OAuth client Secret, app ConfigMap, trusted CA bundle ConfigMap, reson8 Deployment/Service/Route/NetworkPolicy, disson8 Deployment/Service/Route (when enabled), optional cluster RBAC.

## Constraints

- One-time [cluster CA setup](../platform/cluster-ca-setup.md) required.
- Single-replica topology — see [readiness.md](../platform/readiness.md).
- NetworkPolicies require enforcing CNI (OpenShift SDN/OVN).

## Install flows

- Evaluator: [evaluator-install.md](../../flows/evaluator-install.md)
- Operator (production): [operator-install.md](../../flows/operator-install.md)
