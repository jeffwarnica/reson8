package com.coherentnetworksolutions.reson8;

import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.sound.sampled.AudioFormat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(com.coherentnetworksolutions.reson8.GsTestProfile.class)
public class WavCacheCapsBoundaryIT {

    @Inject
    GsToolkit toolkit;

    @Disabled(
        "CachedWav.create() derives the caps string from AudioFormat parameters, so a custom " +
        "Encoding name does not produce a malformed GStreamer caps string. " +
        "Revisit when WavCache exposes a path that takes a raw capsString so it can be injected directly."
    )
    @Test
    void malformedCapsStringThrowsGracefully() {
        assertThrows(RuntimeException.class,
                () -> CachedWav.create(new byte[8],
                        buildFakeFormat("NOT_A_REAL_FORMAT"),
                        toolkit));
    }

    // -----------------------------------------------------------------------

    /**
     * Returns an AudioFormat whose {@link AudioFormat.Encoding} carries the
     * supplied name string.  Note: {@code CachedWav.generateCapsString()} uses
     * only the encoding *category* (PCM_SIGNED vs. other), so the name alone
     * does not produce a malformed GStreamer caps string.
     */
    private AudioFormat buildFakeFormat(String encodingName) {
        return new AudioFormat(
                new AudioFormat.Encoding(encodingName),
                8000f,  // sampleRate
                16,     // sampleSizeInBits
                1,      // channels
                2,      // frameSize
                8000f,  // frameRate
                false   // bigEndian
        );
    }
}
