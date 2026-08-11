# application.properties

Primary file: `reson8/src/main/resources/application.properties`.

## Profile splits

| Profile | OIDC | Notes |
| --- | --- | --- |
| `%dev` | Off (`%dev.quarkus.oidc.enabled=false`) | Dev-tier cookie simulation available |
| `%test` | Off | Silent audio, relaxed Thanos readiness |
| Unprefixed / cluster | Unqualified `quarkus.oidc.enabled` unset in JAR | Cluster mounted config sets `quarkus.oidc.enabled: true` |

## OpenShift deployment block

Keys under `# --- OpenShift deployment ---`:

| Property | Value | Notes |
| --- | --- | --- |
| `quarkus.container-image.registry` | `image-registry.openshift-image-registry.svc:5000` | Internal registry |
| `quarkus.container-image.group` | `reson8` | Overridden by `deploy/run.sh` to active namespace |
| `quarkus.openshift.build-strategy` | `docker` | Uses `Dockerfile.jvm` |
| `quarkus.container-image.builder` | `openshift` | In-cluster build default |
| `quarkus.openshift.service-account` | `reson8` | Holds ClusterRoleBinding |
| `quarkus.openshift.replicas` | `1` | Single-replica — see [readiness.md](../platform/readiness.md) |
| `quarkus.openshift.route.expose` | `true` | Creates Route |
| `quarkus.openshift.config-map-volumes.app-config.config-map-name` | `reson8-config` | Mounted at `/deployments/config/` |

Local image builds with Podman: `-Dquarkus.container-image.builder=podman -Dquarkus.container-image.build=true`.

## Thanos local override

Outside the cluster, set `RESON8_K8S_THANOS_BASE_URL` via `application-local.properties` (from `application-local-DIST.properties`). Authoritative default is on `Reson8Config.ThanosConfig`.

## Build identity (`/q/info`)

Packaged defaults require authentication for `/q/info`. `%dev` / `%test` use `permit`. Do not put `/q/*` under a blanket authenticated policy — `/q/health/ready` must stay probe-open.

## Code defaults

Authoritative application semantics use `@WithDefault` on `Reson8Config` and config source factories. Properties files hold deployment-specific overrides only — see `.cursor/rules/configuration-defaults.mdc`.
