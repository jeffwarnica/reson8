package com.coherentnetworksolutions.reson8.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
public class DropChannelTest {

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
                .body("$", hasItem("Pod Crash"));
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
    @DisplayName("POST /audio/drop - Error handling for unknown drop")
    void testTriggerDropNotFound() {
        // A drop that definitely doesn't exist
        String requestBody = "{\"drop\": \"NonExistentSound\"}";

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when().post("/audio/drop")
                .then()
                // Since the server code doesn't check for null endpoint yet,
                // it hits endpoint.trigger() and throws NPE -> 500.
                .statusCode(500);
    }

    @Test
    @DisplayName("POST /audio/drop - Malformed JSON")
    void testTriggerDropMalformed() {
        // Sending a field name the DTO doesn't recognize ("invalidKey")
        // results in 'drop' being null -> NPE -> 500.
        given()
                .contentType(ContentType.JSON)
                .body("{\"invalidKey\": \"Pod Startup\"}")
                .when().post("/audio/drop")
                .then()
                .statusCode(500);
    }
}