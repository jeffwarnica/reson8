package com.coherentnetworksolutions.reson8.audio;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.input.GsDropChannel;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.output.to.BrowserSessionManager;
import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import static org.awaitility.Awaitility.await;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import io.smallrye.mutiny.subscription.Cancellable;

@QuarkusTest
@TestProfile(com.coherentnetworksolutions.reson8.GsTestProfile.class)
class GsDropChannelAudioFlowIT {

    @Inject
    GsToolkit toolkit; // NativeGsToolkit in this profile

    @Inject
    Mixer mixer;

    @Inject
    BrowserSessionManager browserSessionManager;

    @Test
    void triggeredDropProducesAudioSamples() throws Exception {
        AtomicInteger samplesReceived = new AtomicInteger(0);
        Cancellable subscription = browserSessionManager.subscribe()
                .subscribe().with(bytes -> samplesReceived.addAndGet(bytes.length));

        var mockDef = mock(Reson8Config.SoundDefinition.class);
        var mockDropDef = mock(Reson8Config.DropConfig.class);
        when(mockDropDef.filename()).thenReturn("sounds/chirp_short1.wav");
        when(mockDropDef.ceiling()).thenReturn(100.0);
        when(mockDef.drop()).thenReturn(Optional.of(mockDropDef));

        var bucket = mock(SignalBucket.class);
        when(bucket.getName()).thenReturn("AudioFlowIT");
        when(bucket.getSoundDefinition()).thenReturn(mockDef);

        var wav = mock(CachedWav.class);
        when(wav.pcmData()).thenReturn(new byte[44100 * 4]);
        when(wav.bytesPerFrame()).thenReturn(4);
        when(wav.sampleRate()).thenReturn(44100.0);
        when(wav.caps()).thenReturn(toolkit.capsFromString("audio/x-raw,format=F32LE,rate=44100,channels=1"));

        GsDropChannel ch = new GsDropChannel(bucket, wav, toolkit);
        mixer.addInputChannel(ch);
        try {
            ch.trigger();
            await().atMost(3, TimeUnit.SECONDS)
                    .until(() -> samplesReceived.get() > 0);
            assertTrue(samplesReceived.get() > 0, "Drop trigger produced no audio");
        } finally {
            subscription.cancel();
            mixer.removeInputChannel(ch.getChannelName());
        }
    }
}
