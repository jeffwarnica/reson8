package com.coherentnetworksolutions.reson8.k8s.client;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;
import com.coherentnetworksolutions.reson8.signal.K8sSyncState;
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

    @Inject
    K8sAuthTokenProvider authTokenProvider;

    @Inject
    SignalManager signalManager;

    @Inject
    K8sSyncState k8sSyncState;

    ThanosRestClient restClient;

    private String authToken;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean startupObserved = new AtomicBoolean(false);
    private final Map<String, String> activeQueries = new ConcurrentHashMap<>();

    private String thanosUrl;

    // 1. Wait for the K8s signal
    @ConsumeEvent("k8s-client-ready")
    @Blocking
    void thanosStartup(String msg) {
        startupObserved.set(true);
        initializePoller();
    }

    private void initializePoller() {
        thanosUrl = ThanosApiBaseUri.resolve(config);

        this.restClient = QuarkusRestClientBuilder.newBuilder()
            .baseUri(URI.create(thanosUrl))
            .trustAll(config.k8s().thanos().ignoreCerts()) // Map your config here
            .build(ThanosRestClient.class);

        refreshAuthToken();
        
        if (checkThanosHealth()) {
            activeQueries.clear();
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
            initialized.set(false);
            Log.error("Thanos connectivity isn't healthy. Metric polling won't start.");
        }
    }

    @Scheduled(every = "10s")
    void retryInitialization() {
        if (!startupObserved.get() || initialized.get() || !k8sSyncState.isEnabled()) {
            return;
        }
        Log.warn("Thanos poller is not initialized; retrying startup.");
        initializePoller();
    }

    @Scheduled(every = "2s")
    void pollMetrics() {
        if (!initialized.get() || !k8sSyncState.isEnabled()) {
            return;
        }
        Log.debugf("Polling Thanos for metrics...");
        activeQueries.forEach((signalId, promql) -> {
            try {
                ThanosResponse response = restClient.query(promql, authToken);
                double value = parseResponse(response);
                Log.debugf("Thanos response for signal [%s]: %f", signalId, value);
                signalManager.updateSignalIntensity(signalId, value);
            } catch (Exception e) {
                Log.error("Failed to pull metrics for signalId: " + signalId, e);
                if (isLikelyAuthFailure(e)) {
                    Log.warn("Refreshing Thanos auth token after query auth failure.");
                    refreshAuthToken();
                }
            }
        });
    }

    private double parseResponse(ThanosResponse response) {
        if (response.data().result().isEmpty()) return 0.0;
        // Prometheus returns ["timestamp", "value"] — value is at index 1
        List<Object> value = response.data().result().get(0).value();
        if (value == null || value.size() < 2) {
            Log.warnf("Unexpected Thanos value format: %s", value);
            return 0.0;
        }
        return Double.parseDouble(value.get(1).toString());
    }
    
    private boolean checkThanosHealth() {
        try {
            ThanosLabelResponse response = restClient.checkHealth(authToken);
            return "success".equals(response.status());
        } catch (Exception e) {
            Log.error("Thanos health check failed.", e);
            return false;
        }
    }

    private void refreshAuthToken() {
        String token = authTokenProvider.getToken();
        if (token == null || token.isBlank()) {
            authToken = null;
            Log.warn("K8sAuthTokenProvider returned an empty token.");
            return;
        }
        authToken = "Bearer " + token;
    }

    private boolean isLikelyAuthFailure(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lowered = message.toLowerCase();
        return lowered.contains("401") || lowered.contains("403") || lowered.contains("unauthorized")
            || lowered.contains("forbidden");
    }
}
