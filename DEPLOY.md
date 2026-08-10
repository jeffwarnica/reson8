# Reson8 — OpenShift Build & Deploy Guide

Reson8 is a cluster-observer workload: it watches the Kubernetes API for events and
metrics and uses them to drive a GStreamer audio pipeline served as an HTTP stream.
Deployment therefore has two layers of setup that a standard web service does not need:
a custom GStreamer base image and cluster-scoped RBAC.

For day-to-day contributor code/test/build/deploy loops, see `CONTRIBUTING.md`.

For concept-first external evaluation with prebuilt Quay images and Helm, see
`docs/poc/helm-evaluator.md`.

---

## Prerequisites

| Tool | Why |
| ------ | ----- |
| `oc` CLI, logged in | Applies manifests and drives BuildConfigs |
| Project-admin rights on runtime (`reson8`) and builder (`reson8-build`) namespaces | Applies generated Quarkus manifests and builder resources |
| Cluster-admin rights (bootstrap only) | Creates `ClusterRole` / `ClusterRoleBinding` |
| Java 21 + Maven wrapper (`./mvnw`) | Builds the JAR locally before triggering the in-cluster build |
| `podman` on a subscribed RHEL host (recommended) | Builds and pushes `reson8-base` when cluster repos are incomplete |

---

## Step 1 — Bootstrap (one time, cluster-admin)

These steps create the runtime namespace, bootstrap a dedicated builder namespace
for `reson8-base`, and grant the app the RBAC it needs. Run once per cluster.
Re-run when base-build automation or RBAC changes.

### 1a. Create the namespace

```bash
oc new-project reson8
```

### 1b. Bootstrap the builder namespace (`reson8-base`, best-effort)

Run the builder bootstrap script from repo root:

```bash
./deploy/openshift/deploy-reson8-builder.sh
```

Defaults:

- Builder namespace: `reson8-build` (`OPENSHIFT_BUILD_PROJECT`)
- Runtime namespace that pulls base image: `reson8` (`OPENSHIFT_PROJECT`)

The script:

1. Creates the builder namespace if missing.
2. Applies `deploy/openshift/cluster_build_config/*.yaml.tmpl` with `envsubst`.
3. Installs:
   - a scheduled UBI `ImageStream` import (`ubi10-openjdk-21:latest`)
   - `reson8-base` `ImageStream`
   - `reson8-base` `BuildConfig` with `ImageChange` trigger from UBI
   - cross-namespace image-puller RBAC so runtime namespace ServiceAccounts can pull from builder namespace
4. Triggers a one-shot entitlement sync job immediately, so the build namespace receives
   `etc-pki-entitlement` now (without waiting for the hourly cron schedule).
5. Triggers an initial `reson8-base` build (unless `SKIP_INITIAL_BUILD=1`).

> **Important reliability note**
>
> This in-cluster build path is **best-effort**. Even with entitlement sync, some clusters do
> not expose the full set of required GStreamer RPMs to UBI-based BuildConfigs.
> The BuildConfig now preflights those RPMs and fails early with a clear error when they are
> unavailable. Treat that failure as a signal to use the local subscribed-host flow below.

The entitlement sync template (`001-rpm-entitlement.yaml.tmpl`) uses `${TARGET_NS}` and is rendered by the same script.

Resulting base image location:
`image-registry.openshift-image-registry.svc:5000/reson8-build/reson8-base:latest`

That is the `FROM` address used by `reson8/src/main/docker/Dockerfile.jvm`.

Verify:

```bash
oc get is -n reson8-build
oc get bc -n reson8-build
oc get builds -n reson8-build --sort-by=.metadata.creationTimestamp | tail -n 5
```

#### 1b-alt. Local subscribed-host build (recommended)

Build `reson8-base` from a registered RHEL workstation (RHSM/Satellite) and push to the same
builder namespace image repository. This is the **recommended** path because it is the most
deterministic way to source the required GStreamer RPMs.

From repo root:

```bash
podman build --pull=always --no-cache \
  -f reson8/src/main/docker/Containerfile.gstreamer-base \
  -t localhost/reson8-base:local \
  reson8
```

Push as `reson8-build/reson8-base:latest`:

```bash
REGISTRY="$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}')"
podman login -u "$(oc whoami)" -p "$(oc whoami -t)" "$REGISTRY"
podman tag localhost/reson8-base:local "$REGISTRY/reson8-build/reson8-base:latest"
podman push "$REGISTRY/reson8-build/reson8-base:latest"
```

