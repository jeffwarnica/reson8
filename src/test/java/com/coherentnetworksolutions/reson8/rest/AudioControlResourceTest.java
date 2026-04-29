package com.coherentnetworksolutions.reson8.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.utils.map.SignalCurveMap;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;
import com.coherentnetworksolutions.reson8.signal.SignalManager;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;

@QuarkusTest
class AudioControlResourceTest {

    @InjectMock
    Mixer mixer;

    @InjectMock
    SignalManager signalManager;

    @BeforeEach
    void setUp() {
        when(mixer.getMasterVolume()).thenReturn(80.0);
        when(signalManager.isK8sSyncEnabled()).thenReturn(true);
        when(signalManager.getSignalBuckets()).thenReturn(List.of());
    }

    // ─── GET /audio/control/channels ─────────────────────────────────

    @Test
    @DisplayName("GET /channels: empty signal map returns empty JSON array")
    void getChannels_emptySignalMap_returnsEmptyList() {
        given()
            .when().get("/audio/control/channels")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    @DisplayName("GET /channels: all ChannelStateDTO fields are populated from the bucket")
    void getChannels_mapsBucketFieldsToDTO() {
        SignalBucket bucket = mock(SignalBucket.class);
        when(bucket.getName()).thenReturn("wind");
        when(bucket.getVolume()).thenReturn(70.0);
        when(bucket.getIntensity()).thenReturn(50.0);
        when(bucket.getCurrentIntensity()).thenReturn(45.0);
        when(bucket.isDrop()).thenReturn(false);
        when(bucket.getSourceType()).thenReturn(SourceType.PROMETHEUS);
        SignalCurveMap curve1 = mockCurve();
        when(bucket.getCurve()).thenReturn(curve1);
        when(mixer.getInputChannelVolume("wind")).thenReturn(0.8);
        when(signalManager.getSignalBuckets()).thenReturn(List.of(bucket));

        given()
            .when().get("/audio/control/channels")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(1))
            .body("[0].name",             is("wind"))
            .body("[0].chanVol",          is(70.0f))
            .body("[0].mixerVol",         is(0.8f))
            .body("[0].targetIntensity",  is(50.0f))
            .body("[0].currentIntensity", is(45.0f))
            .body("[0].isDrop",           is(false))
            .body("[0].sourceType",       is("PROMETHEUS"));
    }

    @Test
    @DisplayName("GET /channels: isDrop is true for a drop-type channel")
    void getChannels_dropChannel_hasFlagSet() {
        SignalBucket bucket = mock(SignalBucket.class);
        when(bucket.getName()).thenReturn("pod-killed");
        when(bucket.isDrop()).thenReturn(true);
        when(bucket.getSourceType()).thenReturn(SourceType.KUBERNETES_EVENT);
        SignalCurveMap curve2 = mockCurve();
        when(bucket.getCurve()).thenReturn(curve2);
        when(signalManager.getSignalBuckets()).thenReturn(List.of(bucket));

        given()
            .when().get("/audio/control/channels")
            .then()
            .statusCode(200)
            .body("[0].isDrop", is(true));
    }

    @Test
    @DisplayName("GET /channels: multiple buckets all appear in the response")
    void getChannels_multipleBuckets_allPresent() {
        SignalBucket b1 = namedBucket("wind",  SourceType.PROMETHEUS);
        SignalBucket b2 = namedBucket("drops", SourceType.KUBERNETES_EVENT);
        when(signalManager.getSignalBuckets()).thenReturn(List.of(b1, b2));

        given()
            .when().get("/audio/control/channels")
            .then()
            .statusCode(200)
            .body("name", containsInAnyOrder("wind", "drops"));
    }

    // ─── GET /audio/control/state ─────────────────────────────────────

    @Test
    @DisplayName("GET /state: returns master volume, k8s-sync flag and channel list")
    void getState_returnsFullMixerState() {
        when(mixer.getMasterVolume()).thenReturn(75.0);
        when(signalManager.isK8sSyncEnabled()).thenReturn(false);

        given()
            .when().get("/audio/control/state")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("masterVolume",  is(75.0f))
            .body("k8sSyncActive", is(false))
            .body("channels",      hasSize(0));
    }

    @Test
    @DisplayName("GET /state: channel DTOs include current and target intensity")
    void getState_channelDTOs_intensityFieldsReflectBucketValues() {
        SignalBucket bucket = namedBucket("cpu", SourceType.PROMETHEUS);
        when(bucket.getCurrentIntensity()).thenReturn(33.0);
        when(bucket.getIntensity()).thenReturn(60.0);
        when(signalManager.getSignalBuckets()).thenReturn(List.of(bucket));

        given()
            .when().get("/audio/control/state")
            .then()
            .statusCode(200)
            .body("channels[0].currentIntensity", is(33.0f))
            .body("channels[0].targetIntensity",  is(60.0f));
    }

    // ─── POST /audio/control/k8s-sync/{active} ────────────────────────

    @Test
    @DisplayName("POST /k8s-sync/true: enables k8s sync on SignalManager")
    void setK8sSync_true_invokesSignalManagerEnable() {
        given()
            .when().post("/audio/control/k8s-sync/true")
            .then()
            .statusCode(204);

        // Called twice: (1) direct call in the resource body, (2) via the "k8s-sync-enable"
        // event-bus message whose @ConsumeEvent consumer is registered at Quarkus build time
        // and still fires against the mock.
        verify(signalManager, times(2)).setK8sSyncEnabled(true);
    }

