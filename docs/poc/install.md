# POC install (happy path)

This page is a short **proof-of-concept** path for operators who already have an OpenShift cluster and the `oc` CLI. For **namespace creation, GStreamer base image build/push, RBAC, ConfigMap generation, and full deploy mechanics**, use the authoritative guide:

**→ [DEPLOY.md](../../DEPLOY.md)**

The steps below are the order of operations only; detailed commands and verification live in DEPLOY.

---

## Prerequisites (summary)

| You need | Detail |
|----------|--------|
| `oc` | Logged in to the target cluster |
| Rights | Project-admin on the `reson8` namespace; cluster-admin for one-time bootstrap |
| Build tooling | Java 21 and `./mvnw` from this repo for the app image build |

See [DEPLOY.md — Prerequisites](../../DEPLOY.md#prerequisites) for the full table.

---

## Order of operations

1. **Bootstrap once (cluster-admin)** — Namespace, build and push the GStreamer **base** image from a subscribed host, apply cluster RBAC, create the `reson8-config` ConfigMap from `application.yml`.  
   **→ [DEPLOY.md — Step 1](../../DEPLOY.md#step-1-bootstrap-one-time-cluster-admin)**

2. **Deploy the application (each release)** — Ensure the ConfigMap is current, then from the `reson8/` Maven module:  
   `./mvnw package -Dquarkus.openshift.deploy=true`  
   **→ [DEPLOY.md — Step 2](../../DEPLOY.md#step-2-deploy-developer-every-release)**

3. **Confirm rollout** — Wait for the deployment and readiness (Kubernetes API + Thanos checks as documented).  
   **→ [DEPLOY.md — Readiness gate](../../DEPLOY.md#readiness-gate)**

---

## First stream in the browser

After the route exists:

```bash
oc get route reson8 -n reson8
```

Open an HTTPS audio client (many browsers can play the stream directly) at:

```text
https://<route-host>/audio/stream
```

More detail: [DEPLOY.md — Checking the deployment](../../DEPLOY.md#checking-the-deployment).

---

## If something fails

Use [Troubleshooting](troubleshooting.md) for symptom → fix, with pointers back into DEPLOY.
