# RBAC

Reson8 observes cluster-wide events and metrics. The default signal-map expects cluster-scoped read access.

## Cluster-scoped resources

Apply once per cluster (requires cluster-admin):

```bash
oc apply -f deploy/rbac.yml
```

Verify:

```bash
oc get clusterrole reson8-cluster-observer
oc get clusterrolebinding reson8-cluster-observer reson8-monitoring-view
```

The `ClusterRole` grants read/watch on events, nodes, pods, and deployments cluster-wide, plus ServiceAccount token requests (for Thanos). `reson8-monitoring-view` binds `cluster-monitoring-view` for Thanos querier access.

## Namespace-specific ServiceAccounts

Checked-in `deploy/rbac.yml` binds `system:serviceaccount:reson8:reson8` for the shared `reson8` runtime namespace.

For `cluster-test` namespaces (`${BASE_NS}-dev-${USER}`), ensure ClusterRoleBinding subjects match the test namespace ServiceAccount if you use custom RBAC.

## Cross-namespace metric queries

`cluster-monitoring-view` covers basic Thanos API access. Cross-namespace `sum(rate(...))` queries may require additional `view` on source namespaces:

```bash
oc adm policy add-role-to-user view \
  system:serviceaccount:<namespace>:reson8 \
  -n <source-namespace>
```

## Helm installs

Set `rbac.clusterScoped.create=false` when your platform team pre-provisioned the ClusterRole and bindings. See [helm-values.md](../configuration/helm-values.md).

Less-privileged RBAC can work when paired with a narrower signal-map in `application.yaml`.
