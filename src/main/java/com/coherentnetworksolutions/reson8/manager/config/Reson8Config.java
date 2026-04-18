package com.coherentnetworksolutions.reson8.manager.config;

import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "reson8")
public interface Reson8Config {
    @WithName("audio-path")
    @WithDefault("${RESON8_AUDIO_PATH:${user.dir}/src/main/resources}")
    String audioPath();

    K8sConfig k8s();

    SignalMap signalMap();

    List<Soundscape> soundscapes();

    interface K8sConfig {
        String cluster();

        NamespaceConfig namespaces();

        String map();
    }

    interface NamespaceConfig {
        boolean all();

        @WithDefault("")
        List<String> include();

        @WithDefault("")
        List<String> exclude();
    }

    interface SignalMap {
        String name();

        @WithName("default-soundscape")
        String defaultSoundscape();

        List<InputMapping> inputs();
    }

    interface InputMapping {
        String name();

        String sound(); // references "base/stream" or "stream"

        Optional<SourceType> type();
        
        Optional<String> query();

        Optional<String> unit();
    }

    public enum SourceType {
        @WithName("prometheus") PROMETHEUS,
        @WithName("kubernetes_event") KUBERNETES_EVENT,
        @WithName("kubernetes_stats") KUBERNETES_STATS,
        // @WithName("dummy_loop") DUMMY_LOOP,
        // @WithName("dummy_drop") DUMMY_DROP,
        // @WithName("dummy_procedure") DUMMY_PROCEDURE
    }

    interface Soundscape {
        String name();

        List<SoundDefinition> sounds();
    }

    interface SoundDefinition {
        String name();

        SoundType type(); // drop, loop, procedural

        // Auto-validated sub-configs
        Optional<LoopConfig> loop();
        Optional<DropConfig> drop();
        Optional<ProceduralConfig> procedural();
    }

    public enum SoundType {
        @WithName("drop") DROP,
        @WithName("loop") LOOP,
        @WithName("procedural") PROCEDURAL
    }

    interface LoopConfig {
        String filename();
        @WithDefault("1.0")
        Double gain();
    }

    interface DropConfig {
        String filename();
        @WithDefault("1.0")
        Double gain();
    }

    interface ProceduralConfig {
        @WithName("type")
        String className(); // e.g. WindGaugeChannel
        
        Optional<Double> phase();

        @WithName("gain")
        Optional<Double> gain();

        @WithName("cutoffmin")
        Optional<Double> cutoffmin();

        @WithName("cutoffscale")
        Optional<Double> cutoffscale();

        @WithName("smoothingrate")
        Optional<Double> smoothingrate();

        @WithName("intensity")
        Optional<Double> intensity();

        Optional<IntensityCurveConfig> curve();
    }

    interface IntensityCurveConfig {
        @WithDefault("linear")
        Interpolation interpolation(); // linear, smooth, step

        List<CurvePoint> points();

        @WithDefault("true")
        boolean extrapolate();
    }

    public enum Interpolation {
        @WithName("linear") LINEAR,
        @WithName("smooth") SMOOTH,
        @WithName("step") STEP
    }  
        

    interface CurvePoint {
        @WithName("in")
        double input();

        @WithName("out")
        double output();
    }
}