    @Test
    @DisplayName("POST /k8s-sync/false: disables k8s sync on SignalManager")
    void setK8sSync_false_invokesSignalManagerDisable() {
        given()
            .when().post("/audio/control/k8s-sync/false")
            .then()
            .statusCode(204);

        verify(signalManager, times(2)).setK8sSyncEnabled(false);
    }

    // ─── POST /audio/control/fader ────────────────────────────────────

    @Test
    @DisplayName("POST /fader: both volumes applied when chVol and mixVol ≥ 0")
    void setFader_bothNonNegative_updatesMixerAndChannel() {
        InputChannel mockChannel = mock(InputChannel.class);
        when(mixer.getInputChannel("wind")).thenReturn(mockChannel);

        given()
            .contentType(ContentType.JSON)
            .body("{\"channel\":\"wind\",\"chVol\":50.0,\"mixVol\":80.0}")
            .when().post("/audio/control/fader")
            .then()
            .statusCode(200);

        // chVol (50.0) ≥ 0 → setInputChannelVolume called (using req.mixVol as value)
        verify(mixer).setInputChannelVolume("wind", 80.0);
        // mixVol (80.0) ≥ 0 → setCeiling called (using req.chVol as value)
        verify(mockChannel).setCeiling(50.0);
    }

    @Test
    @DisplayName("POST /fader: negative chVol skips the mixer fader update")
    void setFader_negativeChVol_skipsSetInputChannelVolume() {
        InputChannel mockChannel = mock(InputChannel.class);
        when(mixer.getInputChannel("wind")).thenReturn(mockChannel);

        given()
            .contentType(ContentType.JSON)
            .body("{\"channel\":\"wind\",\"chVol\":-1.0,\"mixVol\":80.0}")
            .when().post("/audio/control/fader")
            .then()
            .statusCode(200);

        verify(mixer, never()).setInputChannelVolume(anyString(), anyDouble());
    }

    @Test
    @DisplayName("POST /fader: negative mixVol skips the channel ceiling update")
    void setFader_negativeMixVol_skipsSetCeiling() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"channel\":\"wind\",\"chVol\":50.0,\"mixVol\":-1.0}")
            .when().post("/audio/control/fader")
            .then()
            .statusCode(200);

        // mixVol (-1.0) < 0 → getInputChannel never called, setCeiling never called
        verify(mixer, never()).getInputChannel(anyString());
    }

    // ─── PATCH /audio/control/channels/{name}/target-intensity ────────

    @Test
    @DisplayName("PATCH target-intensity: delegates value to channel and returns 200")
    void setTargetIntensity_validBody_delegatesToChannel() {
        InputChannel mockChannel = mock(InputChannel.class);
        when(mixer.getInputChannel("wind")).thenReturn(mockChannel);

        given()
            .contentType(ContentType.JSON)
            .body("{\"targetIntensity\":75.0}")
            .when().patch("/audio/control/channels/wind/target-intensity")
            .then()
            .statusCode(200);

        verify(mockChannel).setTargetIntensity(75.0);
    }

    @Test
    @DisplayName("PATCH target-intensity: zero intensity is valid (floor of the range)")
    void setTargetIntensity_zeroIsAccepted() {
        InputChannel mockChannel = mock(InputChannel.class);
        when(mixer.getInputChannel("wind")).thenReturn(mockChannel);

        given()
            .contentType(ContentType.JSON)
            .body("{\"targetIntensity\":0.0}")
            .when().patch("/audio/control/channels/wind/target-intensity")
            .then()
            .statusCode(200);

        verify(mockChannel).setTargetIntensity(0.0);
    }

    @Test
    @DisplayName("PATCH target-intensity: missing body returns 400")
    void setTargetIntensity_missingBody_returns400() {
        given()
            .contentType(ContentType.JSON)
            .when().patch("/audio/control/channels/wind/target-intensity")
            .then()
            .statusCode(400);
    }

    // ─── helpers ──────────────────────────────────────────────────────

    /** Returns a minimal stub bucket that satisfies all ChannelStateDTO field reads. */
    private SignalBucket namedBucket(String name, SourceType sourceType) {
        SignalBucket b = mock(SignalBucket.class);
        when(b.getName()).thenReturn(name);
        when(b.getSourceType()).thenReturn(sourceType);
        SignalCurveMap curve = mockCurve();
        when(b.getCurve()).thenReturn(curve);
        return b;
    }

    /** Returns a stub {@link SignalCurveMap} with a trivial linear identity over [0, 100]. */
    private SignalCurveMap mockCurve() {
        SignalCurveMap curve = mock(SignalCurveMap.class);
        when(curve.minInput()).thenReturn(0.0);
        when(curve.maxInput()).thenReturn(100.0);
        when(curve.map(anyDouble())).thenAnswer(inv -> inv.getArgument(0));
        when(curve.mapUnclamped(anyDouble())).thenAnswer(inv -> inv.getArgument(0));
        return curve;
    }
}
