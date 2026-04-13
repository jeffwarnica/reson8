package com.coherentnetworksolutions.reson8.audio.providers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ProviderTest {
    // @Inject CannedNoiseProvider provider;
    // @Inject Mixer mixer;

    // @Test
    // void testCannedProviderPopulatesMixer() {
    //     provider.populate(mixer);
    //     // Verify the map actually contains the noise channels
    //     assertTrue(mixer.getInputChannels().containsKey("cpu-noise"));
    //     assertTrue(mixer.getInputChannels().containsKey("mem-noise"));
    // }
}