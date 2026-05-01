package com.coherentnetworksolutions.reson8.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.freedesktop.gstreamer.Gst;

@QuarkusTest
public class DropChannelIT {


    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "This test class must not require native GStreamer. " +
                        "If a production class calls Caps.fromString() or ElementFactory.make() directly, " +
                        "that's a toolkit abstraction leak — route it through GsToolkit.");
    }

    @Test
    @DisplayName("GET /audio/drop - List registered drops")
    void testListDrops() {
        given()
                .when().get("/audio/drop")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                // Verify it returns a list containing your specific aliases
                .body("$", hasItem("Pod Startup"))
                .body("$", hasItem("Pod Killed"));
    }

    @Test
    @DisplayName("POST /audio/drop - Trigger successful drop")
    void testTriggerDropSuccess() {
        // We use the alias registered in your MappingManager
        String requestBody = "{\"drop\": \"Pod Startup\"}";

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when().post("/audio/drop")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("POST /audio/drop - Unknown drop name returns 404")
    void testTriggerDropNotFound() {
        String requestBody = "{\"drop\": \"NonExistentSound\"}";

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when().post("/audio/drop")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("POST /audio/drop - Missing drop field returns 400")
    void testTriggerDropMalformed() {
        // Sending a field name the DTO doesn't recognize results in drop=null -> 400.
        given()
                .contentType(ContentType.JSON)
                .body("{\"invalidKey\": \"Pod Startup\"}")
                .when().post("/audio/drop")
                .then()
                .statusCode(400);
    }
}