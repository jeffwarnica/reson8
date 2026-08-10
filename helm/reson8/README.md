# reson8 Helm chart (OpenShift MVP)

This chart installs `reson8` and `disson8` for concept-first evaluation using
prebuilt images from Quay. It is intentionally minimal and OpenShift-oriented.

## Install

Default install is **evaluator** posture (Thanos readiness off, demo groups, DEBUG).
Not production-safe.

```bash
helm upgrade --install reson8-eval ./helm/reson8 \
  -n reson8-eval \
  --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=v0.1.1 \
  --set disson8.image.repository=quay.io/rhn_gps_jwarnica/disson8 \
  --set disson8.image.tag=v0.1.1
```

### Production posture (Helm)

Pass the production values overlay so the chart mounts `files/application-production.yaml`
(Thanos readiness on, explicit security keys, INFO logging). Adjust IdP group names in that
file (or via `appConfig.inlineYaml`) before real production use. The canonical Quarkus/OpenShift
deploy path remains `DEPLOY.md` with the cluster overlay ConfigMap.

```bash
helm upgrade --install reson8 ./helm/reson8 \
  -n reson8 \
  --create-namespace \
  -f ./helm/reson8/values-production.yaml \
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
- `appConfig.profile`: `evaluator` (default) or `production`; selects the bundled ConfigMap YAML.
- `appConfig.inlineYaml`: optional full override for mounted `application.yaml` (wins over profile).
- `networkPolicy.enabled`: baseline NetworkPolicies (default `true`); set `false` to skip.
- `resources` / `disson8.resources`: non-empty CPU/memory requests and limits.
- `podSecurityContext` / `securityContext` (and `disson8.*` mirrors): baseline hardening.

## Included resources

- ServiceAccount + OpenShift OAuth redirect annotation
- OAuth client token Secret
- App config ConfigMap (`application.yaml`)
- Trusted CA bundle injection ConfigMap
- Reson8 Deployment (CA projection + app config mount + probes + resources + securityContext)
- Reson8 Service + Route
- Reson8 NetworkPolicy (router ingress; DNS / kube API / Thanos / OAuth egress)
- Disson8 Deployment + Service + Route (enabled by default; resources + securityContext)
- Disson8 NetworkPolicy (router ingress; DNS egress only)
- Optional cluster-scoped RBAC

## Known constraints

- One-time cluster CA setup is still required.
- Default chart config is **evaluator** posture: OIDC on, Thanos readiness check off, demo
  groups, DEBUG logging. Use `-f values-production.yaml` for production app-config posture.
- Single-replica topology.
- NetworkPolicies require a CNI that enforces them (OpenShift SDN/OVN). Override
  `networkPolicy.*` namespace selectors if your cluster uses nonstandard labels.
  Cluster RBAC and CA trust remain separate prerequisites (see `DEPLOY.md`).

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
