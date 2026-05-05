package com.coherentnetworksolutions.reson8.signal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.sound.SoundDefinitionRegistry;
import com.coherentnetworksolutions.reson8.audio.utils.map.CurveMapFactory;
import com.coherentnetworksolutions.reson8.audio.utils.map.SignalCurveMap;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.mutiny.core.eventbus.EventBus;

@ExtendWith(MockitoExtension.class)
class SignalManagerIntensityMappingTest {

    @Mock Reson8Config config;
    @Mock SoundDefinitionRegistry soundDefinitionRegistry;
    @Mock InputChannelFactory channelFactory;
    @Mock Mixer mixer;
    @Mock EventBus eventBus;
    @Mock CurveMapFactory curveFactory;
    @Mock K8sSyncState k8sSyncState;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    SignalManager signalManager;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak detected in SignalManagerIntensityMappingTest.");
    }

    @Test
    void updateSignalIntensityMapsRawValueBeforeApplyingIntensity() throws Exception {
        SignalBucket bucket = mock(SignalBucket.class);
        SignalCurveMap curve = mock(SignalCurveMap.class);
        when(bucket.getName()).thenReturn("cpu");
        when(bucket.getCurve()).thenReturn(curve);
        when(curve.map(42.0)).thenReturn(66.0);
        registerBucket(bucket);

        signalManager.updateSignalIntensity("cpu", 42.0);

        var order = inOrder(curve, bucket);
        order.verify(curve).map(42.0);
        order.verify(bucket).setIntensity(66.0);
    }

    @SuppressWarnings("unchecked")
    private void registerBucket(SignalBucket bucket) throws Exception {
        Field f = SignalManager.class.getDeclaredField("buckets");
        f.setAccessible(true);
        ((Map<String, SignalBucket>) f.get(signalManager)).put(bucket.getName(), bucket);
    }
}
