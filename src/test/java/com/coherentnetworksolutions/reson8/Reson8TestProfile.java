package com.coherentnetworksolutions.reson8;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class Reson8TestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "reson8.k8s.cluster", "mock",
            "reson8.signal-map.default-soundscape", "test-scape",
            "reson8.soundscapes[0].name", "test-scape",
            "reson8.soundscapes[0].sounds[0].name", "chirp",
            "reson8.soundscapes[0].sounds[0].type", "drop",
            "reson8.soundscapes[0].sounds[0].drop.filename", "sounds/chirp_short1.wav"
        );
    }
}
