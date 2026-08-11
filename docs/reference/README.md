# Reference documentation

Topical lookup for configuration, platform setup, and troubleshooting. For step-by-step procedures, see [flow docs](../flows/).

## Configuration

| Topic | Description |
| --- | --- |
| [application-yml.md](configuration/application-yml.md) | Signal map, soundscapes, bundled runtime YAML |
| [application-properties.md](configuration/application-properties.md) | Quarkus profiles, OpenShift deployment block |
| [cluster-overlay.md](configuration/cluster-overlay.md) | Gitignored cluster overlay merge for Quarkus deploy |
| [helm-values.md](configuration/helm-values.md) | Chart values, evaluator vs production posture |
| [security-tiers.md](configuration/security-tiers.md) | `reson8.security.*`, tier precedence |
| [oidc.md](configuration/oidc.md) | OIDC bootstrap, redirects, issuer discovery |

## Platform

| Topic | Description |
| --- | --- |
| [prerequisites.md](platform/prerequisites.md) | Tools, permissions, production readiness gates |
| [cluster-ca-setup.md](platform/cluster-ca-setup.md) | One-time ingress CA trust (cluster-admin) |
| [rbac.md](platform/rbac.md) | ClusterRole, monitoring view, namespace grants |
| [bootstrap-builder.md](platform/bootstrap-builder.md) | `reson8-base` builder namespace |
| [readiness.md](platform/readiness.md) | Health checks, Thanos gate, single-replica model |
| [image-layout.md](platform/image-layout.md) | Base and runtime image chain |

## Troubleshooting

| Topic | Description |
| --- | --- |
| [troubleshooting.md](troubleshooting.md) | Consolidated symptom index |

## Deploy scripts

Flag-level documentation lives in each script's built-in help:

```bash
deploy/run.sh
deploy/publish-quay.sh --help
deploy/release-roundtrip.sh --help
```

Module defaults and toggles: [`deploy/deploy.conf`](../../deploy/deploy.conf).
