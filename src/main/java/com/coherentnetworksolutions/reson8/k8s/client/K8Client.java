package com.coherentnetworksolutions.reson8.k8s.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Produce;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;
import com.coherentnetworksolutions.reson8.signal.SignalManager;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.authentication.TokenRequest;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.WatcherException;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.Vertx;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
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
     * If the capacity is being recalculated as well pull usage, we could get very
     * weird percentages.
     * 
     * We want the denominator to be either the old or new capacity, not somewhere
     * between.
     * So, say, we go from 10-11 nodes, we will have 10 nodes of CPU, or 11 nodes of
     * CPU, but never sometimes 5 nodes of CPUs
     */
    private final AtomicReference<ClusterCapacity> clusterCapacity = new AtomicReference<>(new ClusterCapacity(1, 1));

    // TODO :Figure out how to run connectivity checks early, but in a non-blocking
    // way
    @PostConstruct
    public void startupValidation() {
        Log.info("Hello world");
        Log.info(client.getMasterUrl());
        Log.info("XXXXXXXXXXXXXXXXXXXXXXXXXXXxxx");
        // VertxContextSupport.subscribe(() -> {
        // try {
        // client.nodes().list();
        // Log.info("K8s API Connectivity: OK");
        // } catch (Exception e) {
        // Log.error("K8s API Connectivity: FAILED. Signal processing will be
        // disabled.");
        // }
        // });
    }

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
                        // Pass the signal to the processor logic
                        // handleK8sEvent(action, resource);
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
        // 1. Get the Capacity/Allocatable from the Core API
        List<Node> nodes = client.nodes().list().getItems();

        long totalCpuCapNano = 0;
        long totalMemCapacityBytes = 0;

        for (Node node : nodes) {
            Map<String, Quantity> allocatable = node.getStatus().getAllocatable();

            totalCpuCapNano += Quantity.getAmountInBytes(allocatable.get("cpu")).longValue();
            // Memory is usually in Ki, Mi, or Gi
            totalMemCapacityBytes += Quantity.getAmountInBytes(allocatable.get("memory")).longValue();
        }
        clusterCapacity.set(new ClusterCapacity(totalCpuCapNano, totalMemCapacityBytes));
        Log.debugf("Mem: [%.2f], CPU:[%.2f]", (float) clusterCapacity.get().memBytes, (float) clusterCapacity.get().cpuNano);
        gotCapacity = true;
    }

    /**
     * For now (at least), we will treat these as special case, and feed the well
     * known SignalBuckets
     */
    @Scheduled(every = "10s", concurrentExecution = ConcurrentExecution.SKIP)
    void pullMetrics() {

        if (!(isFlowing.get() && k8sSync.get()))
            return;

        Log.debugf("Scheduled pull");

        if (!gotCapacity) {
            pullCapacity();
        }

        ClusterCapacity currentCapacity = clusterCapacity.get();

        // 2. Get the current Usage from the Metrics API
        var nodeMetrics = client.top().nodes().metrics();

        long currentCpuUsageNano = 0;
        long currentMemUsageBytes = 0;
        // ... calculate usage as you did before ...

        for (NodeMetrics node : nodeMetrics.getItems()) {
            // "cpu" is returned in NanoCPUs (e.g., 500m = 500,000,000)
            Log.debug(node);
            Map<String, Quantity> usage = node.getUsage();

            currentCpuUsageNano += Quantity.getAmountInBytes(usage.get("cpu")).longValue();
            currentMemUsageBytes += Quantity.getAmountInBytes(usage.get("memory")).longValue();
        }

        // 3. Calculate Percentage for your SignalProcessor
        double cpuPercent = (double) currentCpuUsageNano / currentCapacity.cpuNano * 100;
        double memPercent = (double) currentMemUsageBytes / currentCapacity.memBytes * 100;

        Log.debugf("CAP:   CPU: [%.2f], MEM: [%.2f]", (float) currentCapacity.cpuNano,
                (float) currentCapacity.memBytes);
        Log.debugf("USAGE: CPU: [%.2f], MEM: [%.2f]", (float) currentCpuUsageNano, (float) currentMemUsageBytes);
        Log.debugf("%%:     CPU: [%.2f], MEM: [%.2f]", cpuPercent, memPercent);

        SignalBucket cpuBucket = signalManager.getSignalBucket("Cluster CPU Load");
        SignalBucket memBucket = signalManager.getSignalBucket("Cluster Memory Load");

        if (cpuBucket != null)
            cpuBucket.setIntensity(cpuPercent);
        if (memBucket != null)
            memBucket.setIntensity(memPercent);

    }

    public String getAuthToken() {
        String token = client.getConfiguration().getOauthToken();

        if (token == null || token.isEmpty()) {
            try {
                // Simplified TokenRequest API
                var tr = client.serviceAccounts()
                        .inNamespace(client.getNamespace())
                        .withName("default")
                        .tokenRequest(new TokenRequest()); // No DSL spec needed for defaults

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
