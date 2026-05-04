package com.coherentnetworksolutions.reson8.k8s.client;


import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.fabric8.kubernetes.api.model.NodeList;
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
            NodeList nodeList = client.nodes().list();
            int count = nodeList.getItems() != null ? nodeList.getItems().size() : 0;
            return HealthCheckResponse.builder()
                    .name("Kubernetes API")
                    .up()
                    .withData("nodesListed", Integer.toString(count))
                    .build();
        } catch (Exception e) {
            Log.error("Kubernetes connectivity health check failed.", e);
            return HealthCheckResponse.builder()
                    .name("Kubernetes API")
                    .down()
                    .build();
        }
    }
}