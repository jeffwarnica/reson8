package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;    
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.output.to.BrowserSessionManager;
import com.coherentnetworksolutions.reson8.security.DevTierRequestFilter;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * Tier guards with {@link DevTierRequestFilter} simulation and {@code reson8.security.endpoint-authorization-enabled=true}.
 */
@QuarkusTest
@TestProfile(TieredEndpointAuthorizationIT.Profile.class)
class TieredEndpointAuthorizationIT {
    private static final String COOKIE_HEADER = "Cookie";

    public static final class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "reson8.security.endpoint-authorization-enabled", "true",
                    "reson8.security.stream-groups[0]", "__anonymous__");
        }
    }

    @Inject
    BrowserSessionManager sessionManager;

    @TestHTTPResource("/audio/stream")
    URL audioStreamUrl;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(), "Native Gst must not load for tier REST IT.");
    }

    @Test
    @DisplayName("admin: GET control/state allowed")
    void admin_canReadControlState() {
        given().header(DevTierRequestFilter.X_RESON8_DEV_TIER, "admin")
                .when()
                .get("/audio/control/state")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("viewer: GET control/state allowed; POST mutation forbidden")
    void viewer_readOnlyControl() {
        given().header(DevTierRequestFilter.X_RESON8_DEV_TIER, "viewer")
                .when()
                .get("/audio/control/state")
                .then()
                .statusCode(200);

        given().header(DevTierRequestFilter.X_RESON8_DEV_TIER, "viewer")
                .when()
                .post("/audio/control/k8s-sync/false")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("stream: control forbidden; stream opens when tier allows")
    void stream_onlyStreamPath() throws Exception {
        given().header(DevTierRequestFilter.X_RESON8_DEV_TIER, "stream")
                .when()
                .get("/audio/control/state")
                .then()
                .statusCode(403);

        StreamTestSupport.consumeOpeningChunk(
                audioStreamUrl,
                Map.of(COOKIE_HEADER, DevTierRequestFilter.DEV_TIER_COOKIE + "=stream"),
                sessionManager);
    }

    @Test
    @DisplayName("stream: dev tier via cookie when header absent (<audio/> parity)")
    void stream_devTierCookie() throws Exception {
        URI base = audioStreamUrl.toURI();
        URL url = new URI(
                        base.getScheme(),
                        base.getAuthority(),
                        base.getPath(),
                        "t=1",
                        null)
                .toURL();
        StreamTestSupport.consumeOpeningChunk(
                url,
                Map.of(COOKIE_HEADER, DevTierRequestFilter.DEV_TIER_COOKIE + "=admin"),
                sessionManager);
    }

    @Test
    @DisplayName("anonymous + sentinel: stream opens")
    void anonymous_streamWhenSentinel() throws Exception {
        StreamTestSupport.consumeOpeningChunk(
                audioStreamUrl,
                Map.of(COOKIE_HEADER, DevTierRequestFilter.DEV_TIER_COOKIE + "=anonymous"),
                sessionManager);
    }

    @Test
    @DisplayName("anonymous + sentinel: stream opens without dev tier cookie")
    void anonymous_streamWithoutDevTierCookie() throws Exception {
        StreamTestSupport.consumeOpeningChunk(audioStreamUrl, Map.of(), sessionManager);
    }

    @Test
    @DisplayName("viewer: GET drops allowed; POST forbidden")
    void viewer_dropSplit() {
        given().header(DevTierRequestFilter.X_RESON8_DEV_TIER, "viewer")
                .when()
                .get("/audio/drop")
                .then()
                .statusCode(200);

        given().header(DevTierRequestFilter.X_RESON8_DEV_TIER, "viewer")
                .contentType("application/json")
                .body("{\"drop\":\"x\"}")
                .when()
                .post("/audio/drop")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("viewer: debug forbidden")
    void viewer_debugForbidden() {
        given().header(DevTierRequestFilter.X_RESON8_DEV_TIER, "viewer")
                .when()
                .get("/api/debug/mixer-dump")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("admin: debug allowed")
    void admin_debugAllowed() {
        given().header(DevTierRequestFilter.X_RESON8_DEV_TIER, "admin")
                .when()
                .get("/api/debug/mixer-dump")
                .then()
                .statusCode(200);
    }
}
