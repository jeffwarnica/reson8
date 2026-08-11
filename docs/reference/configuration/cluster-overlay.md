# Cluster overlay

For Quarkus/OpenShift developer deploys, cluster-specific keys merge from a gitignored overlay file.

## Setup

```bash
cp deploy/openshift/runtime_config/application-cluster-overlay.example.yaml \
   deploy/openshift/runtime_config/application-cluster-overlay.yaml
# Edit: quarkus.oidc.enabled, admin-groups, viewer-groups, etc.
```

Path: `deploy/openshift/runtime_config/application-cluster-overlay.yaml` (gitignored — never commit).

## Merge behavior

`deploy/run.sh` deep-merges the overlay over `reson8/src/main/resources/application.yml` before syncing the `reson8-config` ConfigMap.

Without the overlay, each deploy replaces the entire ConfigMap and drops cluster-specific keys (including `quarkus.oidc.enabled: true`).

## Required keys for cluster runtime

| Key | Purpose |
| --- | --- |
| `quarkus.oidc.enabled: true` | Enable OIDC in cluster |
| `reson8.security.admin-groups` | IdP groups for admin tier |
| `reson8.security.viewer-groups` | Read-only UI tier |
| `reson8.security.stream-groups` | Stream-only tier; may include `__anonymous__` |
| `reson8.security.dev-tier-cookie-enabled: false` | Disable dev toolbar cookie in cluster |

Example: [`application-cluster-overlay.example.yaml`](../../../deploy/openshift/runtime_config/application-cluster-overlay.example.yaml).

## Manual ConfigMap sync (discouraged)

Prefer `deploy/run.sh` — manual `oc create configmap` without overlay merge loses OIDC and group config.

## Helm installs

Helm uses chart `appConfig` instead of this overlay path. See [helm-values.md](helm-values.md) and [quarkus-openshift-deploy flow](../../flows/quarkus-openshift-deploy.md) for when to use each path.