Then run the normal app deploy to rebuild runtime image layers against the refreshed base:

```bash
CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod
```

This path is useful both when BuildConfig repo access is incomplete and for developers who can
read cluster monitoring streams but do not have permissions to run/modify BuildConfigs in the
builder namespace.
For full local-from-scratch run paths (JVM dev mode and local containerized runtime),
see `CONTRIBUTING.md`.

### 1c. Apply cluster-scoped RBAC

The app holds a `ClusterRole` with read/watch access to events, nodes, pods, and
deployments cluster-wide, plus the ability to create `ServiceAccount` token requests
(used to authenticate with Thanos). A second binding grants `cluster-monitoring-view`
so the app can reach the Thanos querier in `openshift-monitoring`.

This cluster-scoped posture is the **tested default** for the included `application.yml`
signal-map and soundscape definitions. Less-privileged access can work, but only when
paired with intentionally narrower signal/query configuration.

```bash
# Requires cluster-admin
oc apply -f deploy/rbac.yml
```

Verify:

```bash
oc get clusterrole reson8-cluster-observer
oc get clusterrolebinding reson8-cluster-observer reson8-monitoring-view
```

For `cluster-test` namespaces (`${BASE_NS}-dev-${USER}`), ensure the ServiceAccount
subject in any ClusterRoleBinding targets that namespace/SA identity. The checked-in
`deploy/rbac.yml` binds `system:serviceaccount:reson8:reson8` for the shared `reson8`
runtime lane.

### 1d. Register the OIDC OAuth client (namespace-admin, one time)

OpenShift's OAuth server supports **ServiceAccounts as OAuth clients** — no cluster-admin and no
`OAuthClient` cluster resource required. Registering the client is done entirely inside the `reson8`
namespace by annotating the `reson8` ServiceAccount with a dynamic redirect reference:

```yaml
serviceaccounts.openshift.io/oauth-redirectreference.primary: >-
  {"kind":"OAuthRedirectReference","apiVersion":"v1","reference":{"kind":"Route","name":"reson8"}}
```

This annotation is already in `reson8/src/main/kubernetes/openshift.yml` and is applied
automatically by `./mvnw … -Dquarkus.openshift.deploy=true` — **no manual step is needed**.

The Deployment receives two env vars wired by `application.properties`:

| Env var | Source | Value |
| ------- | ------ | ----- |
| `OIDC_CLIENT_ID` | Hard-coded in Deployment | `system:serviceaccount:reson8:reson8` |
| `OIDC_CLIENT_SECRET` | `reson8-oauth-client-secret` Secret, key `token` | Long-lived SA token, auto-populated by the token controller |

The Secret (`reson8-oauth-client-secret`, type `kubernetes.io/service-account-token`) is also declared
in `openshift.yml` and applied by Maven. OpenShift's token controller fills the `token` field as soon as
the SA exists; no post-deploy step is required.

**Verify after first deploy:**

```bash
# Confirm the annotation is on the SA
oc get sa reson8 -n reson8 -o jsonpath='{.metadata.annotations}' | jq

# Confirm the secret has been populated with a token
oc get secret reson8-oauth-client-secret -n reson8 \
  -o jsonpath='{.data.token}' | base64 -d | head -c 40 && echo

# Confirm the env var reaches the pod
oc exec -n reson8 deploy/reson8 -- env | grep OIDC_CLIENT
```

> **If the namespace is `reson8` but you rename it**: update `OIDC_CLIENT_ID` in
> `reson8/src/main/resources/application.properties` to match the new namespace before deploying.

### 1e. Create the application ConfigMap

`application.yml` is mounted into every pod at `/deployments/config/application.yaml`.
Quarkus reads that path automatically as a higher-priority config source than the
copy bundled in the JAR, so you can tune signal maps, soundscapes, and Thanos queries
without rebuilding the image.

#### Cluster overlay (required for OIDC and tier configuration)

The bundled `application.yml` intentionally does **not** set `quarkus.oidc.enabled`.
Profile keys in `application.properties` disable OIDC in `%dev`/`%test`; cluster overlay should set `quarkus.oidc.enabled: true`
in the cluster, use a **cluster overlay file**:

```bash
# One-time setup: copy the example and fill in your cluster values
cp deploy/openshift/runtime_config/application-cluster-overlay.example.yaml \
   deploy/openshift/runtime_config/application-cluster-overlay.yaml
# Edit the file — set quarkus.oidc.enabled, admin-groups, viewer-groups, etc.
# This file is gitignored; never commit it.
```

