package com.coherentnetworksolutions.reson8.audio.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(com.coherentnetworksolutions.reson8.GstTestProfile.class)
class WavCacheIntegrationTest {
    @Inject
    WavCache wavCache;
    @Inject
    Reson8Config config;

    @Test
    void loadedWavHasValidCapsString() {
        // requires a known test .wav in src/test/resources
        CachedWav wav = wavCache.getOrLoad("sounds/chirp_short1.wav");
        assertNotNull(wav);
        assertTrue(wav.capsString().startsWith("audio/x-raw,format="));
        // Caps.fromString() must succeed — if Gst isn't running this throws
        assertNotNull(wav.caps());
        assertTrue(wav.caps().isFixed());
    }

    @Test
    void bytesPerFrameMatchesCapsChannelCount() {
        CachedWav wav = wavCache.getOrLoad("sounds/chirp_short1.wav");
        assertEquals(wav.channels() * (wav.sampleSizeInBits() / 8), wav.bytesPerFrame());
    }
}
