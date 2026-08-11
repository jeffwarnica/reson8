# Ludicrous speed start

Who are you? Run the matching command, then open the flow doc.

## Developer

Hack on code, run tests, deploy from this repo.

```bash
./mvnw -pl reson8 quarkus:dev
```

→ [docs/flows/local-development.md](docs/flows/local-development.md)

## Evaluator

Try prebuilt images — concept feedback in ~15 minutes.

```bash
helm install reson8-eval oci://quay.io/rhn_gps_jwarnica/reson8-helm \
  --version 0.1.1 -n reson8-eval --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 --set image.tag=v0.1.1 \
  --set disson8.image.repository=quay.io/rhn_gps_jwarnica/disson8 --set disson8.image.tag=v0.1.1
```

→ [docs/flows/evaluator-install.md](docs/flows/evaluator-install.md)

## Operator

Production Helm install from Quay OCI.

```bash
helm upgrade --install reson8 oci://quay.io/rhn_gps_jwarnica/reson8-helm \
  --version 0.1.1 -n reson8 --create-namespace \
  -f helm/reson8/values-production.yaml \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 --set image.tag=v0.1.1 \
  --set disson8.image.repository=quay.io/rhn_gps_jwarnica/disson8 --set disson8.image.tag=v0.1.1
```

→ [docs/flows/operator-install.md](docs/flows/operator-install.md)

---

**Maintainers:** [docs/flows/releasing.md](docs/flows/releasing.md) (linked from [CONTRIBUTING.md](CONTRIBUTING.md))

**Reference:** [docs/reference/README.md](docs/reference/README.md)

**Full doc map:** [README.md](README.md)
