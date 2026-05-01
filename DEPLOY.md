# Reson8 — OpenShift Build & Deploy Guide

Reson8 is a cluster-observer workload: it watches the Kubernetes API for events and
metrics and uses them to drive a GStreamer audio pipeline served as an HTTP stream.
Deployment therefore has two layers of setup that a standard web service does not need:
a custom GStreamer base image and cluster-scoped RBAC.

---

## Prerequisites

| Tool | Why |
|------|-----|
| `oc` CLI, logged in | Applies manifests and drives BuildConfigs |
| Project-admin rights on the `reson8` namespace | Applies the generated Quarkus manifests |
| Cluster-admin rights (bootstrap only) | Creates `ClusterRole` / `ClusterRoleBinding` |
| Java 21 + Maven wrapper (`./mvnw`) | Builds the JAR locally before triggering the in-cluster build |

---

## Step 1 — Bootstrap (one time, cluster-admin)

These steps create the namespace, build the GStreamer base image inside the cluster,
and grant the app the RBAC it needs. Run once per cluster. Re-run only if the base
image or RBAC changes.

### 1a. Create the namespace

```bash
oc new-project reson8
```

### 1b. Build the GStreamer base image

The base image (`ubi10/openjdk-21` + GStreamer) **must be built on a subscribed
RHEL 10 host**. When Podman runs on a subscribed RHEL system it automatically
mounts `/run/secrets/rhsm` into the build container, giving `dnf` access to the
full RHEL 10 repo set (including AppStream where the GStreamer packages live).
OpenShift's in-cluster build pods do not carry a subscription, which is why this
step is done locally rather than via a BuildConfig.

**Expose the internal registry route (cluster-admin, once per cluster):**

```bash
oc patch configs.imageregistry.operator.openshift.io/cluster \
  --patch '{"spec":{"defaultRoute":true}}' \
  --type=merge
```

**Build and push (run from the repo root on a subscribed RHEL 10 host):**

```bash
# Resolve the external hostname of the internal registry
REGISTRY=$(oc get route default-route -n openshift-image-registry \
  -o jsonpath='{.spec.host}')

# Authenticate Podman against the internal registry
podman login -u $(oc whoami) -p $(oc whoami -t) $REGISTRY

# Build the GStreamer base image (subscription is used automatically via /run/secrets/rhsm)
podman build \
  -f reson8/src/main/docker/Containerfile.gstreamer-base \
  -t $REGISTRY/reson8/reson8-base:latest

# Push to the internal registry
podman push $REGISTRY/reson8/reson8-base:latest
```

The resulting image is available in-cluster at:
`image-registry.openshift-image-registry.svc:5000/reson8/reson8-base:latest`

This is the `FROM` address in `reson8/src/main/docker/Dockerfile.jvm`.

### 1c. Apply cluster-scoped RBAC

The app holds a `ClusterRole` with read/watch access to events, nodes, pods, and
deployments cluster-wide, plus the ability to create `ServiceAccount` token requests
(used to authenticate with Thanos). A second binding grants `cluster-monitoring-view`
so the app can reach the Thanos querier in `openshift-monitoring`.

```bash
# Requires cluster-admin
oc apply -f deploy/rbac.yml
```

Verify:
```bash
oc get clusterrole reson8-cluster-observer
oc get clusterrolebinding reson8-cluster-observer reson8-monitoring-view
```

### 1d. Create the application ConfigMap

`application.yml` is mounted into every pod at `/deployments/config/application.yaml`.
Quarkus reads that path automatically as a higher-priority config source than the
copy bundled in the JAR, so you can tune signal maps, soundscapes, namespace filters,
and Thanos queries without rebuilding the image.

Generate and apply the ConfigMap from source (idempotent — safe to re-run after any
change to `application.yml`):

```bash
# Run from the repo root
oc create configmap reson8-config \
  --from-file=application.yaml=reson8/src/main/resources/application.yml \
  -n reson8 \
  --dry-run=client -o yaml | oc apply -f -
```

> **CI/CD note**: include this command in your pipeline *before* the Maven deploy step
> so the ConfigMap is current before the new pod starts.  If you change `application.yml`
> on a running cluster without a full redeploy, roll the deployment to pick up the new
> values:
> ```bash
> oc rollout restart deployment/reson8 -n reson8
> ```

Verify:
```bash
oc get configmap reson8-config -n reson8
oc describe configmap reson8-config -n reson8
```

---

## Step 2 — Deploy (developer, every release)

Ensure the ConfigMap is current first (step 1d), then from inside the `reson8/`
Maven module directory:

```bash
cd reson8
./mvnw package -Dquarkus.openshift.deploy=true
```

This single command:

1. Compiles and packages the application JAR (`target/quarkus-app/`).
2. Creates or updates a `BuildConfig` in OpenShift with Docker strategy, pointing at
   `src/main/docker/Dockerfile.jvm`.