The deploy script automatically deep-merges `runtime_config/application-cluster-overlay.yaml` over `application.yml`
before syncing the ConfigMap. Without the overlay, every deploy would clobber any cluster-specific
configuration you added manually.

**Why**: `oc apply` on a ConfigMap replaces `data.application.yaml` entirely on each sync.
Any keys not in the source file — including `quarkus.oidc.enabled: true` — are lost.
The overlay file is the safe, repeatable way to supply those keys.

**Helm installs:** the chart default is evaluator posture. For production app-config via Helm,
pass `-f helm/reson8/values-production.yaml` (see `helm/reson8/README.md`). The ConfigMap overlay
path above remains the canonical approach for Quarkus/OpenShift deploys via `./deploy/run.sh`.

#### Syncing the ConfigMap

Use the deploy script (preferred — merges overlay automatically):

```bash
CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod
```

Or manually (no overlay merge — OIDC and group config from overlay will be lost):

```bash
# Run from the repo root
oc create configmap reson8-config \
  --from-file=application.yaml=reson8/src/main/resources/application.yml \
  -n reson8 \
  --dry-run=client -o yaml | oc apply -f -
```

> **CI/CD note**: sync the ConfigMap *before* the Maven deploy step so the ConfigMap is current
> before the new pod starts. If you change `application.yml` or the overlay on a running cluster
> without a full redeploy, roll the deployment to pick up the new values:
>
> ```bash
> oc rollout restart deployment/reson8 -n reson8
> ```

Verify:

```bash
oc get configmap reson8-config -n reson8
oc describe configmap reson8-config -n reson8
```

### 1f. Dev vs package vs cluster runtime (OIDC + tiers)

Three deliberately different setups:

| Situation | OIDC | Tier simulation | Typical tier enforcement |
| ----------- | ------ | ----------------- | --------------------------- |
| **`quarkus:dev`** (`%dev`) | Off — `%dev.quarkus.oidc.enabled=false` | `reson8-dev-tier` cookie when the dev toolbar is on | Defaults stay **on**; use tier-simulation cookie or relax **`reson8.security.endpoint-authorization-enabled`** in local overrides if you want a completely open REST surface. **Thanos:** outside the cluster set **`RESON8_K8S_THANOS_BASE_URL`** to your monitoring Route (template **`reson8/application-local-DIST.properties`**); avoid committing cluster-specific URLs. |
| **`mvn package` / image build** (`%prod` bundle) | Unqualified key unset (Quarkus default true) | N/A | Bootstrap resolves issuer **at runtime** in-cluster once pods set **`quarkus.oidc.enabled=true`** (see OIDC subsection). |
| **Pod on OpenShift** (mounted `application.yaml`) | **`quarkus.oidc.enabled: true`** — mounted config source ordinal **>** JAR | **`dev-tier-cookie-enabled`** must stay **false** | Merge **`quarkus.oidc`** from [`deploy/openshift/runtime_config/application-cluster-overlay.example.yaml`](deploy/openshift/runtime_config/application-cluster-overlay.example.yaml). |

**Reasonable defaults for many clusters** (adjust group names to match your IdP’s claims):

- **`cluster-admins`** → **admin** tier (full control + debug).
- A dedicated **`viewer-groups`** entry (example file uses **`reson8-viewers`**) → authenticated **read-only** UI (**viewer** tier: sees control state, cannot mutate).
- **`stream-groups`** includes **`__anonymous__`** → unauthenticated callers can match **stream-only** (listen) when ingress does not force authentication on `/audio/stream`.

**Anonymous “read-only” vs listen-only:** **viewer** tier requires JWT **groups**; purely anonymous callers never become viewers. Anonymous users only reach **stream-only** via the **`__anonymous__`** sentinel (**listen**, **`canViewControlState`** false) when ingress allows tokenless traffic for that path.

Example overlay (see step 1e for the full workflow):

[`deploy/openshift/runtime_config/application-cluster-overlay.example.yaml`](deploy/openshift/runtime_config/application-cluster-overlay.example.yaml)

Copy to `deploy/openshift/runtime_config/application-cluster-overlay.yaml` (gitignored), fill in your group names, then run the deploy script — it merges the overlay automatically.

---

## Step 2 — Deploy (developer, every release)

Use the top-level mode wrapper from repo root:

```bash
# Local developer loop (no cluster apply)
./deploy/run.sh local-dev

# In-cluster test lane (namespace: reson8-dev-$USER)
./deploy/run.sh cluster-test

# Preview-only (no cluster mutations; uses oc diff)
./deploy/run.sh cluster-test --dry-run
```

