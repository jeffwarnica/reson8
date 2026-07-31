# reson8

`reson8` is a Kubernetes auralizer: it turns cluster telemetry into continuous sound so operators can build ambient awareness of system state without constantly watching dashboards.

This repository is a **multi-module Maven project** with:

- `reson8` — the core audio engine and API
- `disson8` — a quick-and-dirty HTTP event generator for dev/demo and observability testing

## Why this repo has two apps

### `reson8` (main app)

`reson8` ingests Kubernetes/Prometheus signals and maps them into audio channels (wind, loops, drops, stochastic sounds), then streams mixed audio to browser clients.

This is the main application, providing .................telemetry-to-sound monitoring.

### `disson8` (dev/demo helper)

`disson8` is intentionally simple and noisy. It generates controllable HTTP traffic (`200/404/500`) so you can validate metrics, alerts, tracing, and dashboard behavior without involving the full audio stack.

Use it when you need synthetic load/events quickly.

## Repository layout

- `reson8/` — core Quarkus service and audio pipeline
- `disson8/` — standalone Quarkus traffic harness
- `deploy/openshift/` — deployment scripts and manifests
- `docs/poc/` — POC install and troubleshooting guides
- `DEPLOY.md` — full OpenShift deployment runbook
- `CONTRIBUTING.md` — developer workflows

## Quick start

Consult with `LudicrousSpeedStart.md` to get going at the speed of plaid. 
