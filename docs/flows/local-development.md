# Local development

**Audience:** Developer

**You need:**

- Java 21, `./mvnw`
- Optional: copy `reson8/application-local-DIST.properties` → `application-local.properties` (gitignored) for cluster Routes or Thanos URL overrides

**Prerequisites:**

- None for basic dev mode

## Happy path

1. Start dev mode from repo root:

```bash
./mvnw -pl reson8 quarkus:dev
```

2. Run focused tests while iterating:

```bash
./mvnw -pl reson8 -Dtest=YourTestName test
```

3. For HTTP event generation without the audio stack, run [disson8](../../../disson8/README.md):

```bash
./mvnw -pl disson8 quarkus:dev
```

## Useful variants

- **Local overrides:** set `RESON8_K8S_THANOS_BASE_URL` in `application-local.properties` when testing against a cluster Thanos Route from your workstation.
- **Tier simulation:** `%dev` enables `reson8-dev-tier` cookie via the dev toolbar; OIDC is off by default — see [security-tiers](../reference/configuration/security-tiers.md).

## Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Tests need native GStreamer | IT uses `GsTestProfile` | Install GStreamer locally or run focused unit tests only |
| Thanos errors in dev | No in-cluster Thanos | Set `RESON8_K8S_THANOS_BASE_URL` or use silent/test profile |
| Secrets in git rejected | Local properties committed | Use gitignored `application-local.properties` only |

Full index: [troubleshooting](../reference/troubleshooting.md).

## Reference

- [application-properties](../reference/configuration/application-properties.md) — `%dev` profile
- IDE and coverage: [CONTRIBUTING.md](../../../CONTRIBUTING.md#cursor--vs-code-remote-ssh)
- Next step in-cluster: [cluster-test](cluster-test.md)
