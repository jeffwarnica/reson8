# Bootstrap builder namespace

Developer workflows that build from source need a GStreamer base image (`reson8-base`) in the internal registry.

## Builder bootstrap script

From repo root:

```bash
./deploy/openshift/deploy-reson8-builder.sh
```

Defaults:

- Builder namespace: `reson8-build` (`OPENSHIFT_BUILD_PROJECT`)
- Runtime namespace: `reson8` (`OPENSHIFT_PROJECT`)

The script creates the builder namespace, applies `deploy/openshift/cluster_build_config/*.yaml.tmpl`, installs UBI ImageStream import, `reson8-base` ImageStream and BuildConfig, cross-namespace pull RBAC, triggers entitlement sync, and starts an initial base build (unless `SKIP_INITIAL_BUILD=1`).

Resulting base image:

```text
image-registry.openshift-image-registry.svc:5000/reson8-build/reson8-base:latest
```

Used as `FROM` in `reson8/src/main/docker/Dockerfile.jvm`.

Verify:

```bash
oc get is -n reson8-build
oc get bc -n reson8-build
oc get builds -n reson8-build --sort-by=.metadata.creationTimestamp | tail -n 5
```

## In-cluster build reliability

The in-cluster BuildConfig path is **best-effort**. Some clusters do not expose required GStreamer RPMs to UBI BuildConfigs. The BuildConfig preflights RPM availability and fails early when packages are missing.

## Local subscribed-host build (recommended)

Build from a registered RHEL workstation and push to the builder namespace:

```bash
podman build --pull=always --no-cache \
  -f reson8/src/main/docker/Containerfile.gstreamer-base \
  -t localhost/reson8-base:local \
  reson8

REGISTRY="$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}')"
podman login -u "$(oc whoami)" -p "$(oc whoami -t)" "$REGISTRY"
podman tag localhost/reson8-base:local "$REGISTRY/reson8-build/reson8-base:latest"
podman push "$REGISTRY/reson8-build/reson8-base:latest"
```

Then redeploy the app so runtime images rebuild against the refreshed base. See [cluster-test flow](../../flows/cluster-test.md) or [quarkus-openshift-deploy flow](../../flows/quarkus-openshift-deploy.md).

## Refresh base in-cluster

```bash
oc start-build reson8-base -n reson8-build --wait
```
