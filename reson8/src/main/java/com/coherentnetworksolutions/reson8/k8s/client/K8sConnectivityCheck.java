package com.coherentnetworksolutions.reson8.k8s.client;


import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Readiness
@ApplicationScoped
public class K8sConnectivityCheck implements HealthCheck {
    @Inject
    KubernetesClient client;

    @Override
    public HealthCheckResponse call() {
        try {
            client.nodes().list(); // Simple ping
            return HealthCheckResponse.up("Kubernetes Connectivity");
        } catch (Exception e) {
            Log.error("Kubernetes connectivity health check failed.", e);
            return HealthCheckResponse.down("Kubernetes Connectivity");
        }
    }
}