`cluster-test` uses script-driven reconciliation:

1. Ensures/switches to the test namespace (`${BASE_NS}-dev-${USER}`).
2. Reconciles builder resources in the shared builder namespace (default `reson8-build`).
3. Builds/publishes `reson8-base` according to `BASE_IMAGE_SOURCE`:
   - `auto` (default): try in-cluster build first; if it fails, fall back to local build+push.
   - `cluster`: use only in-cluster BuildConfig flow.
   - `local`: use only local subscribed-host build+push.
   (`FORCE_BASE_REBUILD=1` still forces an in-cluster rebuild when the cluster path is used.)
4. Syncs `reson8-config` by merging `application.yml` with
   `runtime_config/application-cluster-overlay.yaml`.
5. Runs Maven in **generation-only** mode (no direct deploy/push/build) and writes
   manifests under the configured output directory (default `.generated/quarkus`, gitignored).
6. Applies generated OpenShift manifests via `oc apply`.
7. Waits for rollout/readiness and performs a route health check for `reson8`.
8. Generates/applies `disson8` in the same namespace and waits for `disson8` rollout/readiness.

Runtime image tags follow the current Maven project version from `pom.xml`, not `latest`.
In `cluster-test`, the image group/namespace is `${BASE_NS}-dev-${USER}` for both modules.

Generation-only Maven flags used by the wrapper:

```bash
-Dquarkus.openshift.deploy=false
-Dquarkus.container-image.build=false
-Dquarkus.container-image.push=false
-Dquarkus.kubernetes.output-directory=.generated/quarkus
```

`deploy/run.sh` is the only supported deploy entrypoint.

`cluster-test --dry-run` performs the same generation and reconciliation logic but runs
`oc diff` instead of `oc apply`, skips base-image rebuild, and skips rollout/readiness checks.

Example base-image source overrides:

```bash
# Force local subscribed-host base image build+push
BASE_IMAGE_SOURCE=local ./deploy/run.sh cluster-test

# Force in-cluster only (disable fallback)
BASE_IMAGE_SOURCE=cluster ALLOW_LOCAL_BASE_FALLBACK=0 ./deploy/run.sh cluster-test
```

### Readiness gate

The pod's readiness probe hits `/q/health/ready`, which aggregates MicroProfile readiness checks:

- **Kubernetes API** — lists cluster nodes via the Kubernetes client; response includes `nodesListed` (count). The pod stays **DOWN** until this succeeds (same as before: RBAC and `ServiceAccount` must be in place—see below).
- **Thanos querier** — `GET /api/v1/labels` against the configured Thanos/Prometheus API with the same bearer token used for metric queries; confirms monitoring ingress from the app's perspective. Calls use **`java.net.http.HttpClient`** with JVM-default TLS trust — the pod entrypoint runs `update-ca-trust` before the JVM starts (see CA trust below), so all cluster CAs (service CA, ingress CA, kube CA) are present with zero application-level configuration.

Disable the Thanos check only when necessary (e.g. unit tests): `reson8.k8s.thanos.readiness-check=false` (`%test` sets this by default in `application.properties`).

Until readiness is **UP**:

- The `reson8` `ServiceAccount` exists (created by the deploy step), and
- The `ClusterRoleBinding` from step 1c is in place, and
- (When the Thanos check is enabled) Thanos is reachable with `cluster-monitoring-view` rights, and
- The cluster-admin CA trust setup below has been completed, so all three CA bundles are available.

This is intentional — traffic is not routed until the app can reach the cluster and (in production-like profiles) monitoring.

#### CA trust — OS-level, zero application configuration

At pod startup the entrypoint script (`run-with-ca-update.sh`) projects three auto-created OpenShift ConfigMaps into `/etc/pki/ca-trust/source/anchors/` and calls `update-ca-trust extract` before the JVM starts. The JVM inherits a fully-populated OS trust store. **No application-level TLS configuration is needed or permitted.**

| File projected into anchors | Source ConfigMap | Auto-created? | Trusts |
| --- | --- | --- | --- |
| `service-ca.crt` | `openshift-service-ca.crt` | Yes — OpenShift creates in every namespace | All `*.svc.cluster.local` serving certs (Thanos, k8s API) |
| `ingress-ca.crt` | `reson8-trusted-ca-bundle` key `ca-bundle.crt` | Via label `config.openshift.io/inject-trusted-cabundle: "true"` — populated after the one-time cluster-admin setup below | Router/ingress CA for `*.apps.*` (OIDC endpoint) |
| `kube-ca.crt` | `kube-root-ca.crt` | Yes — OpenShift creates in every namespace | Kubernetes API server certificate |

