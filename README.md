# reson8

`reson8` is a Kubernetes auralizer: it turns cluster telemetry into continuous sound so operators can build ambient awareness without constantly watching dashboards.

This repository is a **multi-module Maven project**:

- `reson8` — core audio engine and API
- `disson8` — HTTP event generator for dev, evaluation, and observability testing

Architecture and module detail: [reson8/README.md](reson8/README.md).

**In a hurry?** [LudicrousSpeedStart.md](LudicrousSpeedStart.md)

## Personas

| Persona | You want to | Artifact model | Start here |
| --- | --- | --- | --- |
| **Developer** | Change code, test locally or in a personal test namespace | Git clone + Maven + `deploy/run.sh` | [local-development](docs/flows/local-development.md) |
| **Evaluator** | Try the concept with stable prebuilt images | Helm OCI + Quay (no clone required) | [evaluator-install](docs/flows/evaluator-install.md) |
| **Operator** | Run production in a shared namespace | Helm OCI + Quay, production posture | [operator-install](docs/flows/operator-install.md) |

**Maintainers** (not a top-level persona): [releasing](docs/flows/releasing.md) — linked from [CONTRIBUTING.md](CONTRIBUTING.md).

## Flow catalog

### Developer

| Flow | Description |
| --- | --- |
| [local-development](docs/flows/local-development.md) | `quarkus:dev`, local tests |
| [local-container](docs/flows/local-container.md) | Podman base + runtime locally |
| [cluster-test](docs/flows/cluster-test.md) | In-cluster deploy to `reson8-dev-$USER` |
| [quarkus-openshift-deploy](docs/flows/quarkus-openshift-deploy.md) | Shared `reson8` namespace via Quarkus deploy |
| [ci-and-pr](docs/flows/ci-and-pr.md) | CI gates before opening a PR |

### Evaluator

| Flow | Description |
| --- | --- |
| [evaluator-install](docs/flows/evaluator-install.md) | Helm + Quay, 15-minute concept test |

### Operator

| Flow | Description |
| --- | --- |
| [operator-install](docs/flows/operator-install.md) | Helm OCI production install |

## Prerequisites (all cluster installs)

One-time platform setup — not persona-specific:

- [Cluster CA setup](docs/reference/platform/cluster-ca-setup.md)
- [RBAC](docs/reference/platform/rbac.md)
- [Prerequisites and production gates](docs/reference/platform/prerequisites.md)

## Reference

Configuration, platform topics, and troubleshooting: [docs/reference/README.md](docs/reference/README.md)

## Repository layout

- `reson8/` — core Quarkus service
- `disson8/` — traffic harness
- `deploy/` — `run.sh`, publish scripts, OpenShift templates
- `helm/reson8/` — Helm chart
- `docs/flows/` — step-by-step procedures
- `docs/reference/` — topical reference
- `CONTRIBUTING.md` — contribution norms, IDE, releasing link
