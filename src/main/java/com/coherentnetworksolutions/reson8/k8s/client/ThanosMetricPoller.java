package com.coherentnetworksolutions.reson8.k8s.client;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;
import com.coherentnetworksolutions.reson8.signal.SignalManager;

import io.quarkus.logging.Log;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ThanosMetricPoller {
    public record ThanosResponse(String status, ThanosData data) {}
    public record ThanosLabelResponse(String status, List<String> data) {}
    public record ThanosData(String resultType, List<ThanosResult> result) {}
    public record ThanosResult(Map<String, String> metric, List<Object> value) {}
 
    @Inject
    Reson8Config config;

    // @RestClient
    ThanosRestClient restClient; 

    @Inject
    K8Client k8sClient; // To access the SignalManager and update intensities

    @Inject
    SignalManager signalManager;

    private String authToken;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean k8sSync = new AtomicBoolean(true);
    private final Map<String, String> activeQueries = new ConcurrentHashMap<>();

    private String thanosUrl;

    // 1. Wait for the K8s signal
    @ConsumeEvent("K8sClientReady")
    @Blocking
    void thanosStartup(String msg) {
        if (new File("/var/run/secrets/kubernetes.io").exists()) {
            thanosUrl = "https://thanos-querier.openshift-monitoring.svc.cluster.local:9091/api/v1";
        } else {
            thanosUrl = config.k8s().thanos().baseUrl();
            if (!thanosUrl.endsWith("/api/v1")) {
                thanosUrl = thanosUrl.endsWith("/") ? thanosUrl + "api/v1" : thanosUrl + "/api/v1";
            }
        }

        this.restClient = QuarkusRestClientBuilder.newBuilder()
            .baseUri(URI.create(thanosUrl))
            .trustAll(config.k8s().thanos().ignoreCerts()) // Map your config here
            .build(ThanosRestClient.class);

        authToken = "Bearer " + k8sClient.getAuthToken(); 
        
        Log.debugf("my auth token is [%s]", authToken);

        if (checkThanosHealth()) {
            signalManager.getSignalBuckets().stream()
                .peek(bucket -> Log.debugf("My bucket [%s] is of type [%s] and has query [%s]", 
                    bucket.getName(), bucket.getSourceType(), bucket.getQuery()))
                .filter(b -> b.getSourceType() == SourceType.PROMETHEUS)
                .forEach(bucket -> {
                    Log.infof("Registering Thanos query for bucket [%s]: [%s]", bucket.getName(), bucket.getQuery());
                    activeQueries.put(bucket.getName(), bucket.getQuery());
                });
            initialized.set(true);
            Log.info("Thanos connectivity is healthy. Metric polling initialized.");
            Log.debugf("My activeQueries: [%s]", activeQueries);
        } else {
            Log.error("Thanos connectivity isn't healthy. Metric polling won't start.");
        }

        
    }   

    // NEW: Listener for the GUI toggle
    @ConsumeEvent(value = "k8s-sync-enable")
    public void setK8sSyncEnabled(boolean state) {
        Log.debugf("Thanos poller updating sync mode to [%s]", state);
        k8sSync.set(state);
    }


    @Scheduled(every = "2s")
    void pollMetrics() {
        if (!initialized.get() || !k8sSync.get()) {
            return;
        }
        Log.debugf("Polling Thanos for metrics...");
        activeQueries.forEach((signalId, promql) -> {
            try {
                ThanosResponse response = restClient.query(promql, authToken);
                double value = parseResponse(response);
                Log.debugf("Thanos response for signal [%s]: %f", signalId, value);
                signalManager.updateSignalIntensityFromPromVal(signalId, value);
            } catch (Exception e) {
                Log.error("Failed to pull metrics for signalId: " + signalId, e);
            }
        });
    }

    private double parseResponse(ThanosResponse response) {
        if (response.data().result().isEmpty()) return 0.0;
        // Prometheus returns ["timestamp", "value"] - value is index 1
        String val = response.data().result().get(0).value().get(1).toString();
        return Double.parseDouble(val);
    }
    
    // public void registerQuery(String id, String query) {
    //     activeQueries.put(id, query);
    // }
    
    private boolean checkThanosHealth() {
        try {
            ThanosLabelResponse response = restClient.checkHealth(authToken);
            return response.status().equals("success");
        } catch (Exception e) {
            Log.error("Thanos health check failed: " + e.getMessage());
            return false;
        }
    }
}