**If `service-ca.crt`, `ingress-ca.crt`, or `kube-ca.crt` is missing or empty, the pod exits immediately with a FATAL message and does not start the JVM. No configuration override exists.**

#### Cluster CA Setup (one-time, cluster-admin)

The `openshift-service-ca.crt` and `kube-root-ca.crt` ConfigMaps are auto-created by OpenShift in every namespace and require no setup. The ingress/router CA requires a one-time cluster-admin operation:

```bash
# 1. Extract the router CA from the openshift-ingress-operator namespace
oc get secret router-ca -n openshift-ingress-operator \
  -o jsonpath='{.data.tls\.crt}' | base64 -d > router-ca.crt

# 2. Create a ConfigMap in openshift-config with the router CA
oc create configmap custom-ca --from-file=ca-bundle.crt=router-ca.crt -n openshift-config

# 3. Patch the cluster proxy to reference this ConfigMap as the trusted CA bundle
oc patch proxy cluster --type=merge -p '{"spec":{"trustedCA":{"name":"custom-ca"}}}'
```

After step 3, the cluster-network-operator injects the router CA into any namespace-scoped ConfigMap labelled `config.openshift.io/inject-trusted-cabundle: "true"` — including `reson8-trusted-ca-bundle` — as the key `ca-bundle.crt`. The application code assumes this has been done; it fails hard on startup if the file is missing.

**This is a one-time cluster operation.** Once configured, all namespaces with the label automatically receive the router CA. Verify with:

```bash
oc get configmap reson8-trusted-ca-bundle -n reson8 \
  -o jsonpath='{.data.ca-bundle\.crt}' | openssl x509 -noout -subject
```

The code also assumes `openshift-service-ca.crt` is available 100% of the time. If it is absent, the code fails hard — no workaround or configuration override exists or is allowed.

### Checking the deployment

Use namespace `reson8` for `prod`, or `${BASE_NS}-dev-${USER}` for `cluster-test`.

```bash
# Watch rollout
oc rollout status deployment/reson8 -n reson8

# Pod logs
oc logs -f deployment/reson8 -n reson8

# Exposed route
oc get route reson8 -n reson8
```

The audio stream is available at:

```text
https://<route-host>/audio/stream
```

---

## Image layout

```text
image-registry.../reson8-build/reson8-base:latest
  └── ubi10/openjdk-21:latest
      └── gstreamer1 + gstreamer1-plugins-base + gstreamer1-plugins-good + gstreamer1-plugins-bad-free

image-registry.../<runtime-namespace>/reson8:<maven-project-version>
  └── reson8-base:latest
      ├── /opt/reson8/sounds/   (WAV assets extracted from app JAR during image build)
      ├── /deployments/config/  (ConfigMap reson8-config mounted here at pod start)
      └── /deployments/         (Quarkus fast-JAR layers)

image-registry.../<runtime-namespace>/disson8:<maven-project-version>
  └── UBI9 OpenJDK 21 S2I builder chain (does not consume reson8-base)
```

---

## Configuration reference

All deployment config lives in `reson8/src/main/resources/application.properties`
under the `# --- OpenShift deployment ---` block. Key values:

| Property | Value | Notes |
| ---------- | ------- | ------- |
| `quarkus.container-image.registry` | `image-registry.openshift-image-registry.svc:5000` | Internal registry in-cluster address |
| `quarkus.container-image.group` | `reson8` | Wrapper scripts override this to the active target namespace (for `cluster-test`: `${BASE_NS}-dev-${USER}`) |
| `quarkus.openshift.build-strategy` | `docker` | OpenShift **Dockerfile** strategy (fixed API value `docker`). Uses `Dockerfile.jvm`; required for GStreamer base + sounds. Cluster performs the build |
| `quarkus.container-image.builder` | `openshift` | With `quarkus-container-image-podman` also on the classpath, keeps **in-cluster** deploy as the default. For **local** image builds with Podman, use `-Dquarkus.container-image.builder=podman -Dquarkus.container-image.build=true` |
| `quarkus.openshift.service-account` | `reson8` | SA that holds the ClusterRoleBinding |
| `quarkus.openshift.replicas` | `1` | Not horizontally scalable (GStreamer pipeline + audio stream) |
| `quarkus.openshift.route.expose` | `true` | Creates an OpenShift Route |
| `quarkus.openshift.env.vars.RESON8_AUDIO_PATH` | `/opt/reson8` | Base path for `WavCache` filesystem lookups |
| `quarkus.openshift.config-map-volumes.app-config.config-map-name` | `reson8-config` | ConfigMap mounted at `/deployments/config/` |
| `quarkus.openshift.mounts.app-config.path` | `/deployments/config` | Quarkus reads `application.yaml` here at higher priority than the bundled JAR copy |

