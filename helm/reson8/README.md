# reson8 Helm chart (OpenShift)

Minimal OpenShift-oriented chart for `reson8` and `disson8`. Install procedures:

- **Evaluator:** [docs/flows/evaluator-install.md](../../docs/flows/evaluator-install.md)
- **Operator (production):** [docs/flows/operator-install.md](../../docs/flows/operator-install.md)

Configuration reference: [docs/reference/configuration/helm-values.md](../../docs/reference/configuration/helm-values.md).

## Postures

| Profile | Values | Use |
| --- | --- | --- |
| Evaluator (default) | `values.yaml` | Concept feedback; Thanos readiness off, demo groups |
| Production | `values-production.yaml` | Operator installs; Thanos readiness on, explicit security |

## Important values

- `image.repository`, `image.tag` — reson8 runtime image
- `disson8.enabled` — include disson8 (default `true`)
- `disson8.image.repository`, `disson8.image.tag`
- `rbac.clusterScoped.create` — set `false` if RBAC pre-provisioned
- `appConfig.profile` — `evaluator` or `production`
- `appConfig.inlineYaml` — full `application.yaml` override
- `networkPolicy.enabled` — baseline policies (default `true`)
- `resources`, `disson8.resources`, `securityContext`, `podSecurityContext`

## Included resources

ServiceAccount, OAuth redirect annotation, OAuth client Secret, app ConfigMap, trusted CA bundle ConfigMap, reson8 Deployment/Service/Route/NetworkPolicy, disson8 Deployment/Service/Route (when enabled), optional cluster RBAC.

## Constraints

- [Cluster CA setup](../../docs/reference/platform/cluster-ca-setup.md) required
- [RBAC](../../docs/reference/platform/rbac.md) required or pre-provisioned
- Single-replica — [readiness](../../docs/reference/platform/readiness.md)
- NetworkPolicies need enforcing CNI (OpenShift SDN/OVN)

## Maintainers

Publishing: [docs/flows/releasing.md](../../docs/flows/releasing.md) — `deploy/release-roundtrip.sh --help`
