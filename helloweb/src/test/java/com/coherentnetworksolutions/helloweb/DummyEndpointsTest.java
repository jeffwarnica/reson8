package com.coherentnetworksolutions.helloweb;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Smoke test verifying the three {@link DummyEndpoints} return the correct HTTP response codes.
 */
@QuarkusTest
class DummyEndpointsTest {

    @Test
    @DisplayName("GET /api/test/success returns 200 OK")
    void success_returns200() {
        given()
            .when().get("/api/test/success")
            .then()
            .statusCode(200)
            .body(equalTo("Hello"));
    }

    @Test
    @DisplayName("GET /api/test/notfound returns 404 Not Found")
    void notFound_returns404() {
        given()
            .when().get("/api/test/notfound")
            .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("GET /api/test/error returns 500 Internal Server Error")
    void error_returns500() {
        given()
            .when().get("/api/test/error")
            .then()
            .statusCode(500);
    }
}
