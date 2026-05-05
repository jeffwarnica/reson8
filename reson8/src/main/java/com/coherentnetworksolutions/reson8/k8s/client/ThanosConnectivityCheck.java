package com.coherentnetworksolutions.reson8.k8s.client;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Readiness: verifies the Thanos querier Prometheus API responds to {@code GET /api/v1/labels}
 * with {@code status: success}, using the same bearer token as {@link ThanosMetricPoller}.
 */
@Readiness
@ApplicationScoped
public class ThanosConnectivityCheck implements HealthCheck {

    @Inject
    Reson8Config config;

    @Inject
    K8sAuthTokenProvider authTokenProvider;

    private ThanosPrometheusApi thanosApi;
    private String thanosUrl;

    @PostConstruct
    void init() {
        thanosUrl = ThanosApiBaseUri.resolve(config);
        if (!config.k8s().thanos().readinessCheck()) {
            Log.infof("Thanos readiness check disabled (reson8.k8s.thanos.readiness-check=false). URL would be: %s", thanosUrl);
            return;
        }
        Log.infof("Thanos readiness check enabled — target: %s", thanosUrl);
        thanosApi = ThanosPrometheusHttpClient.create(config);
    }

    @Override
    public HealthCheckResponse call() {
        if (!config.k8s().thanos().readinessCheck()) {
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .up()
                    .withData("readinessCheck", "disabled")
                    .withData("url", thanosUrl)
                    .build();
        }
        if (thanosApi == null) {
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .down()
                    .withData("url", thanosUrl)
                    .withData("reason", "Thanos HTTP client not initialized")
                    .build();
        }
        Log.debugf("Thanos readiness check — querying %s", thanosUrl);
        String raw = authTokenProvider.getToken();
        if (raw == null || raw.isBlank()) {
            Log.warn("Thanos readiness: no Kubernetes bearer token available.");
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .down()
                    .withData("url", thanosUrl)
                    .withData("reason", "no bearer token")
                    .build();
        }
        String bearer = "Bearer " + raw;
        try {
            ThanosMetricPoller.ThanosLabelResponse response = thanosApi.fetchLabels(bearer);
            boolean ok = "success".equals(response.status());
            if (ok) {
                return HealthCheckResponse.builder()
                        .name("Thanos querier")
                        .up()
                        .withData("url", thanosUrl)
                        .withData("labels", "ok")
                        .build();
            }
            Log.warnf("Thanos readiness: labels endpoint status was [%s]", response.status());
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .down()
                    .withData("url", thanosUrl)
                    .withData("labelsStatus", response.status() != null ? response.status() : "null")
                    .build();
        } catch (Exception e) {
            Log.errorf(e, "Thanos readiness check failed against %s", thanosUrl);
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .down()
                    .withData("url", thanosUrl)
                    .withData("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    .build();
        }
    }
}
