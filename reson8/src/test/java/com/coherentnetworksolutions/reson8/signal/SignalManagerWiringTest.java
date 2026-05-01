package com.coherentnetworksolutions.reson8.signal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.sound.SoundDefinitionRegistry;
import com.coherentnetworksolutions.reson8.audio.utils.map.CurveMapFactory;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.mutiny.core.eventbus.EventBus;

@ExtendWith(MockitoExtension.class)
class SignalManagerWiringTest {

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
                "Toolkit abstraction leak detected in SignalManagerWiringTest.");
    }

    @Test
    @DisplayName("Wiring delegates channel startup to Mixer")
    void wiringDelegatesStartupToMixer() throws Exception {
        when(mixer.isReady()).thenReturn(true);

        InputChannel channel = mock(InputChannel.class);
        when(channel.getChannelName()).thenReturn("wind");

        SignalBucket bucket = mock(SignalBucket.class);
        when(bucket.getName()).thenReturn("wind");
        when(bucket.getSourceType()).thenReturn(SourceType.KUBERNETES_STATS);
        when(bucket.getInputChannel()).thenReturn(channel);
        registerBucket(bucket);

        signalManager.onMixerReady("ready");

        verify(mixer).addInputChannel(channel);
        verify(mixer).setInputChannelVolume("wind", 80.0);
        verify(channel).setTargetIntensity(50);
        verify(channel, never()).start();
        verify(eventBus).publish("signal-manager-ready", null);
    }

    @SuppressWarnings("unchecked")
    private void registerBucket(SignalBucket bucket) throws Exception {
        Field f = SignalManager.class.getDeclaredField("buckets");
        f.setAccessible(true);
        ((Map<String, SignalBucket>) f.get(signalManager)).put(bucket.getName(), bucket);
    }
}
