package com.coherentnetworksolutions.reson8.manager.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class Reson8ConfigSecurityMappingTest {

    @Inject
    Reson8Config config;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak: native GStreamer must not initialise for config mapping tests.");
    }

    @Test
    @DisplayName("security ConfigMapping: bundled stream sentinel, openshift OAuth TLS defaults")
    void security_defaults() {
        assertTrue(config.security().adminGroups().orElse(List.of()).isEmpty());
        assertTrue(config.security().viewerGroups().orElse(List.of()).isEmpty());
        assertEquals(List.of("__anonymous__"), config.security().streamGroups().orElse(List.of()));
        assertEquals("__anonymous__", config.security().anonymousStreamSentinel());
        assertFalse(config.security().loginAvailable());
        assertTrue(config.security().devTierCookieEnabled());
        assertFalse(config.security().endpointAuthorizationEnabled());
        assertTrue(config.openshiftOauth().discoveryEnabled());
        assertTrue(config.openshiftOauth().metadataUrl().isEmpty());
        assertTrue(config.openshiftOauth().authServerUrl().isEmpty());
    }

    @Test
    @DisplayName("Thanos ConfigMapping: pod-off defaults use openshift-monitoring service DNS")
    void thanos_defaults() {
        String expectedBaseUrl = Optional.ofNullable(System.getenv("RESON8_K8S_THANOS_BASE_URL"))
                .filter(value -> !value.isBlank())
                .orElse("https://thanos-querier.openshift-monitoring.svc.cluster.local:9091/");
        assertEquals(
                expectedBaseUrl,
                config.k8s().thanos().baseUrl());
        assertEquals(
                "https://thanos-querier.openshift-monitoring.svc.cluster.local:9091/api/v1",
                config.k8s().thanos().inClusterUrl());
    }
}
