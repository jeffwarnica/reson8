Ludicrous speed start
=====================

Repo:
[https://github.com/jeffwarnica/reson8](https://github.com/jeffwarnica/reson8)

Use this as a command cheat-sheet. Full detail lives in `CONTRIBUTING.md` and `DEPLOY.md`.

Quick paths
-----------

1) External evaluator path (Helm + Quay, concept feedback)

```bash
helm upgrade --install reson8-eval ./helm/reson8 \
  -n reson8-eval \
  --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=v0.1.1 \
  --set disson8.image.repository=quay.io/rhn_gps_jwarnica/disson8 \
  --set disson8.image.tag=v0.1.1
```

See `docs/poc/helm-evaluator.md` for the full 15-minute evaluator flow.

1) Local only (no cluster writes)

```bash
./mvnw -pl reson8 quarkus:dev
```

1) Local HTTP event generator (disson8)

```bash
./mvnw -pl disson8 quarkus:dev
```

1) Refresh base image in-cluster (builder access)

```bash
oc start-build reson8-base -n reson8-build --wait
```

1) Full app deploy loop (runtime namespace)

```bash
# One-time: copy and edit cluster overlay with your group names
cp deploy/openshift/runtime_config/application-cluster-overlay.example.yaml \
   deploy/openshift/runtime_config/application-cluster-overlay.yaml

# Deploy (sync ConfigMap + build/deploy app)
CONFIRM_PROD_DEPLOY=1 ./deploy/run.sh prod
```

1) In-cluster test lane (per-user namespace)

```bash
./deploy/run.sh cluster-test
TEST_NS="${BASE_NS:-reson8}-dev-${USER}"
oc get route reson8 -n "${TEST_NS}"
```

1) Local from-scratch base fallback (RHSM/Satellite workstation)

```bash
podman build --pull=always --no-cache \
  -f reson8/src/main/docker/Containerfile.gstreamer-base \
  -t localhost/reson8-base:local \
  reson8
```

Notes
-----

- Base image is UBI 10 + GStreamer (`Containerfile.gstreamer-base`).
- Runtime image consumes `reson8-build/reson8-base:latest`.
- If in doubt: start at `CONTRIBUTING.md`, then use `DEPLOY.md` for cluster setup.
