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

    @BeforeEach
    void assertNotNativeGst() {
        org.junit.jupiter.api.Assertions.assertFalse(
                Gst.isInitialized(),
                "Toolkit abstraction leak: native GStreamer must not initialise for REST tests.");
    }

    @Test
    @DisplayName("GET /api/capabilities: default anonymous tier NONE with test security config")
    void capabilities_defaults() {
        given()
                .when()
                .get("/api/capabilities")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("tier", is("NONE"))
                .body("canStream", is(false))
                .body("showDevRoleSelector", is(true))
                .body("devUi", is(true))
                .body("loginAvailable", is(false));
    }

    @Test
    @DisplayName("GET /api/capabilities: dev header selects admin tier")
    void capabilities_devHeaderAdmin() {
        given()
                .header(DevTierRequestFilter.X_RESON8_DEV_TIER, "admin")
                .when()
                .get("/api/capabilities")
                .then()
                .statusCode(200)
                .body("tier", is("ADMIN"))
                .body("canMutate", is(true))
                .body("canViewControlState", is(true));
    }

    @Test
    @DisplayName("GET /api/capabilities: dev header anonymous -> NONE without sentinel in stream-groups")
    void capabilities_devHeaderAnonymous_noSentinelInConfig() {
        given()
                .header(DevTierRequestFilter.X_RESON8_DEV_TIER, "anonymous")
                .when()
                .get("/api/capabilities")
                .then()
                .statusCode(200)
                .body("tier", is("NONE"))
                .body("canStream", is(false));
    }
}
