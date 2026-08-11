# CI and pull requests

**Audience:** Developer

**You need:**

- Java 21, `./mvnw`
- Native GStreamer for `GsTestProfile` integration tests

**Prerequisites:**

- None

## Happy path

1. Match CI Maven gate locally:

```bash
./mvnw -B -pl reson8,disson8 -am test
```

2. Match Helm lint/template gate:

```bash
./deploy/ci-helm-check.sh
```

3. Open PR — GitHub Actions runs [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) on PRs and pushes to `main`.

## Useful variants

- **Heavier check (optional):** `./mvnw -pl reson8 verify` (SpotBugs + PITest)
- **Single module:** `./mvnw -pl reson8 test`

## Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| GsTestProfile IT failures | GStreamer not installed | Install native GStreamer or check IT annotations |
| Helm check fails | Chart template drift | Fix chart or values; run `helm lint helm/reson8` |

## Reference

- IDE coverage workflow: [CONTRIBUTING.md](../../CONTRIBUTING.md#cursor--vs-code-remote-ssh)
- [helm-values](../reference/configuration/helm-values.md) — evaluator vs production template checks
