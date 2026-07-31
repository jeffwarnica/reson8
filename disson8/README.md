# disson8

`disson8` is a quick-and-dirty HTTP event generator for local and OpenShift testing.
It produces controllable 200/404/500 traffic so you can validate dashboards, alerting,
tracing, and ServiceMonitor scraping without touching the main `reson8` audio engine.

## What it provides

- Three test endpoints under `/api/test`:
  - `GET /api/test/success` -> `200 OK`
  - `GET /api/test/notfound` -> `404 Not Found`
  - `GET /api/test/error` -> `500 Internal Server Error`
- A simple web UI at `/` to generate traffic at configurable rate and duration.
- Prometheus metrics via Micrometer for HTTP request counts, status classes, and latency.

## Run locally

From the repository root:

```bash
./mvnw -pl disson8 quarkus:dev
```

Defaults:

- `disson8` dev port: `8081`
- `disson8` default HTTP port outside `%dev`: `8080`

When running, open:

- `http://localhost:8081/` for the traffic generator UI
- `http://localhost:8081/api/test/success`
- `http://localhost:8081/api/test/notfound`
- `http://localhost:8081/api/test/error`

## Build and test

```bash
./mvnw -pl disson8 test
./mvnw -pl disson8 package
```

## OpenShift usage

This module is configured for OpenShift image/build flow and emits route + Prometheus
ServiceMonitor metadata.

From repository root:

```bash
./mvnw -pl disson8 package -Dquarkus.openshift.deploy=true
```

Key defaults in `src/main/resources/application.properties`:

- `quarkus.container-image.group=stress-test`
- `quarkus.openshift.route.expose=true`
- `quarkus.openshift.prometheus.generate-service-monitor=true`

## Optional console dashboard

Apply the included OpenShift `ConsoleDashboard` to view HTTP traffic trends:

```bash
oc apply -f disson8/crs/dashboard.yaml
```

## Relationship to reson8

`disson8` is a sibling utility module under the same Maven parent. It has no runtime
dependency on the `reson8` audio pipeline and is intended as a standalone traffic harness.
