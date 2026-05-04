package com.coherentnetworksolutions.reson8.k8s.client;

import java.net.URI;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import io.quarkus.logging.Log;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
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

    private ThanosRestClient restClient;

    @PostConstruct
    void init() {
        if (!config.k8s().thanos().readinessCheck()) {
            return;
        }
        String baseUri = ThanosApiBaseUri.resolve(config);
        restClient = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(baseUri))
                .trustAll(config.k8s().thanos().ignoreCerts())
                .build(ThanosRestClient.class);
    }

    @Override
    public HealthCheckResponse call() {
        if (!config.k8s().thanos().readinessCheck()) {
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .up()
                    .withData("readinessCheck", "disabled")
                    .build();
        }
        if (restClient == null) {
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .down()
                    .withData("reason", "rest client not initialized")
                    .build();
        }
        String raw = authTokenProvider.getToken();
        if (raw == null || raw.isBlank()) {
            Log.warn("Thanos readiness: no Kubernetes bearer token available.");
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .down()
                    .withData("reason", "no bearer token")
                    .build();
        }
        String bearer = "Bearer " + raw;
        try {
            ThanosMetricPoller.ThanosLabelResponse response = restClient.checkHealth(bearer);
            boolean ok = "success".equals(response.status());
            if (ok) {
                return HealthCheckResponse.builder()
                        .name("Thanos querier")
                        .up()
                        .withData("labels", "ok")
                        .build();
            }
            Log.warnf("Thanos readiness: labels endpoint status was [%s]", response.status());
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .down()
                    .withData("labelsStatus", response.status() != null ? response.status() : "null")
                    .build();
        } catch (Exception e) {
            Log.error("Thanos readiness check failed.", e);
            return HealthCheckResponse.builder()
                    .name("Thanos querier")
                    .down()
                    .build();
        }
    }
}
