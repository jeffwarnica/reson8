package com.coherentnetworksolutions.reson8.k8s.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ThanosConnectivityCheckTest {

    @Inject
    @Readiness
    ThanosConnectivityCheck thanosConnectivityCheck;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "This test class must not require native GStreamer.");
    }

    @Test
    @DisplayName("Thanos readiness is UP when readiness-check is disabled (%test)")
    void readinessSkippedByConfig_returnsUp() {
        HealthCheckResponse response = thanosConnectivityCheck.call();
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }
}
