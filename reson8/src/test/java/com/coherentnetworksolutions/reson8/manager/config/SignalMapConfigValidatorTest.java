package com.coherentnetworksolutions.reson8.manager.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ClusterMetric;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.CurveConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignalMapConfigValidatorTest {

    @Test
    @DisplayName("valid signal-map passes")
    void valid_ok() {
        List<String> errors = new ArrayList<>();
        SignalMapConfigValidator.collectErrors(
                configWithInputs(inputs(sampleInputMapping()), "forest"),
                errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("missing signal-map")
    void missingMap() {
        List<String> errors = new ArrayList<>();
        SignalMapConfigValidator.collectErrors(cfg(null), errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("signal-map is missing")));
    }

    @Test
    @DisplayName("empty inputs")
    void emptyInputs() {
        List<String> errors = new ArrayList<>();
        SignalMapConfigValidator.collectErrors(configWithInputs(List.of(), "forest"), errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("blank default soundscape")
    void blankSoundscape() {
        List<String> errors = new ArrayList<>();
        SignalMapConfigValidator.collectErrors(configWithInputs(inputs(sampleInputMapping()), "   "), errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("default-soundscape")));
    }

    private static Reson8Config.InputMapping sampleInputMapping() {
        return inputMapping("CPU load", "brook");
    }

    private static Reson8Config cfg(Reson8Config.SignalMap signalMap) {
        return new Reson8Config() {
            @Override public String audioPath() { return ""; }
            @Override public SecurityConfig security() { throw new UnsupportedOperationException(); }
            @Override public Reson8Config.OpenshiftOauthConfig openshiftOauth() {
                return new Reson8Config.OpenshiftOauthConfig() {
                    @Override public boolean discoveryEnabled() {
                        return false;
                    }

                    @Override public Optional<String> metadataUrl() {
                        return Optional.empty();
                    }

                    @Override public Optional<String> authServerUrl() {
                        return Optional.empty();
                    }
                };
            }
            @Override public K8sConfig k8s() { throw new UnsupportedOperationException(); }
            @Override public Reson8Config.SignalMap signalMap() { return signalMap; }
            @Override public List<Soundscape> soundscapes() { throw new UnsupportedOperationException(); }
        };
    }

    private static Reson8Config configWithInputs(List<Reson8Config.InputMapping> inputs, String defaultSoundscape) {
        return cfg(new Reson8Config.SignalMap() {
            @Override public String name() { return "n"; }
            @Override public String defaultSoundscape() { return defaultSoundscape; }
            @Override public List<Reson8Config.InputMapping> inputs() { return inputs; }
        });
    }

    private static List<Reson8Config.InputMapping> inputs(Reson8Config.InputMapping... m) {
        return List.of(m);
    }

    private static Reson8Config.InputMapping inputMapping(String name, String sound) {
        return new Reson8Config.InputMapping() {
            @Override public String name() { return name; }
            @Override public String sound() { return sound; }
            @Override public Optional<SourceType> sourceType() { throw new UnsupportedOperationException(); }
            @Override public Optional<String> query() { throw new UnsupportedOperationException(); }
            @Override public Optional<ClusterMetric> metric() { throw new UnsupportedOperationException(); }
            @Override public Optional<CurveConfig> curve() { throw new UnsupportedOperationException(); }
        };
    }
}
