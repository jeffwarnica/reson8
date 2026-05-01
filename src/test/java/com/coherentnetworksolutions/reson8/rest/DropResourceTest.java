package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;
import com.coherentnetworksolutions.reson8.signal.SignalManager;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class DropResourceTest {

    @InjectMock
    SignalManager signalManager;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak: a production class is calling " +
                "Caps.fromString() or ElementFactory.make() directly.");
    }

    @BeforeEach
    void setUp() {
        when(signalManager.getSignalBuckets()).thenReturn(List.of());
    }

    // ─── GET /audio/drop ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /audio/drop: returns empty list when no drops are registered")
    void listDrops_noDrops_returnsEmptyList() {
        given()
            .when().get("/audio/drop")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    @DisplayName("GET /audio/drop: returns only DROP-type bucket names")
    void listDrops_mixedBuckets_returnsOnlyDrops() {
        SignalBucket drop1 = dropBucket("chirp");
        SignalBucket drop2 = dropBucket("boom");
        SignalBucket loop  = loopBucket("ambient");
        when(signalManager.getSignalBuckets()).thenReturn(List.of(drop1, drop2, loop));

        given()
            .when().get("/audio/drop")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("$", containsInAnyOrder("chirp", "boom"));
    }

    // ─── POST /audio/drop ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /audio/drop: triggers the named drop bucket")
    void triggerDrop_knownDrop_returns200AndTriggers() {
        SignalBucket bucket = dropBucket("chirp");
        when(signalManager.getSignalBucket("chirp")).thenReturn(bucket);

        given()
            .contentType(ContentType.JSON)
            .body("{\"drop\":\"chirp\"}")
            .when().post("/audio/drop")
            .then()
            .statusCode(200);

        verify(bucket).trigger();
    }

    @Test
    @DisplayName("POST /audio/drop: returns 404 when drop name is unknown")
    void triggerDrop_unknownDrop_returns404() {
        when(signalManager.getSignalBucket("unknown")).thenReturn(null);

        given()
            .contentType(ContentType.JSON)
            .body("{\"drop\":\"unknown\"}")
            .when().post("/audio/drop")
            .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("POST /audio/drop: returns 400 when drop name is blank")
    void triggerDrop_blankName_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"drop\":\"\"}")
            .when().post("/audio/drop")
            .then()
            .statusCode(400);
    }

    // ─── helpers ──────────────────────────────────────────────────────

    private SignalBucket dropBucket(String name) {
        SignalBucket b = mock(SignalBucket.class);
        when(b.getName()).thenReturn(name);
        when(b.getSoundType()).thenReturn(Reson8Config.SoundType.DROP);
        return b;
    }

    private SignalBucket loopBucket(String name) {
        SignalBucket b = mock(SignalBucket.class);
        when(b.getName()).thenReturn(name);
        when(b.getSoundType()).thenReturn(Reson8Config.SoundType.LOOP);
        return b;
    }
}
