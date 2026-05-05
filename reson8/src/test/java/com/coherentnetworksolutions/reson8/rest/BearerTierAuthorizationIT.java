package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URL;
import java.util.Map;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.output.to.BrowserSessionManager;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.ClaimType;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;

/**
 * JWT-style tier checks with endpoint authorization enabled and dev-tier header disabled.
 * <p>
 * Production OIDC defaults require authentication ({@code quarkus.oidc.authentication.optional=false}) and use the
 * OpenShift OAuth discovery URL unless overridden; {@code %test} disables OIDC for this suite.
 * The JWT {@code groups} claim maps to roles via {@code quarkus.oidc.roles.role-claim-path=groups} (Quarkus OIDC extension configuration).
 * {@link TestSecurity} supplies equivalent roles without an IdP. {@link OidcSecurity} uses the supported Quarkus
 * {@code quarkus-test-security-oidc} annotations so tests exercise that published test path alongside tier guards.
 */
@QuarkusTest
@TestProfile(BearerTierAuthorizationIT.Profile.class)
class BearerTierAuthorizationIT {

    public static final class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("reson8.security.endpoint-authorization-enabled", "true"),
                    Map.entry("reson8.security.dev-tier-header-enabled", "false"),
                    Map.entry("reson8.security.admin-groups[0]", "reson8-admins"),
                    Map.entry("reson8.security.viewer-groups[0]", "reson8-viewers"),
                    Map.entry("reson8.security.stream-groups[0]", "reson8-streamers"),
                    Map.entry("reson8.security.stream-groups[1]", "__anonymous__"),
                    Map.entry("quarkus.oidc.enabled", "false"));
        }
    }

    @Inject
    BrowserSessionManager sessionManager;

    @TestHTTPResource("/audio/stream")
    URL audioStreamUrl;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(), "Native Gst must not load for bearer tier IT.");
    }

    @Test
    @DisplayName("@TestSecurity roles=admin group: GET control/state allowed")
    @TestSecurity(user = "alice", roles = {"reson8-admins"})
    void admin_readsControlState() {
        given().when().get("/audio/control/state").then().statusCode(200);
    }

    @Test
    @DisplayName("@TestSecurity roles=viewer group: read OK; mutation forbidden")
    @TestSecurity(user = "bob", roles = {"reson8-viewers"})
    void viewer_readOnly() {
        given().when().get("/audio/control/state").then().statusCode(200);
        given().when().post("/audio/control/k8s-sync/false").then().statusCode(403);
    }

    @Test
    @DisplayName("@TestSecurity roles=stream group: stream opens; control forbidden")
    @TestSecurity(user = "radio", roles = {"reson8-streamers"})
    void streamGroup_access() throws Exception {
        given().when().get("/audio/control/state").then().statusCode(403);
        StreamTestSupport.consumeOpeningChunk(audioStreamUrl, Map.of(), sessionManager);
    }

    @Test
    @DisplayName("anonymous: stream opens when sentinel configured; control forbidden")
    void anonymous_sentinelAllowsStreamOnly() throws Exception {
        StreamTestSupport.consumeOpeningChunk(audioStreamUrl, Map.of(), sessionManager);
        given().when().get("/audio/control/state").then().statusCode(403);
    }

    @Test
    @DisplayName("@OidcSecurity extra claims + roles: tier resolution unchanged")
    @TestSecurity(user = "jwt-user", roles = {"reson8-admins"})
    @OidcSecurity(
            claims = {
                @Claim(key = "azp", value = "reson8-client", type = ClaimType.STRING),
            })
    void oidcAugmentedIdentity_keepsAdminTier() {
        given().when().get("/audio/control/state").then().statusCode(200);
        given().when().get("/api/capabilities").then().statusCode(200).body("tier", is("ADMIN"));
    }
}
