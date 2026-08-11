# Releasing

**Audience:** Maintainer

Publish images and Helm chart to Quay. Linked from [CONTRIBUTING.md](../../CONTRIBUTING.md) — not a top-level persona.

**You need:**

- `podman`, Quay credentials (`QUAY_USERNAME`, `QUAY_PASSWORD`) unless already logged in
- RHEL subscription when rebuilding `reson8-base`
- `helm` 3.8+ for chart OCI push

**Prerequisites:**

- Write access to Quay repositories
- Version alignment across Maven, chart, and image tags

## Version bump (manual)

Before a release, align these files to the same version (example `0.1.1` / `v0.1.1`):

1. Root `pom.xml` `<version>` (non-`SNAPSHOT` for release lane)
2. `helm/reson8/Chart.yaml` — `version` and `appVersion`
3. `helm/reson8/values.yaml` — `image.tag` and `disson8.image.tag`
4. Commit; tag in git if your process requires it (not automated by scripts)

## Happy path — release lane

```bash
deploy/release-roundtrip.sh release --app-tag v0.1.1 --skip-base --push-chart
```

Validates Maven/chart consistency, publishes images via `publish-quay.sh`, packages and pushes chart to `oci://quay.io/rhn_gps_jwarnica`.

## Useful variants

- **Snapshot (mutable tags):** `deploy/release-roundtrip.sh snapshot --app-tag latest --skip-base --also-latest`
- **Chart only:** `deploy/release-roundtrip.sh chart-release --push-chart --chart-version 0.1.1`
- **Lower-level image publish:** `deploy/publish-quay.sh --tag v0.1.1 --skip-base`
- **Dry run:** add `--dry-run` to either script

Run `deploy/release-roundtrip.sh --help` and `deploy/publish-quay.sh --help` for flags.

## Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Strict mode failures | Version drift between pom/Chart/values | Align files listed above |
| Quay READ_ONLY | Registry maintenance | Retry when writable |
| Release mode rejects SNAPSHOT | Maven still SNAPSHOT | Bump pom to release version |

## Reference

- [image-layout](../reference/platform/image-layout.md)
- [helm-values](../reference/configuration/helm-values.md)
- Evaluator install after publish: [evaluator-install](evaluator-install.md)
