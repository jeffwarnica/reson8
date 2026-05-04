package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class HomeResourceTest {

    @BeforeEach
    void assertNotNativeGst() {
        org.junit.jupiter.api.Assertions.assertFalse(
                Gst.isInitialized(),
                "Toolkit abstraction leak: native GStreamer must not initialise for REST tests.");
    }

    @Test
    @DisplayName("GET / HTML includes dev toolbar when dev-tier header is enabled (%test)")
    void home_includesDevToolbarMarker() {
        given()
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .contentType("text/html;charset=UTF-8")
                .body(containsString("data-dev-toolbar"))
                .body(containsString("id=\"reson8-dev-toolbar\""));
    }

    @Test
    @DisplayName("GET / HTML includes SPA mount assets")
    void home_includesSpaAssets() {
        given()
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .body(containsString("/reson8-spa.js"))
                .body(containsString("/reson8-spa.css"));
    }
}