### SPA security (`reson8.security`)

Tune these keys in the mounted `application.yml` (or equivalent properties) when OIDC / tiered UI is enabled:

| Key | Purpose |
| ----- | --------- |
| `reson8.security.admin-groups` | IdP group names for full operator control |
| `reson8.security.viewer-groups` | Read + stream; no mutations |
| `reson8.security.stream-groups` | Stream-only tier; may include the anonymous sentinel |
| `reson8.security.anonymous-stream-sentinel` | Defaults to `__anonymous__`; when this exact string appears in `stream-groups`, unauthenticated users may match stream-only (see product security doc for OAuth sidecar / ingress caveats) |
| `reson8.security.login-available` | Advertised to the SPA (`GET /api/capabilities`); set `true` in production when OIDC login is wired |
| `reson8.security.dev-tier-cookie-enabled` | Enables `reson8-dev-tier` cookie simulation — **`false` in production** |
| `reson8.security.endpoint-authorization-enabled` | Enforces tiers on `/audio/control`, `/audio/drop`, `/api/debug`, and `/audio/stream`; defaults **`true`** on `Reson8Config.SecurityConfig` |

**Build identity (`/q/info`):** packaged defaults require **authentication** (`quarkus.http.auth.permission.info.policy=authenticated`). The SPA loads this after capabilities and shows version / git commit when the call succeeds. `%dev` / `%test` use `permit` so local work without OIDC. Do not put `/q/*` under a blanket authenticated policy — readiness (`/q/health/ready`) must stay probe-open.

Lists may be empty. The **same group must not appear in more than one** of the three lists — the application fails fast at startup if they overlap. When resolving a subject, tier precedence is **admin > viewer > stream**.

Local **`quarkus:dev`** files that contain cluster Routes or secrets must **not** be committed: copy **`reson8/application-local-DIST.properties`** → **`application-local.properties`** (gitignored) or use env vars — see **`.cursor/rules/secrets-no-git.mdc`**.

### OIDC bootstrap (`Reson8OidcBootstrapConfigSourceFactory`)

Application defaults for OIDC bootstrap live on `reson8.oidc.*` and `reson8.openshift-oauth.*` keys consumed by **`Reson8OidcBootstrapConfigSourceFactory`** (plus `Reson8Config.OpenshiftOauthConfig` mapping for documented defaults). `application.properties` supplies profile posture (`%dev` / `%test`) only — authoritative defaults remain on config mappings and factories (Thanos `base-url` default is on `Reson8Config.ThanosConfig`). A **`ConfigSourceFactory`** (**`Reson8OidcBootstrapConfigSourceFactory`**, ordinal **450**) materializes **`quarkus.oidc.*`** when OIDC is effectively enabled:

- The bundled **`application.properties`** leaves unqualified `quarkus.oidc.enabled` unset (Quarkus default is `true`) and sets `%dev/%test` to `false`. Production pods still set **`quarkus.oidc.enabled: true`** in mounted **`application.yaml`** for explicit operator intent and easier troubleshooting.
- If **`quarkus.oidc.enabled`** is set explicitly (including **`false`** from the bundled default above), the factory **does not override it** — local `quarkus:dev` and CI stay off OIDC without cluster IdP until you raise **`quarkus.oidc.enabled`** via env or higher-ordinal config.
- If **`quarkus.oidc.enabled`** is **unset** at runtime (e.g. stripped from overlay config), **`reson8.oidc.enabled`** (default **`true`**) decides. When **`false`**, the factory emits only **`quarkus.oidc.enabled=false`**.

When enabled, it sets Quarkus OIDC application type (default **`hybrid`**: Bearer-token APIs keep working while browsers can hit **`GET /login`** to run the authorization-code flow), redirect HTTPS scheme, OpenShift OAuth endpoints, and client id/secret (env bridges **`OIDC_CLIENT_ID`** / **`OIDC_CLIENT_SECRET`** via **`reson8.oidc.*`** defaults). TLS uses the JVM default trust store; no per-OIDC-client cert configuration is emitted.

