# Prerequisites

Tools and permissions for Reson8 workflows.

## Tools

| Tool | Why |
| --- | --- |
| `oc` CLI, logged in | Applies manifests, routes, rollout status |
| `helm` 3.8+ | Evaluator and Operator installs (OCI chart pulls) |
| Java 21 + `./mvnw` | Developer builds and in-cluster Quarkus deploy |
| `podman` on a subscribed RHEL host | Local `reson8-base` builds when cluster BuildConfigs are incomplete |

## Permissions by workflow

| Workflow | Typical permissions |
| --- | --- |
| **Developer** (`cluster-test`, Quarkus deploy) | Project-admin on runtime namespace; builder namespace for base image refresh |
| **Evaluator / Operator** (Helm) | Namespace-scoped create in target namespace; cluster-admin for one-time CA setup |
| **Maintainer** (releasing) | Quay push; RHEL subscription for base image rebuilds |

Cluster-admin is required for one-time bootstrap: [cluster CA setup](cluster-ca-setup.md), [RBAC](rbac.md).

## Production readiness gates (Operator)

Before a production Helm install, confirm:

- [ ] [Cluster CA setup](cluster-ca-setup.md) completed (ingress CA injected into `reson8-trusted-ca-bundle`)
- [ ] [Cluster RBAC](rbac.md) applied or pre-provisioned (`rbac.clusterScoped.create=false` when pre-provisioned)
- [ ] IdP group names updated in `files/application-production.yaml` or `appConfig.inlineYaml`
- [ ] Chart installed with `-f values-production.yaml` (not default evaluator posture)
- [ ] NetworkPolicies supported by cluster CNI (or `networkPolicy.enabled=false` if your platform requires it)
- [ ] Resource requests/limits acceptable for your namespace quota

See [operator install flow](../../flows/operator-install.md) for the full procedure.
