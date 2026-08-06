# reson8 Helm chart (OpenShift MVP)

This chart installs `reson8` and `disson8` for concept-first evaluation using
prebuilt images from Quay. It is intentionally minimal and OpenShift-oriented.

## Install

```bash
helm upgrade --install reson8-eval ./helm/reson8 \
  -n reson8-eval \
  --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=v0.1.1 \
  --set disson8.image.repository=quay.io/rhn_gps_jwarnica/disson8 \
  --set disson8.image.tag=v0.1.1
```

## Publish to Quay (OCI)

```bash
cd helm/reson8
helm package .
helm registry login quay.io -u 'your_username+your_username_robot' --password-stdin
helm push reson8-helm-0.1.1.tgz oci://quay.io/rhn_gps_jwarnica
```

Verify pullability:

```bash
helm pull oci://quay.io/rhn_gps_jwarnica/reson8-helm --version 0.1.1
```

Notes:

- The archive version (`reson8-helm-0.1.1.tgz`) must match `version` in `Chart.yaml`.
- Helm auth is separate from Podman auth; `podman login` does not authenticate Helm OCI pulls.
- Pre-create `quay.io/rhn_gps_jwarnica/reson8-helm` and grant robot push/write access.

## Important values

- `image.repository`, `image.tag`: reson8 runtime image source.
- `disson8.enabled`: include/exclude disson8 deployment resources.
- `disson8.image.repository`, `disson8.image.tag`: disson8 runtime image source.
- `rbac.clusterScoped.create`: set `false` if cluster RBAC is pre-provisioned.
- `appConfig.inlineYaml`: optional full override for mounted `application.yaml`.

## Included resources

- ServiceAccount + OpenShift OAuth redirect annotation
- OAuth client token Secret
- App config ConfigMap (`application.yaml`)
- Trusted CA bundle injection ConfigMap
- Reson8 Deployment (CA projection + app config mount + probes)
- Reson8 Service + Route
- Disson8 Deployment + Service + Route (enabled by default)
- Optional cluster-scoped RBAC

## Known constraints

- One-time cluster CA setup is still required.
- Default chart config enables OIDC and disables Thanos readiness check for first-pass evaluation.
- Single-replica topology.

## Maintainer release lane

Use `deploy/release-roundtrip.sh` as the canonical release helper for image + chart publish checks:

- `snapshot`: mutable/fast lane for shared testing.
- `release`: immutable app-tag lane with stricter consistency checks.
- `chart-release`: chart packaging and OCI push checks.

Examples from repo root:

```bash
deploy/release-roundtrip.sh snapshot --app-tag v0.1.1 --skip-base
deploy/release-roundtrip.sh release --app-tag v0.1.1 --skip-base --push-chart
deploy/release-roundtrip.sh chart-release --push-chart --chart-version 0.1.1
```

For lower-level image publishing details, see `deploy/publish-quay.sh`.
