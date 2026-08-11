# Image layout

```text
image-registry.../reson8-build/reson8-base:latest
  └── ubi10/openjdk-21:latest
      └── gstreamer1 + gstreamer1-plugins-base + gstreamer1-plugins-good + gstreamer1-plugins-bad-free

image-registry.../<runtime-namespace>/reson8:<maven-project-version>
  └── reson8-base:latest
      ├── /opt/reson8/sounds/   (WAV assets from app JAR during image build)
      ├── /deployments/config/  (ConfigMap mounted at pod start)
      └── /deployments/         (Quarkus fast-JAR layers)

image-registry.../<runtime-namespace>/disson8:<maven-project-version>
  └── UBI9 OpenJDK 21 S2I (does not consume reson8-base)
```

## Quay images (Evaluator / Operator)

Prebuilt images for Helm installs:

- `quay.io/<org>/reson8:<tag>`
- `quay.io/<org>/disson8:<tag>`
- Chart OCI: `oci://quay.io/<org>/reson8-helm:<chart-version>`

Image tags should match chart `appVersion` and `values.yaml` image tags for a given release.

## Developer in-cluster tags

Runtime image tags follow Maven project version from `pom.xml`, not `latest`. In `cluster-test`, the image group/namespace is `${BASE_NS}-dev-${USER}` for both `reson8` and `disson8`.
