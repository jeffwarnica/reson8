# Contributing to Reson8

Day-to-day developer norms and IDE setup. For procedures, see [docs/flows/](docs/flows/). For publishing releases, see [docs/flows/releasing.md](docs/flows/releasing.md).

## Development loops

Reson8 has two distinct image build loops:

1. Base image (`reson8-base` in `reson8-build`)
2. Application (`reson8` / `disson8` in runtime namespace)

See [cluster-test](docs/flows/cluster-test.md), [quarkus-openshift-deploy](docs/flows/quarkus-openshift-deploy.md), and [bootstrap-builder](docs/reference/platform/bootstrap-builder.md).

## Access and workflow matrix

| Access level | Typical capabilities | Recommended workflow |
| --- | --- | --- |
| Local workstation only | No cluster write access | [local-development](docs/flows/local-development.md) |
| Cluster stream/observe only | Read metrics/streams; no BuildConfig access | Local dev; coordinate deploy with namespace admin |
| Runtime namespace admin | Deploy app; may not control builder NS | `deploy/run.sh` `cluster-test` or `prod` |
| Builder namespace admin | BuildConfigs/ImageStreams | `oc start-build reson8-base -n reson8-build --wait` |
| Cluster admin | Bootstrap rights | [platform reference](docs/reference/platform/) + developer flows |

## Inner loop and CI

Fast iteration:

```bash
./mvnw -pl reson8 quarkus:dev
./mvnw -pl reson8 test
```

Before opening a PR, see [ci-and-pr](docs/flows/ci-and-pr.md).

## Cursor / VS Code (Remote-SSH)

Workspace IDE config: [`.vscode/`](.vscode/). Coverage uses Maven + `quarkus-jacoco`; the editor reads `reson8/target/site/jacoco/jacoco.xml`.

| Goal | How |
| --- | --- |
| Run tests + coverage | **Terminal → Run Task…** → `reson8: test + coverage`, or `./mvnw -pl reson8 -am test` |
| Run / debug single test | Testing view or gutter **Run Test** |
| Line coverage | [Coverage Gutters](https://marketplace.visualstudio.com/items?itemName=ryanluker.vscode-coverage-gutters) |
| HTML report | `reson8/target/site/jacoco/index.html` |

Remote-SSH: merge [`.vscode/cursor-client-settings.json`](.vscode/cursor-client-settings.json) into Cursor **User** settings.

### View HTML coverage report (SSH remote)

```bash
cd reson8/target/site/jacoco
python3 -m http.server 8765
```

Forward port 8765 in Cursor **Ports**; open http://127.0.0.1:8765/

## Developer flow index

| Flow | When |
| --- | --- |
| [local-development](docs/flows/local-development.md) | `quarkus:dev`, unit tests |
| [local-container](docs/flows/local-container.md) | Podman runtime without cluster |
| [cluster-test](docs/flows/cluster-test.md) | Personal in-cluster namespace |
| [quarkus-openshift-deploy](docs/flows/quarkus-openshift-deploy.md) | Shared `reson8` namespace |
| [ci-and-pr](docs/flows/ci-and-pr.md) | Pre-PR gates |

## Releasing (maintainers)

Publish images and chart to Quay: [docs/flows/releasing.md](docs/flows/releasing.md).

Scripts: `deploy/release-roundtrip.sh --help`, `deploy/publish-quay.sh --help`.

## Notes

- Runtime image `FROM`: `image-registry.../reson8-build/reson8-base:latest`
- Cluster-specific config: `deploy/openshift/runtime_config/application-cluster-overlay.yaml` (gitignored)
- Operators install via Helm only: [operator-install](docs/flows/operator-install.md)
