package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;

/**
 * {@code GET /q/info} is gated by {@code quarkus.http.auth.permission.info} (not the tier filter).
 * Default {@code %test} permits the path; this profile restores {@code authenticated} for the checks below.
 */
@QuarkusTest
@TestProfile(InfoEndpointAuthIT.Profile.class)
class InfoEndpointAuthIT {

    public static final class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.auth.permission.info.policy", "authenticated");
        }
    }

    @Test
    @DisplayName("anonymous GET /q/info forbidden when info policy is authenticated")
    void anonymous_forbidden() {
        given().when().get("/q/info").then().statusCode(401);
    }

    @Test
    @DisplayName("@TestSecurity identity: GET /q/info returns build metadata")
    @TestSecurity(user = "ops", roles = {"reson8-admins"})
    void authenticated_returnsBuildInfo() {
        given().when()
                .get("/q/info")
                .then()
                .statusCode(200)
                .body("build", notNullValue())
                .body("build.version", notNullValue());
    }
}
