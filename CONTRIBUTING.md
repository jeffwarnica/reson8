# Contributing to Reson8

This document covers the day-to-day developer loop. For cluster bootstrap and operator
runbooks, use `DEPLOY.md`.

## Development Loops

Reson8 has two distinct image build loops:

1. Base image loop (`reson8-base` in namespace `reson8-build`)
2. Application loop (`reson8` in namespace `reson8`)

Choosing the right loop keeps iteration fast and avoids unnecessary rebuilds.

## Access and workflow matrix

| Access level | Typical capabilities | Recommended workflow |
| --- | --- | --- |
| Local workstation only | No cluster write access | Use local run loop (`quarkus:dev`) and local tests; optionally use local container run path below |
| Cluster stream/observe only | Can read cluster metrics/streams, cannot modify BuildConfigs | Run locally for code/test; if allowed to push images, use local base-image fallback then app deploy by someone with namespace rights |
| Runtime namespace admin (`reson8`) | Can deploy app resources, may not control builder namespace | Use `deploy-reson8.sh` for app loop; request or coordinate `reson8-base` refresh with builder-capable user |
| Builder namespace admin (`reson8-build`) | Can run base BuildConfigs/ImageStreams | Use `oc start-build reson8-base -n reson8-build --wait` for base refresh + app deploy loop |
| Cluster admin | Full bootstrap rights | Use `DEPLOY.md` step-by-step bootstrap + standard contributor loops |

## 1) Inner loop: code + tests (fastest)

Use this while writing code:

```bash
./mvnw -pl reson8 quarkus:dev
./mvnw -pl reson8 -Dtest=YourTestName test
./mvnw -pl reson8 test
./mvnw -pl reson8 package
```

Use this loop for most Java and configuration behavior changes before cluster deploy.

### Local from-scratch run (no cluster required)

If you only need to develop and run locally, you do not need builder namespace access.

#### Option A: Local JVM dev mode (fastest)

```bash
./mvnw -pl reson8 quarkus:dev
```

Use local overrides/env as needed (for example `application-local.properties` copied from
`reson8/application-local-DIST.properties`) and keep secrets/routes out of git.

#### Option B: Local containerized run (uses local `reson8-base`)

1) Build local base image:

```bash
podman build --pull=always --no-cache \
  -f reson8/src/main/docker/Containerfile.gstreamer-base \
  -t localhost/reson8-base:local \
  reson8
```

1) Package app:

```bash
./mvnw -pl reson8 -DskipTests package
```

1) Build runtime image, overriding `Dockerfile.jvm` base `FROM` with your local image:

```bash
podman build \
  --from localhost/reson8-base:local \
  -f reson8/src/main/docker/Dockerfile.jvm \
  -t localhost/reson8-jvm-local \
  reson8
```

1) Run container locally:

```bash
podman run --rm -p 8090:8090 \
  -e JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Dquarkus.oidc.enabled=false" \
  localhost/reson8-jvm-local
```

This is the closest local equivalent to the in-cluster runtime image path.

## 2) Base image loop (`oc start-build`)

Use this when you change runtime base dependencies (UBI/GStreamer), or need to retry
or force a base rebuild:

```bash
oc start-build reson8-base -n reson8-build --wait
oc get builds -n reson8-build --sort-by=.metadata.creationTimestamp | tail -n 5
oc get is -n reson8-build
```

Why:

- `reson8-base` is managed by OpenShift `BuildConfig` + `ImageStream` triggers.
- This decouples infra/base refresh from app code packaging.

### Local from-scratch fallback for `reson8-base`

If you do not have access to rebuild in-cluster (or need to validate base changes from a
workstation), you can build `Containerfile.gstreamer-base` locally and push it to the
cluster registry.

Prerequisites:

- Workstation is registered to RHSM/Satellite (so UBI package repos resolve correctly)
- `podman`, `oc`, and login access to target OpenShift cluster/namespace
- Builder namespace exists (`reson8-build` by default)

Build locally from repo root:

```bash
podman build --pull=always --no-cache \
  -f reson8/src/main/docker/Containerfile.gstreamer-base \
  -t localhost/reson8-base:local \
  reson8
```

Push as the runtime-consumed tag:

```bash
# Internal registry route host (requires image registry default route enabled)
REGISTRY="$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}')"

# Login and push into builder namespace repository
podman login -u "$(oc whoami)" -p "$(oc whoami -t)" "$REGISTRY"
podman tag localhost/reson8-base:local "$REGISTRY/reson8-build/reson8-base:latest"
podman push "$REGISTRY/reson8-build/reson8-base:latest"
```

Then run app deploy loop so runtime image rebuilds against refreshed base:

```bash
CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod
```

## 3) Application deploy loop (run wrapper + Quarkus OpenShift deploy)

Use this when app code/config/manifests changed.

Preferred wrapper:

```bash
CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod
```

What it does:

- Deep-merges `reson8/src/main/resources/application.yml` with
  `deploy/openshift/runtime_config/application-cluster-overlay.yaml`
- Applies `reson8-config` ConfigMap
- Runs Maven with Quarkus OpenShift deploy enabled
- Applies generated OpenShift resources and triggers the app image build

Raw Maven equivalent:

```bash
./mvnw package -pl reson8 -Dquarkus.openshift.deploy=true
```

## When to use which

Use `oc start-build ...` when:

- Only base image/runtime stack changed
- You are retrying/recovering base build issues
- UBI import changed and you want a fresh `reson8-base`

Use Quarkus deploy when:

- App code changed
- `Dockerfile.jvm` or app packaging changed
- App manifests/config overlays changed
- You need full code -> image -> deployment consistency

## Recommended daily workflow

1. Code + local tests (`quarkus:dev`, focused tests)
2. Package locally (`./mvnw -pl reson8 package`)
3. Deploy app (`CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod`)
4. If runtime/base issues appear, rebuild base:
   `oc start-build reson8-base -n reson8-build --wait`
5. Re-run app deploy wrapper

## Notes

- Runtime app image `FROM` points to:
  `image-registry.openshift-image-registry.svc:5000/reson8-build/reson8-base:latest`
- Keep cluster-specific values in:
  `deploy/openshift/runtime_config/application-cluster-overlay.yaml` (local, gitignored)
