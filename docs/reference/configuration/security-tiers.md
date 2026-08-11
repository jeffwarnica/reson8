# Security tiers

Tune in mounted `application.yaml` (Quarkus deploy overlay or Helm chart ConfigMap).

## Keys

| Key | Purpose |
| --- | --- |
| `reson8.security.admin-groups` | IdP group names for full operator control |
| `reson8.security.viewer-groups` | Read + stream; no mutations |
| `reson8.security.stream-groups` | Stream-only tier; may include anonymous sentinel |
| `reson8.security.anonymous-stream-sentinel` | Default `__anonymous__` — enables anonymous stream-only when in `stream-groups` |
| `reson8.security.login-available` | Advertised to SPA via `GET /api/capabilities` |
| `reson8.security.dev-tier-cookie-enabled` | `reson8-dev-tier` cookie simulation — **false in production** |
| `reson8.security.endpoint-authorization-enabled` | Enforces tiers on control/drop/debug/stream endpoints |

## Tier precedence

When resolving a subject: **admin > viewer > stream**.

The same group must not appear in more than one list — the application fails fast at startup on overlap.

## Anonymous access

- **Viewer** tier requires JWT groups; anonymous callers never become viewers.
- **Stream-only** via `__anonymous__` sentinel when ingress allows tokenless `/audio/stream`.
- If ingress forces authentication on all paths, anonymous listeners never reach the app — exempt `/audio/stream` at the proxy or document SSO requirements.

## Reasonable defaults

- `cluster-admins` → admin tier
- `reson8-viewers` (example) → viewer tier
- `stream-groups` includes `__anonymous__` → listen-only without login when ingress permits

Adjust group names to match your IdP claims. See [oidc.md](oidc.md) for login flow.

## Profile differences

| Situation | Tier simulation |
| --- | --- |
| `quarkus:dev` | `reson8-dev-tier` cookie when dev toolbar on |
| Cluster / Helm production | `dev-tier-cookie-enabled` must be **false** |
