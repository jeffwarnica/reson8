# Reson8 evaluator install (Helm + Quay)

This path is for concept feedback, not contributor/developer workflow quality.
It assumes prebuilt images are already published to Quay.

If you want the full maintainer/operator deployment workflow, use [DEPLOY.md](../../DEPLOY.md).

---

## 15-minute challenge

1. Install the chart.
2. Open the Route stream.
3. Listen for 5+ minutes while generating normal cluster activity.
4. Send feedback on signal quality, noise clarity, and operator usefulness.

Suggested ask:

> Take 15 minutes and run this exactly as written.  
> Tell me if it worked, where it was noisy/confusing, and whether it helped build cluster intuition.

---

## Prerequisites

- OpenShift cluster with `oc` access
- `helm` 3.x (3.8+ recommended for OCI chart pulls)
- Permission to create namespace-scoped resources in your target namespace
- Permission to create cluster-scoped RBAC if you keep `rbac.clusterScoped.create=true`
- Pull access to:
  - `quay.io/rhn_gps_jwarnica/reson8:<tag>`
  - `quay.io/rhn_gps_jwarnica/reson8-helm:<chart-version>`
- Quay prep for chart publishing:
  - Create repository `rhn_gps_jwarnica/reson8-helm` (or ensure it already exists).
  - Grant robot `rhn_gps_jwarnica+jwarnica_robot` push/write permission on that repository.

One-time cluster-admin prerequisite still applies:

- Router ingress CA must be configured so the namespace `reson8-trusted-ca-bundle`
  ConfigMap receives `ca-bundle.crt` injection.
- See [DEPLOY.md — Cluster CA Setup](../../DEPLOY.md#cluster-ca-setup-one-time-cluster-admin).

---

## Install

Without cloning this Git repository (OCI chart from Quay). Helm and Podman keep separate auth stores, so run Helm login explicitly.

```bash
helm registry login quay.io -u 'your_username+your_username_robot' --password-stdin
helm install reson8-eval oci://quay.io/rhn_gps_jwarnica/reson8-helm \
  --version 0.1.0 \
  -n reson8-eval \
  --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=latest
```

If your platform team pre-provisioned cluster RBAC:

```bash
helm install reson8-eval oci://quay.io/rhn_gps_jwarnica/reson8-helm \
  --version 0.1.0 \
  -n reson8-eval \
  --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=latest \
  --set rbac.clusterScoped.create=false
```

From a local clone of this repository:

```bash
helm upgrade --install reson8-eval ./helm/reson8 \
  -n reson8-eval \
  --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=latest
```

If your platform team pre-provisioned cluster RBAC, disable chart-managed cluster RBAC:

```bash
helm upgrade --install reson8-eval ./helm/reson8 \
  -n reson8-eval \
  --create-namespace \
  --set image.repository=quay.io/rhn_gps_jwarnica/reson8 \
  --set image.tag=latest \
  --set rbac.clusterScoped.create=false
```

## Publish chart to Quay (OCI)

From a local clone of this repository:

```bash
cd helm/reson8
helm package .
helm registry login quay.io -u 'your_username+your_username_robot' --password-stdin
helm push reson8-helm-0.1.0.tgz oci://quay.io/rhn_gps_jwarnica
```

Notes:

- `0.1.0` must match the chart `version` in `helm/reson8/Chart.yaml`.
- `reson8-helm-0.1.0.tgz` comes from chart `name: reson8-helm` and `version: 0.1.0`.
- Pre-create `quay.io/rhn_gps_jwarnica/reson8-helm` and grant robot push/write access.
- Verify pullability before sharing:

```bash
helm pull oci://quay.io/rhn_gps_jwarnica/reson8-helm --version 0.1.0
```

---

## Verify

```bash
oc rollout status deployment/reson8-eval-reson8 -n reson8-eval
oc get route reson8-eval-reson8 -n reson8-eval
```

Then open:

```text
https://<route-host>/audio/stream
```

---

## Feedback prompts

- Did the stream start without manual fixes?
- Did cluster events feel distinguishable over background sound?
- Did you find yourself inferring cluster state without dashboards?
- What made trust lower: setup friction, audio semantics, or false/noisy cues?

---

## Known constraints (current MVP)

- Single-replica runtime shape.
- CA trust setup is mandatory; missing ingress CA causes hard startup failure.
- Default chart config disables OIDC and Thanos readiness check for faster concept evaluation.
- For production posture and full security/tiering, use the full workflow in [DEPLOY.md](../../DEPLOY.md).
