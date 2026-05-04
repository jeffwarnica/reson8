package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.security.DevTierRequestFilter;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

@QuarkusTest
@TestProfile(CapabilitiesResourceStreamSentinelIT.StreamSentinelProfile.class)
class CapabilitiesResourceStreamSentinelIT {

    public static final class StreamSentinelProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("reson8.security.stream-groups[0]", "__anonymous__");
        }
    }

    @BeforeEach
    void assertNotNativeGst() {
        org.junit.jupiter.api.Assertions.assertFalse(
                Gst.isInitialized(),
                "Toolkit abstraction leak: native GStreamer must not initialise for REST tests.");
    }

    @Test
    @DisplayName("GET /api/capabilities: dev header anonymous -> STREAM when sentinel configured")
    void capabilities_devHeaderAnonymous_streamWhenSentinelListed() {
        given()
                .header(DevTierRequestFilter.X_RESON8_DEV_TIER, "anonymous")
                .when()
                .get("/api/capabilities")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("tier", is("STREAM"))
                .body("canStream", is(true))
                .body("canViewControlState", is(false));
    }
}
