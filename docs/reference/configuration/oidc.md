# OIDC bootstrap

OIDC defaults live on `reson8.oidc.*` and `reson8.openshift-oauth.*`, materialized into `quarkus.oidc.*` by `Reson8OidcBootstrapConfigSourceFactory` (ordinal 450).

## Enablement

- Bundled `application.properties` leaves unqualified `quarkus.oidc.enabled` unset; `%dev`/`%test` set `false`.
- Cluster pods should set `quarkus.oidc.enabled: true` in mounted `application.yaml`.
- If `quarkus.oidc.enabled` is set explicitly, the factory does not override it.
- If unset at runtime, `reson8.oidc.enabled` (default `true`) decides.

## OpenShift ServiceAccount OAuth client

No cluster-admin `OAuthClient` resource required. The `reson8` ServiceAccount carries:

```yaml
serviceaccounts.openshift.io/oauth-redirectreference.primary: >-
  {"kind":"OAuthRedirectReference","apiVersion":"v1","reference":{"kind":"Route","name":"reson8"}}
```

Declared in `reson8/src/main/kubernetes/openshift.yml` and applied by Quarkus deploy or Helm chart.

| Env var | Source |
| --- | --- |
| `OIDC_CLIENT_ID` | `system:serviceaccount:<namespace>:reson8` |
| `OIDC_CLIENT_SECRET` | `reson8-oauth-client-secret` Secret (SA token) |

If you rename the namespace, update `OIDC_CLIENT_ID` in `application.properties` before deploying.

## Application type and login

Default application type: **hybrid** (Bearer APIs + browser authorization-code via `GET /login`).

Redirect URIs: typically `https://<route-host>/q/oidc/*` — check OIDC startup logs for your Quarkus version.

`OidcHybridLoginAuthorizationSanitizer` strips stray `Authorization` headers on `GET /login` so browser login works behind proxies.

**403 on `/login`:** OIDC may be off (missing ConfigMap merge) or a junk `Authorization` header is present.

## Issuer URL resolution

1. `reson8.openshift-oauth.auth-server-url` if set (env: `OPENSHIFT_AUTH_ISSUER_URL`, `OIDC_AUTH_SERVER_URL`)
2. Else if `reson8.openshift-oauth.discovery-enabled` (default `true`): GET metadata URL (in-cluster API server default or override)
3. Else explicit `auth-server-url` required

TLS uses JVM default trust store after pod entrypoint `update-ca-trust`.

## Config keys

| Key | Purpose |
| --- | --- |
| `reson8.oidc.*` | Mirrors Quarkus OIDC defaults |
| `reson8.openshift-oauth.discovery-enabled` | Query OAuth metadata for issuer |
| `reson8.openshift-oauth.metadata-url` | Override metadata URL |
| `reson8.openshift-oauth.auth-server-url` | Explicit issuer |

JWT `groups` map to tiers via `AccessTierResolver`. See [security-tiers.md](security-tiers.md).

## CA trust

Router CA must be in OS trust store via [cluster-ca-setup.md](../platform/cluster-ca-setup.md). No `quarkus.oidc.tls.*` or custom SSLContext configuration is permitted.

## Quarkus deploy fragment caution

Do not use a bare `Deployment` fragment that only tweaks `spec.template` without a named container — Dekorate merge can drop `containers` (`Required value` error).
