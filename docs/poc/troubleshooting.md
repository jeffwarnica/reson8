# POC troubleshooting

Quick **symptom → likely cause → what to do** references. Full commands and context are in **[DEPLOY.md](../../DEPLOY.md)** (especially [Troubleshooting](../../DEPLOY.md#troubleshooting)).

| Symptom | Likely cause | What to do |
| --------- | ---------------- | ------------ |
| Pod stuck **NotReady** / readiness never UP | RBAC or `ServiceAccount` binding not applied; or Thanos reachability when the readiness check is enabled | `oc describe pod` for probe errors; verify RBAC and `oc auth can-i list nodes --as=system:serviceaccount:<target-namespace>:reson8`. For `cluster-test`, `<target-namespace>` is `${BASE_NS}-dev-$USER`. See [DEPLOY.md — Troubleshooting: Pod stuck in `NotReady`](../../DEPLOY.md#troubleshooting). |
| **`Forbidden`** errors to Thanos / monitoring in logs | `cluster-monitoring-view` binding missing | `oc get clusterrolebinding reson8-monitoring-view`. See [DEPLOY.md](../../DEPLOY.md#troubleshooting). |
| GStreamer **WARN** or plugin-not-found in logs | Stale or wrong **base** image | Rebuild and push the GStreamer base image, then redeploy. See [DEPLOY.md — Step 1b](../../DEPLOY.md#1b-build-the-gstreamer-base-image) and [DEPLOY.md — Troubleshooting](../../DEPLOY.md#troubleshooting). |
| **`WavCache` / file-not-found** for sounds | Sounds not present in the runtime image | Confirm files under `/opt/reson8/sounds/` (e.g. `oc debug` as in DEPLOY). See [DEPLOY.md](../../DEPLOY.md#troubleshooting). |
| **Config change** from `application.yml` not reflected | ConfigMap not updated or pod not restarted | Re-apply ConfigMap and `oc rollout restart deployment/reson8`. See [DEPLOY.md — Step 1e / CI note](../../DEPLOY.md#1e-create-the-application-configmap) and [Troubleshooting](../../DEPLOY.md#troubleshooting). |
| Image build or deploy fails | Registry auth, namespace, or base `FROM` image missing in cluster | Confirm internal registry route, `podman login`, and image pull secrets; follow [DEPLOY.md — Step 1b](../../DEPLOY.md#1b-build-the-gstreamer-base-image) and [Image layout](../../DEPLOY.md#image-layout). |

---

## Related docs

- **Install (happy path):** [install.md](install.md)
- **Full deploy guide:** [DEPLOY.md](../../DEPLOY.md)

Notes:

- The included soundscape/signal-map is tested with cluster-scoped access.
- More restrictive RBAC can work when paired with a narrower signal/query configuration.
