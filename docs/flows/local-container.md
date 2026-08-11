# Local container run

**Audience:** Developer

**You need:**

- `podman`
- RHSM/Satellite registration (for UBI GStreamer packages in base image)

**Prerequisites:**

- None for fully local run (no cluster)

## Happy path

1. Build local base image:

```bash
podman build --pull=always --no-cache \
  -f reson8/src/main/docker/Containerfile.gstreamer-base \
  -t localhost/reson8-base:local \
  reson8
```

2. Package the app:

```bash
./mvnw -pl reson8 -DskipTests package
```

3. Build runtime image:

```bash
podman build \
  --from localhost/reson8-base:local \
  -f reson8/src/main/docker/Dockerfile.jvm \
  -t localhost/reson8-jvm-local \
  reson8
```

4. Run:

```bash
podman run --rm -p 8090:8090 \
  -e JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Dquarkus.oidc.enabled=false" \
  localhost/reson8-jvm-local
```

Open `http://localhost:8090/` (OIDC disabled).

## Useful variants

- Closest local equivalent to in-cluster runtime image path (GStreamer + fast-JAR layout).
- For faster iteration, prefer [local-development](local-development.md) (`quarkus:dev`).

## Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| UBI package errors during base build | Host not subscribed to RHSM | Register workstation or use in-cluster base — [bootstrap-builder](../reference/platform/bootstrap-builder.md) |
| GStreamer errors at runtime | Wrong base image | Rebuild base with `--no-cache` |

Full index: [troubleshooting](../reference/troubleshooting.md).

## Reference

- [image-layout](../reference/platform/image-layout.md)
- In-cluster deploy: [cluster-test](cluster-test.md)
