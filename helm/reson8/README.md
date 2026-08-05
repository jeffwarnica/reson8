# reson8 Helm chart (OpenShift MVP)

This chart installs `reson8` for concept-first evaluation using prebuilt images
from Quay. It is intentionally minimal and OpenShift-oriented.

## Install

```bash
helm upgrade --install reson8-eval ./helm/reson8 \
  -n reson8-eval \
  --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=latest
```

## Publish to Quay (OCI)

```bash
cd helm/reson8
helm package .
helm registry login quay.io -u 'your_username+your_username_robot' --password-stdin
helm push reson8-helm-0.1.0.tgz oci://quay.io/rhn_gps_jwarnica
```

Verify pullability:

```bash
helm pull oci://quay.io/rhn_gps_jwarnica/reson8-helm --version 0.1.0
```

Notes:

- The archive version (`reson8-0.1.0.tgz`) must match `version` in `Chart.yaml`.
- Helm auth is separate from Podman auth; `podman login` does not authenticate Helm OCI pulls.
- Pre-create `quay.io/rhn_gps_jwarnica/reson8-helm` and grant robot push/write access.

## Important values

- `image.repository`, `image.tag`: runtime image source.
- `rbac.clusterScoped.create`: set `false` if cluster RBAC is pre-provisioned.
- `appConfig.inlineYaml`: optional full override for mounted `application.yaml`.

## Included resources

- ServiceAccount + OpenShift OAuth redirect annotation
- OAuth client token Secret
- App config ConfigMap (`application.yaml`)
- Trusted CA bundle injection ConfigMap
- Deployment (CA projection + app config mount + probes)
- Service
- Route
- Optional cluster-scoped RBAC

## Known constraints

- One-time cluster CA setup is still required.
- Default chart config disables OIDC and Thanos readiness check for first-pass evaluation.
- Single-replica topology.
