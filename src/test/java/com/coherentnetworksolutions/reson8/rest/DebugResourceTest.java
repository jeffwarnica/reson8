package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DebugResourceTest {

    @InjectMock
    Mixer mixer;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak: a production class is calling " +
                "Caps.fromString() or ElementFactory.make() directly.");
    }

    @Test
    @DisplayName("GET /api/debug/mixer-dump returns 200 with plain text from dumpMixerState()")
    void getMixerDump_returnsDumpText() {
        when(mixer.dumpMixerState()).thenReturn("=== RESON8 CONSOLE DUMP [PLAYING] ===\nMASTER BUS: [PLAYING] | Volume: 1.00\n");

        given()
            .when().get("/api/debug/mixer-dump")
            .then()
            .statusCode(200)
            .contentType("text/plain")
            .body(containsString("RESON8 CONSOLE DUMP"));
    }

    @Test
    @DisplayName("GET /api/debug/mixer-dump: empty dump string is returned as-is")
    void getMixerDump_emptyResponse_returns200() {
        when(mixer.dumpMixerState()).thenReturn("");

        given()
            .when().get("/api/debug/mixer-dump")
            .then()
            .statusCode(200);
    }
}
