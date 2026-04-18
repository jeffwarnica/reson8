package com.coherentnetworksolutions.reson8.k8s.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;
import com.coherentnetworksolutions.reson8.signal.SignalManager;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.events.v1.Event;
// import io.fabric8.kubernetes.api.model.Event;
// import io.fabric8.kubernetes.client.Action;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
// import io.fabric8.kubernetes.client.
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.WatcherException;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.quarkus.vertx.ConsumeEvent;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class k8sclient {
    @Inject KubernetesClient client;
    @Inject Mixer mixer;
    @Inject Vertx vertx;
    @Inject SignalManager signalManager;

    private final AtomicBoolean isWired = new AtomicBoolean(false);
    private boolean gotCapacity = false;

    /*
     * Capacity of a cluster may change as nodes are added and removed, so we have to update it every once in a while.
        
        If the capacity is being recalculated as well pull usage, we could get very weird percentages.

        We want the denominator to be either the old or new capacity, not somewhere between.
        So, say, we go from 10-11 nodes, we will have 10 nodes of CPU, or 11 nodes of CPU, but never sometimes 5 nodes of CPUs
     */
    private final AtomicReference<ClusterCapacity> clusterCapacity = 
        new AtomicReference<>(new ClusterCapacity(1, 1));

    // TODO :Figure out how to run connectivity checks early, but in a non-blocking way
    @PostConstruct
    public void  startupValidation() {
        Log.info("Hello world");
        Log.info(client.getMasterUrl());
        Log.info("XXXXXXXXXXXXXXXXXXXXXXXXXXXxxx");
        // VertxContextSupport.subscribe(() -> {
        //     try {
        //         client.nodes().list();
        //         Log.info("K8s API Connectivity: OK");
        //     } catch (Exception e) {
        //         Log.error("K8s API Connectivity: FAILED. Signal processing will be disabled.");
        //     }
        // });
    }

    @ConsumeEvent(value = "signalManager-ready", blocking=true)
    void onSignalManagerReady(String msg) {
        wireEverything();
    }
    
    private void wireEverything() {
        if (mixer.isReady() && isWired.compareAndSet(false, true)) {
            pullCapacity();
            startK8sWatchers();
        }
    }

    private void handleK8sEvent(Action action, Event resource){
        signalManager.getSignalBuckets().forEach(bucket -> bucket.processEvent(resource));
        
        Log.debugf("handling event [%s], [%s]", action, resource);
    }

    private void startK8sWatchers() {
        Log.info("Opening Kubernetes Event Streams...");
        
        var list = client.events().v1().events().inAnyNamespace().list();
        String lastVersion = list.getMetadata().getResourceVersion();
        
        client.events().v1().events().inAnyNamespace()
            .withResourceVersion(lastVersion)
            .watch( new Watcher<Event>() {
                @Override
                public void eventReceived(Action action, Event resource) {
                    // Pass the signal to the processor logic
                    handleK8sEvent(action, resource);
                }

                @Override
                public void onClose(WatcherException cause) {
                    Log.warn("K8s Watch closed. Attempting reconnect...");
                }
            }
        );
        
    }

    @Scheduled(every = "2m", concurrentExecution = ConcurrentExecution.SKIP)
    void pullCapacity() {
        if (isWired.get() == false)
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
        Log.debugf("Mem: [%.2f], CPU:[%.2f]", clusterCapacity.get().memBytes, clusterCapacity.get().cpuNano );
        gotCapacity = true;
    }
    /**
     * For now (at least), we will treat these as special case, and feed the well known SignalBuckets
     */
    @Scheduled(every = "10s", concurrentExecution = ConcurrentExecution.SKIP)
    void pullMetrics() {
        
        if (isWired.equals(false)) return;
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
        
        
        Log.debugf("CAP:   CPU: [%.2f], MEM: [%.2f]", (float) currentCapacity.cpuNano, (float) currentMemUsageBytes);
        Log.debugf("USAGE: CPU: [%.2f], MEM: [%.2f]", (float) currentCpuUsageNano, (float) currentMemUsageBytes);
        Log.debugf("%%:     CPU: [%.2f], MEM: [%.2f]", cpuPercent, memPercent);

        SignalBucket cpuBucket = signalManager.getSignalBucket("Cluster CPU Load");
        SignalBucket memBucket = signalManager.getSignalBucket("Cluster Memory Load");


        if (cpuBucket != null) cpuBucket.setIntensity(cpuPercent);
        if (memBucket != null) memBucket.setIntensity(memPercent);
        
    }

    public record ClusterCapacity(long cpuNano, long memBytes) {}

}