3. Sends `target/quarkus-app/` as the in-cluster build context. Sound assets are
   extracted from the bundled application JAR inside the Dockerfile (`jar xf`), so
   no extra files need to be present in the build context.
4. Waits for the image build to complete and pushes the result to the internal registry
   at `image-registry.openshift-image-registry.svc:5000/reson8/reson8:latest`.
5. Applies all generated manifests: `Deployment`, `Service`, `Route`, `ServiceAccount`.
   The `Deployment` includes a `volumeMount` for `reson8-config` at `/deployments/config/`
   (generated from the `quarkus.openshift.config-map-volumes` properties).

### Readiness gate

The pod's readiness probe hits `/q/health/ready`, which includes `K8sConnectivityCheck`.
That check calls `client.nodes().list()` against the API server. The pod will remain
`NotReady` until:
- The `reson8` `ServiceAccount` exists (created by step 5 above), and
- The `ClusterRoleBinding` from step 1c is in place.

This is intentional — traffic is not routed until the app can actually reach the cluster.

### Checking the deployment

```bash
# Watch rollout
oc rollout status deployment/reson8 -n reson8

# Pod logs
oc logs -f deployment/reson8 -n reson8

# Exposed route
oc get route reson8 -n reson8
```

The audio stream is available at:
```
http://<route-host>/audio/stream
```

---

## Image layout

```
image-registry.../reson8/reson8-base:latest
  └── ubi10/openjdk-21:latest
      └── gstreamer1 + gstreamer1-plugins-base + gstreamer1-plugins-good

image-registry.../reson8/reson8:latest
  └── reson8-base:latest
      ├── /opt/reson8/sounds/   (WAV assets extracted from app JAR during image build)
      ├── /deployments/config/  (ConfigMap reson8-config mounted here at pod start)
      └── /deployments/         (Quarkus fast-JAR layers)
```

---

## Configuration reference

All deployment config lives in `reson8/src/main/resources/application.properties`
under the `# --- OpenShift deployment ---` block. Key values:

| Property | Value | Notes |
|----------|-------|-------|
| `quarkus.container-image.registry` | `image-registry.openshift-image-registry.svc:5000` | Internal registry in-cluster address |
| `quarkus.container-image.group` | `reson8` | Must match the namespace |
| `quarkus.openshift.build-strategy` | `docker` | Uses `Dockerfile.jvm`; required for GStreamer base + sounds COPY |
| `quarkus.openshift.service-account` | `reson8` | SA that holds the ClusterRoleBinding |
| `quarkus.openshift.replicas` | `1` | Not horizontally scalable (GStreamer pipeline + audio stream) |
| `quarkus.openshift.route.expose` | `true` | Creates an OpenShift Route |
| `quarkus.openshift.env.vars.RESON8_AUDIO_PATH` | `/opt/reson8` | Base path for `WavCache` filesystem lookups |
| `quarkus.openshift.config-map-volumes.app-config.config-map-name` | `reson8-config` | ConfigMap mounted at `/deployments/config/` |
| `quarkus.openshift.mounts.app-config.path` | `/deployments/config` | Quarkus reads `application.yaml` here at higher priority than the bundled JAR copy |

---

## Troubleshooting

**Pod stuck in `NotReady`**
Check the readiness probe failure reason:
```bash
oc describe pod -l app.kubernetes.io/name=reson8 -n reson8
```
Most likely cause: RBAC not yet applied. Confirm with:
```bash
oc auth can-i list nodes --as=system:serviceaccount:reson8:reson8
```

**`Forbidden` errors in pod logs against Thanos**
The `cluster-monitoring-view` binding may be missing:
```bash
oc get clusterrolebinding reson8-monitoring-view
```

**GStreamer `WARN` or plugin-not-found errors**
The base image may be stale. Rebuild and re-push it (see Step 1b), then redeploy.

**`WavCache` file-not-found at startup**
The Docker build context did not include `src/main/resources/sounds/`. Confirm the
sounds were copied by inspecting the image:
```bash
oc debug deployment/reson8 -n reson8 -- ls /opt/reson8/sounds/
```

**Config changes not taking effect after `application.yml` edit**
The pod caches the mounted ConfigMap. Re-apply the ConfigMap and roll the deployment:
```bash
oc create configmap reson8-config \
  --from-file=application.yaml=reson8/src/main/resources/application.yml \
  -n reson8 --dry-run=client -o yaml | oc apply -f -
oc rollout restart deployment/reson8 -n reson8
```

**`dev` mode still works normally**
The deployment properties (`quarkus.container-image.*`, `quarkus.openshift.*`) are
read by Quarkus only when an image build or deploy is explicitly triggered. Running
`./mvnw quarkus:dev` is unaffected.
