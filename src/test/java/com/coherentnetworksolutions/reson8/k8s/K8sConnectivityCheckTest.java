package com.coherentnetworksolutions.reson8.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Listable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesServer;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.k8s.client.K8sConnectivityCheck;

@QuarkusTest
@WithKubernetesTestServer
class K8sConnectivityCheckTest {

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "This test class must not require native GStreamer. " +
                        "If a production class calls Caps.fromString() or ElementFactory.make() directly, " +
                        "that's a toolkit abstraction leak — route it through GsToolkit.");
    }

    @Inject
    @Readiness
    K8sConnectivityCheck connectivityCheck;

    @KubernetesTestServer
    KubernetesServer mockServer;

    @Test
    @DisplayName("Kubernetes Connectivity health check returns UP when the client can list nodes")
    void testHealthOKCheckUp() {
        mockServer.expect().withPath("/api/v1/nodes").andReturn(200, new NodeList()).always();
        
        HealthCheckResponse response = connectivityCheck.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus(), "Expected Kubernetes Connectivity health check to report UP");
    }

    @Test
    @DisplayName("Kubernetes Connectivity health check returns DOWN when the client throws an exception")
    void testHealthCheckDown() {
        mockServer.expect().withPath("/api/v1/nodes").andReturn(500, Object.class).always();
        
        connectivityCheck = new K8sConnectivityCheck();

        HealthCheckResponse response = connectivityCheck.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus(), "Expected Kubernetes Connectivity health check to report DOWN");
    }
}
