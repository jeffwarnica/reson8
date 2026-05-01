package com.coherentnetworksolutions.reson8.k8s.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ClusterMetric;
import com.coherentnetworksolutions.reson8.signal.SignalManager;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.authentication.TokenRequest;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.Vertx;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class K8Client {
    public record ClusterCapacity(long cpuNano, long memBytes) {
    }

    @Inject
    KubernetesClient client;
    @Inject
    Mixer mixer;
    @Inject
    Vertx vertx;
    @Inject
    SignalManager signalManager;
    @Inject
    EventBus eventBus;

    private final AtomicBoolean isFlowing = new AtomicBoolean(false);
    private final AtomicBoolean k8sSync = new AtomicBoolean(true);
    private Watch k8sEventWatch;

    private volatile boolean gotCapacity = false;

    /*
     * Capacity of a cluster may change as nodes are added and removed, so we have
     * to update it every once in a while.
     *
     * We want the denominator to be either the old or new capacity, not somewhere
     * between. So, say, we go from 10-11 nodes, we will have 10 nodes of CPU, or
     * 11 nodes of CPU, but never sometimes 5 nodes of CPUs.
     */
    private final AtomicReference<ClusterCapacity> clusterCapacity = new AtomicReference<>(new ClusterCapacity(1, 1));

    @ConsumeEvent(value = "signalManager-ready")
    @Blocking
    void onSignalManagerReady(String msg) {
        if (mixer.isReady() && isFlowing.compareAndSet(false, true)) {
            pullCapacity();
            startK8sWatchers();
            eventBus.publish("K8sClientReady", null);
            Log.info("K8s Client is now LIVE and processing signals.");
        }
    }

    @ConsumeEvent(value = "k8s-sync-enable")
    @Blocking
    public void setK8sSyncEnabled(boolean state) {
        Log.debugf("Updating sync mode to [%s]", state);
        boolean previous = k8sSync.getAndSet(state);

        if (state && !previous) {
            startK8sWatchers();
        } else if (!state && previous) {
            stopK8sWatchers();
        }
    }

    private void startK8sWatchers() {
        if (k8sEventWatch != null) {
            Log.debug("k8sEventWatch already, er, watching. Returning");
            return;
        }
        Log.info("Opening Kubernetes Event Streams...");

        var list = client.events().v1().events().inAnyNamespace().list();
        String lastVersion = list.getMetadata().getResourceVersion();

        k8sEventWatch = client.events().v1().events().inAnyNamespace()
                .withResourceVersion(lastVersion)
                .watch(new Watcher<Event>() {
                    @Override
                    public void eventReceived(Action action, Event resource) {
                        signalManager.processEvent(resource);
                    }

                    @Override
                    public void onClose(WatcherException cause) {
                        Log.warn("K8s Watch closed. Attempting reconnect...");
                    }
                });
    }

    private void stopK8sWatchers() {
        if (k8sEventWatch != null) {
            Log.info("Closing Kubernetes Event Streams (Manual Override)");
            k8sEventWatch.close();
            k8sEventWatch = null;
        }
    }

    @Scheduled(every = "2m", concurrentExecution = ConcurrentExecution.SKIP)
    void pullCapacity() {
        if (!(isFlowing.get() && k8sSync.get()))
            return;

        Log.debugf("Refreshing cluster capacity");
        List<Node> nodes = client.nodes().list().getItems();

        long totalCpuCapNano = 0;
        long totalMemCapacityBytes = 0;

        for (Node node : nodes) {
            Map<String, Quantity> allocatable = node.getStatus().getAllocatable();
            Quantity cpu = allocatable.get("cpu");
            Quantity mem = allocatable.get("memory");
            if (cpu != null) totalCpuCapNano += Quantity.getAmountInBytes(cpu).longValue();
            if (mem != null) totalMemCapacityBytes += Quantity.getAmountInBytes(mem).longValue();
        }
        clusterCapacity.set(new ClusterCapacity(totalCpuCapNano, totalMemCapacityBytes));
        Log.debugf("Mem: [%.2f], CPU:[%.2f]", (float) clusterCapacity.get().memBytes, (float) clusterCapacity.get().cpuNano);
        gotCapacity = true;
    }

    @Scheduled(every = "10s", concurrentExecution = ConcurrentExecution.SKIP)
    void pullMetrics() {
        if (!(isFlowing.get() && k8sSync.get()))
            return;

        Log.debugf("Scheduled pull");

        if (!gotCapacity) {
            pullCapacity();
        }

        pushStatsBucket(ClusterMetric.CPU, computeCpuPercent());
        pushStatsBucket(ClusterMetric.MEMORY, computeMemPercent());
        pushStatsBucket(ClusterMetric.PENDING_PODS, (double) countPendingPods());
        pushStatsBucket(ClusterMetric.NODE_READINESS, computeNodeReadinessPct());
        pushStatsBucket(ClusterMetric.DEPLOYMENT_HEALTH, computeDeploymentHealthPct());
    }

    private double computeCpuPercent() {
        ClusterCapacity currentCapacity = clusterCapacity.get();
        var nodeMetrics = client.top().nodes().metrics();

        long currentCpuUsageNano = 0;
        long currentMemUsageBytes = 0;

        for (NodeMetrics node : nodeMetrics.getItems()) {
            Map<String, Quantity> usage = node.getUsage();
            Quantity cpu = usage.get("cpu");
            Quantity mem = usage.get("memory");
            if (cpu != null) currentCpuUsageNano += Quantity.getAmountInBytes(cpu).longValue();
            if (mem != null) currentMemUsageBytes += Quantity.getAmountInBytes(mem).longValue();
        }

        double cpuPercent = (double) currentCpuUsageNano / currentCapacity.cpuNano * 100;
        double memPercent = (double) currentMemUsageBytes / currentCapacity.memBytes * 100;

        Log.debugf("CAP:   CPU: [%.2f], MEM: [%.2f]", (float) currentCapacity.cpuNano, (float) currentCapacity.memBytes);
        Log.debugf("USAGE: CPU: [%.2f], MEM: [%.2f]", (float) currentCpuUsageNano, (float) currentMemUsageBytes);
        Log.debugf("%%:     CPU: [%.2f], MEM: [%.2f]", cpuPercent, memPercent);

        // Cache mem for computeMemPercent so we don't call the Metrics API twice per tick.
        lastMemPercent = memPercent;
        return cpuPercent;
    }

    private volatile double lastMemPercent = 0.0;

    private double computeMemPercent() {
        return lastMemPercent;
    }

    private int countPendingPods() {
        return client.pods().inAnyNamespace()
                .withField("status.phase", "Pending")
                .list()
                .getItems()
                .size();
    }

    private double computeNodeReadinessPct() {
        List<Node> nodes = client.nodes().list().getItems();
        if (nodes.isEmpty()) return 100.0;
        long readyCount = nodes.stream()
                .filter(n -> n.getStatus().getConditions().stream()
                        .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus())))
                .count();
        return (double) readyCount / nodes.size() * 100.0;
    }

    private double computeDeploymentHealthPct() {
        var deployments = client.apps().deployments().inAnyNamespace().list().getItems();
        if (deployments.isEmpty()) return 100.0;
        long totalDesired = deployments.stream()
                .mapToLong(d -> d.getSpec().getReplicas() == null ? 0 : d.getSpec().getReplicas())
                .sum();
        if (totalDesired == 0) return 100.0;
        long totalAvailable = deployments.stream()
                .mapToLong(d -> d.getStatus().getAvailableReplicas() == null ? 0 : d.getStatus().getAvailableReplicas())
                .sum();
        return (double) totalAvailable / totalDesired * 100.0;
    }

    private void pushStatsBucket(ClusterMetric metric, double rawValue) {
        signalManager.getBucketByMetric(metric).ifPresentOrElse(
            bucket -> signalManager.updateSignalIntensityFromRaw(bucket.getName(), rawValue),
            () -> Log.tracef("No bucket configured for ClusterMetric [%s], skipping", metric)
        );
    }

    public String getAuthToken() {
        String token = client.getConfiguration().getOauthToken();

        if (token == null || token.isEmpty()) {
            try {
                var tr = client.serviceAccounts()
                        .inNamespace(client.getNamespace())
                        .withName("reson8")
                        .tokenRequest(new TokenRequest());
                token = tr.getStatus().getToken();
                Log.info("Exchanged certs for default ServiceAccount token.");
            } catch (Exception e) {
                Log.error("Failed to request token: " + e.getMessage());
            }
        }
        return token;
    }

    @PreDestroy
    void shutdown() {
        stopK8sWatchers();
    }

}
