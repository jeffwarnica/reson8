# Project TODOs

## Startup configuration validation

- **SoundDefinitionRegistry / soundscape integrity** — At startup, optionally validate that every `signal-map` input resolves to a registered sound definition (and/or that declared soundscape paths exist on disk). Implement as a `StartupConfigContributor` in `manager.config.startup` so it runs with the rest of aggregated config validation and logs consistently via `StartupConfigValidation`.

## Production readiness backlog

### P0 - Must do first

- [ ] **Platform/Security: baseline network policies** — Add `NetworkPolicy` defaults (default-deny plus explicit allow rules) so only required ingress/egress remains open (router ingress, kube API, Thanos, OAuth endpoints).
- [ ] **Reliability/Operations: default resource requests and limits** — Set non-empty CPU/memory `resources.requests` and `resources.limits` defaults for `reson8` and `disson8` in `helm/reson8/values.yaml`.
- [ ] **Security: harden pod/container security context** — Add baseline security context defaults in Helm templates (for example `runAsNonRoot`, `allowPrivilegeEscalation: false`, dropped capabilities, and `seccompProfile: RuntimeDefault`).
- [ ] **CI/Quality gate: add baseline pipeline** — Create CI checks for Maven test/verify, Helm lint/template render, and manifest sanity checks so PRs get pass/fail gates.
- [ ] **Configuration safety: split evaluator and production posture** — Separate demo/evaluator defaults from production defaults in Helm values/files to avoid accidental low-security or low-readiness settings in production installs.

### P1 - Next wave

- [ ] **Security/Auth: authorization edge-case tests** — Extend tier auth tests for method/path edge cases, malformed dev-tier cookie values, and expected `403` behavior on protected routes.
- [ ] **Release hygiene: drift guardrails** — Add automation to validate alignment across chart `version`, chart `appVersion`, image tags, and install/docs examples.
- [ ] **Deploy robustness: preflight checks** — Add deploy-time checks for required ConfigMap keys and projected CA files before rollout wait.
- [ ] **Docs/Runbook: production checklist** — Create a single operator-facing readiness checklist with binary gates (RBAC, CA trust setup, OIDC groups, resources, network policy, CI).

### P2 - Cleanup and maturity

- [ ] **Docs: tighten reson8 README positioning** — Replace stale prototype/template language with concise production, evaluator, and contributor paths that point to authoritative docs.
- [ ] **Architecture/Operations: single-replica guidance** — Document the one-replica operating model, safe restart/drain workflow, and scaling constraints clearly.
- [ ] **Observability: SLO-focused telemetry/runbook notes** — Document the minimal log/metric signals to confirm startup, readiness, auth flow health, and stream availability.
