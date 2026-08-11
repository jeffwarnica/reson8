# application.yml

Bundled at `reson8/src/main/resources/application.yml`. Mounted into pods at `/deployments/config/application.yaml` (higher priority than the JAR copy).

## Primary sections

| Section | Purpose |
| --- | --- |
| `reson8` | Audio path, security groups, k8s/Thanos settings |
| `signal-map` | Maps metric sources to named sounds and curve definitions |
| `soundscapes` | Sound definitions (paths, types: PROCEDURAL, LOOP, STOCHASTIC, DROP) |

Quarkus reads mounted `application.yaml` automatically when present.

## OpenShift deployment block

Deployment-specific Quarkus/OpenShift keys live in [application-properties.md](application-properties.md). The bundled `application.yml` intentionally does **not** set `quarkus.oidc.enabled` — cluster overlay or Helm chart supplies that.

## Security keys

See [security-tiers.md](security-tiers.md) for `reson8.security.*` keys tuned in mounted config.

## Local development

For `quarkus:dev`, use `%dev` profile overrides in `application.properties` or copy `reson8/application-local-DIST.properties` → `application-local.properties` (gitignored). Do not commit cluster Routes or secrets.

## Helm vs Quarkus deploy

| Install path | Config source |
| --- | --- |
| Helm | Chart ConfigMap from `appConfig.profile` or `appConfig.inlineYaml` — see [helm-values.md](helm-values.md) |
| Quarkus deploy | Deep-merge of `application.yml` + [cluster-overlay.md](cluster-overlay.md) |
