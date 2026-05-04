package com.coherentnetworksolutions.reson8.manager.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
    @DisplayName("security ConfigMapping: empty lists, default sentinel, login and dev-header flags")
    void security_defaults() {
        assertTrue(config.security().adminGroups().orElse(List.of()).isEmpty());
        assertTrue(config.security().viewerGroups().orElse(List.of()).isEmpty());
        assertTrue(config.security().streamGroups().orElse(List.of()).isEmpty());
        assertEquals("__anonymous__", config.security().anonymousStreamSentinel());
        assertFalse(config.security().loginAvailable());
        assertTrue(config.security().devTierHeaderEnabled());
    }
}