**OAuth redirect URIs:** Register callbacks for the ServiceAccount OAuth client (via the SA annotation/OAuthRedirectReference flow) that match Quarkus OIDC for your Quarkus minor version (typically **`https://<route-host>/q/oidc/*`** paths — check OIDC startup logs or Quarkus OIDC docs if login loops or **`invalid_redirect_uri`** errors appear). The SPA **`Login`** button navigates to **`/login`** ( **`OidcLoginGatewayResource`** ); after IdP success it redirects back to **`/`**. The bundled JAR sets **`quarkus.http.proxy.proxy-address-forwarding=true`** and (when OIDC is on) **`quarkus.oidc.authentication.force-redirect-https-scheme`** from **`reson8.oidc.force-redirect-https-scheme`** (default **`true`**) so redirects use the public **`https`** host OpenShift presents, not the pod’s internal **`http`** view — if you still see **`http://…`** or router **“Application is not available”** after login, verify the Route sends standard **`Forwarded` / `X-Forwarded-*`** headers and that the OAuth client allows the **`https`** callback URLs.

**`403 Forbidden` on `GET /login`:** With **`application-type=hybrid`**, Quarkus OIDC treats *any* present **`Authorization`** request header as “Bearer/API mode” and skips the browser authorization-code path; proxies occasionally inject an empty or junk header. **`OidcHybridLoginAuthorizationSanitizer`** removes **`Authorization`** on **`GET /login`** before authentication runs so login can redirect to the IdP. **Also:** if OIDC is effectively **off** (**`quarkus.oidc.enabled`** still **`false`** — missing ConfigMap merge / wrong priority / env unset), **`@Authenticated`** on **`/login`** yields **`403`** — ensure mounted **`application.yaml`** sets **`quarkus.oidc.enabled: true`** (§1e).

**Issuer URL (`quarkus.oidc.auth-server-url`)** is resolved as follows:

1. If **`reson8.openshift-oauth.auth-server-url`** is non-empty (including **`OPENSHIFT_AUTH_ISSUER_URL`** / **`OIDC_AUTH_SERVER_URL`** from the **`@WithDefault`** on the config mapping), it is used and discovery is skipped.
2. Else if **`reson8.openshift-oauth.discovery-enabled`** is **`true`** (default): **HTTP GET** on **`reson8.openshift-oauth.metadata-url`**, or the in-cluster default  
   `https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}/.well-known/oauth-authorization-server`  
   using the pod **service-account token** when present. TLS uses the **JVM default trust store** — populated by `update-ca-trust` in the pod entrypoint before the JVM starts (see CA trust section). The JSON **`issuer`** becomes **`quarkus.oidc.auth-server-url`** (public issuer for user tokens).
3. Else (**discovery disabled**) an explicit **`reson8.openshift-oauth.auth-server-url`** is **required**.

For **cluster CA trust**:

1. **`openshift.yml`** ships an (initially empty) **`ConfigMap`** **`reson8-trusted-ca-bundle`** with the label `config.openshift.io/inject-trusted-cabundle: "true"` — the cluster-network-operator injects **`ca-bundle.crt`** (the router/ingress CA) after the cluster-admin CA setup above.
2. **`openshift.yml`** also includes a **`Deployment`** fragment projecting three auto-created ConfigMaps (`openshift-service-ca.crt`, `reson8-trusted-ca-bundle`, `kube-root-ca.crt`) into `/etc/pki/ca-trust/source/anchors/`. The pod entrypoint calls `update-ca-trust extract` before the JVM starts. See the CA trust section above.

Do **not** use a bare **`Deployment`** fragment that only tweaks **`spec.template`** — Quarkus merges fragments such that **`containers`** can disappear (**`spec.template.spec.containers: Required value`**).

JWT **`groups`** → roles align with **`AccessTierResolver`**. Fine-tune via **`reson8.oidc.*`** in mounted config or ConfigMap.

| Key | Purpose |
| ----- | --------- |
| `reson8.oidc.*` | Mirrors **`quarkus.oidc.*`** defaults (enabled, application type, redirect-HTTPS, client id/secret) |
| `reson8.openshift-oauth.discovery-enabled` | **`true`** by default — query OAuth authorization server metadata for `issuer` |
| `reson8.openshift-oauth.metadata-url` | Override full metadata URL when not using the in-cluster API server default |
| `reson8.openshift-oauth.auth-server-url` | Explicit issuer; skips discovery when set |
| `OPENSHIFT_AUTH_ISSUER_URL` | Preferred env bridge into **`auth-server-url`** |
| `OIDC_AUTH_SERVER_URL` | Fallback env bridge |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | OAuth client (mirrors **`reson8.oidc.client-id` / `client-secret`** defaults) |

