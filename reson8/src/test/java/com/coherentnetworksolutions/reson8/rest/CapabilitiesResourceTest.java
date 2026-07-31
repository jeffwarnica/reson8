package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.security.DevTierRequestFilter;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class CapabilitiesResourceTest {
    private static final String COOKIE_HEADER = "Cookie";

    @BeforeEach
    void assertNotNativeGst() {
        org.junit.jupiter.api.Assertions.assertFalse(
                Gst.isInitialized(),
                "Toolkit abstraction leak: native GStreamer must not initialise for REST tests.");
    }

    @Test
    @DisplayName("GET /api/capabilities: bundled stream sentinel gives anonymous STREAM (login still off in %test)")
    void capabilities_defaults() {
        given()
                .when()
                .get("/api/capabilities")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("tier", is("STREAM"))
                .body("canStream", is(true))
                .body("showDevRoleSelector", is(true))
                .body("devUi", is(true))
                .body("loginAvailable", is(false));
    }

    @Test
    @DisplayName("GET /api/capabilities: dev cookie selects admin tier")
    void capabilities_devCookieAdmin() {
        given()
                .header(COOKIE_HEADER, DevTierRequestFilter.DEV_TIER_COOKIE + "=admin")
                .when()
                .get("/api/capabilities")
                .then()
                .statusCode(200)
                .body("tier", is("ADMIN"))
                .body("canMutate", is(true))
                .body("canViewControlState", is(true));
    }

    @Test
    @DisplayName("GET /api/capabilities: dev cookie anonymous -> STREAM when sentinel listed")
    void capabilities_devCookieAnonymous_streamTier() {
        given()
                .header(COOKIE_HEADER, DevTierRequestFilter.DEV_TIER_COOKIE + "=anonymous")
                .when()
                .get("/api/capabilities")
                .then()
                .statusCode(200)
                .body("tier", is("STREAM"))
                .body("canStream", is(true))
                .body("canViewControlState", is(false));
    }
}
