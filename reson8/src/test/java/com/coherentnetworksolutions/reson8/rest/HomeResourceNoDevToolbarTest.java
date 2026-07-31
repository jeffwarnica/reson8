package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.util.Map;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Ensures dev-only markup is not present in HTML when dev-tier simulation is disabled.
 */
@QuarkusTest
@TestProfile(HomeResourceNoDevToolbarTest.Profile.class)
class HomeResourceNoDevToolbarTest {

    @BeforeEach
    void assertNotNativeGst() {
        org.junit.jupiter.api.Assertions.assertFalse(
                Gst.isInitialized(),
                "Toolkit abstraction leak: native GStreamer must not initialise for REST tests.");
    }

    @Test
    @DisplayName("GET / HTML omits dev toolbar when dev-tier simulation is disabled")
    void home_excludesDevToolbarMarker() {
        given()
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .body(not(containsString("data-dev-toolbar")));
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("reson8.security.dev-tier-cookie-enabled", "false");
        }
    }
}