**Anonymous stream-only tier (`__anonymous__` sentinel)** needs tokenless HTTP if you rely on tier logic alone. If ingress forces authentication on every path, anonymous listeners never reach the app; exempt `/audio/stream` at ingress/proxy.

If an ingress OAuth sidecar forces authentication on **all** paths, anonymous stream-only users never reach the app. Allow unauthenticated paths for `/audio/stream` (and static SPA assets) at the proxy, use path exemptions, or document SSO accordingly.

---

## Troubleshooting

**`Deployment ... spec.template.spec.containers: Required value` when running `mvn ... -Dquarkus.openshift.deploy=true`**

Usually caused by a **`Deployment`** fragment in **`openshift.yml`** that specifies **`spec.template`** but the **`containers`** list is missing the required `name` field for Dekorate to merge by. The current fragment in **`openshift.yml`** specifies `name: reson8` in the container entry so Dekorate can merge `volumeMounts` correctly. Do not remove or rename the container entry.

**Pod stuck in `NotReady`**
Check the readiness probe failure reason:

```bash
oc describe pod -l app.kubernetes.io/name=reson8 -n reson8
```

Most likely cause: RBAC not yet applied. Confirm with:

```bash
oc auth can-i list nodes --as=system:serviceaccount:reson8:reson8
```

**`HTTP 503` from the Route (`curl https://…/login`) with HTML body and no matching line in Quarkus logs**

That response is almost always generated **by the OpenShift router / HAProxy** when it cannot obtain a healthy HTTP response from the **`Service`** backends — traffic often **never reaches** the JVM (so an empty pod access log is expected).

Verify endpoints and compare in-cluster vs via Route:

```bash
oc get endpoints reson8 -n reson8 -o wide
oc describe route reson8 -n reson8
oc exec deploy/reson8 -n reson8 -- curl -s -o /dev/null -w "pod-http-status=%{http_code}\n" http://127.0.0.1:8090/
```

If **`pod-http-status`** is **`200`** (or **`302`** on **`/login`** when OIDC is on) but the Route still returns **`503`**, focus on **`Endpoints`** / **`Pod`** readiness / **`Service`** selectors — not application routing logic.

**`Forbidden` / `401 Unauthorized` errors in pod logs against Thanos**

The `cluster-monitoring-view` ClusterRoleBinding may be missing:

```bash
oc get clusterrolebinding reson8-monitoring-view
```

If missing, re-apply RBAC:

```bash
APPLY_CLUSTER_RBAC=1 CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod
# or: oc apply -f deploy/rbac.yml
```

`cluster-monitoring-view` covers the Thanos querier API (`/api/v1/labels`, basic queries) across the cluster.
However, **cross-namespace `sum(rate(...))` aggregation queries** (like the `Web Crashes` signal) may require
additional `view` access on the source namespaces:

```bash
# Grant view on a specific source namespace (repeat per namespace as needed)
oc adm policy add-role-to-user view \
  system:serviceaccount:reson8:reson8 \
  -n <source-namespace>
```

Check the pod logs for the signal name that returned 401 to identify which namespaces are affected.

**GStreamer `WARN` or plugin-not-found errors**
The base image may be stale. Re-run `./deploy/openshift/deploy-reson8-builder.sh` (or
`oc start-build reson8-base -n reson8-build --wait`) and redeploy.

**`WavCache` file-not-found at startup**
The OpenShift **container build** did not place sounds under `/opt/reson8/sounds/`. Confirm the
sounds were copied by inspecting the image:

```bash
oc debug deployment/reson8 -n reson8 -- ls /opt/reson8/sounds/
```

**Config changes not taking effect after `application.yml` edit**
The pod caches the mounted ConfigMap. Re-sync and roll the deployment:

```bash
# Preferred — merges cluster overlay (OIDC, groups) automatically:
SYNC_CONFIGMAP=1 CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod -DskipTests

# Or manually (loses overlay keys — use only if you have no cluster overlay):
oc create configmap reson8-config \
  --from-file=application.yaml=reson8/src/main/resources/application.yml \
  -n reson8 --dry-run=client -o yaml | oc apply -f -
oc rollout restart deployment/reson8 -n reson8
```

**`dev` mode still works normally**
The deployment properties (`quarkus.container-image.*`, `quarkus.openshift.*`) are
read by Quarkus only when an image build or deploy is explicitly triggered. Running
`./mvnw quarkus:dev` is unaffected.
