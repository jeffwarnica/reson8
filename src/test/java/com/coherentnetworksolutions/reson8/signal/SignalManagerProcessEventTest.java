package com.coherentnetworksolutions.reson8.signal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.sound.SoundDefinitionRegistry;
import com.coherentnetworksolutions.reson8.audio.utils.map.CurveMapFactory;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.vertx.mutiny.core.eventbus.EventBus;

@ExtendWith(MockitoExtension.class)
class SignalManagerProcessEventTest {

    @Mock Reson8Config config;
    @Mock SoundDefinitionRegistry soundDefinitionRegistry;
    @Mock InputChannelFactory channelFactory;
    @Mock Mixer mixer;
    @Mock EventBus eventBus;
    @Mock CurveMapFactory curveFactory;
    @Mock K8sSyncState k8sSyncState;
    @Spy  ObjectMapper objectMapper;

    @InjectMocks
    SignalManager signalManager;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak detected in SignalManagerProcessEventTest.");
    }

    // ─── processEvent ─────────────────────────────────────────────────

    @Test
    @DisplayName("processEvent: matching JsonPath triggers the bucket")
    void processEvent_matchingEvent_triggersBucket() throws Exception {
        // Bucket configured to match events whose `reason` equals "Killing"
        SignalBucket bucket = mockEventBucket("pod-killed", "$..[?(@.reason == 'Killing')]");
        registerBucket(bucket);

        Event event = buildEvent("Killing", "BackOff");

        signalManager.processEvent(event);

        verify(bucket).trigger();
    }

    @Test
    @DisplayName("processEvent: non-matching JsonPath does not trigger the bucket")
    void processEvent_nonMatchingEvent_doesNotTrigger() throws Exception {
        SignalBucket bucket = mockEventBucket("pod-killed", "$..[?(@.reason == 'Killing')]");
        registerBucket(bucket);

        Event event = buildEvent("BackOff", "Normal");

        signalManager.processEvent(event);

        verify(bucket, never()).trigger();
    }

    @Test
    @DisplayName("processEvent: only matching bucket is triggered when multiple buckets exist")
    void processEvent_multipleBuckets_onlyMatchingTriggered() throws Exception {
        SignalBucket bucketA = mockEventBucket("killing-bucket", "$..[?(@.reason == 'Killing')]");
        SignalBucket bucketB = mockEventBucket("failed-bucket",  "$..[?(@.reason == 'Failed')]");
        registerBucket(bucketA);
        registerBucket(bucketB);

        Event event = buildEvent("Killing", "BackOff");

        signalManager.processEvent(event);

        verify(bucketA).trigger();
        verify(bucketB, never()).trigger();
    }

    // ─── helpers ──────────────────────────────────────────────────────

    private SignalBucket mockEventBucket(String name, String jsonPathExpr) {
        SignalBucket b = mock(SignalBucket.class);
        when(b.getName()).thenReturn(name);
        when(b.getSourceType()).thenReturn(com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType.KUBERNETES_EVENT);
        when(b.getJsonPath()).thenReturn(JsonPath.compile(jsonPathExpr));
        return b;
    }

    @SuppressWarnings("unchecked")
    private void registerBucket(SignalBucket bucket) throws Exception {
        Field f = SignalManager.class.getDeclaredField("buckets");
        f.setAccessible(true);
        ((Map<String, SignalBucket>) f.get(signalManager)).put(bucket.getName(), bucket);
    }

    private Event buildEvent(String reason, String action) {
        Event event = new Event();
        event.setReason(reason);
        event.setAction(action);
        io.fabric8.kubernetes.api.model.ObjectReference ref = new io.fabric8.kubernetes.api.model.ObjectReference();
        ref.setKind("Pod");
        ref.setName("test-pod");
        event.setRegarding(ref);
        return event;
    }
}
