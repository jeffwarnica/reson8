package com.coherentnetworksolutions.reson8.manager.config;

import java.util.List;
import java.util.Optional;

import com.coherentnetworksolutions.reson8.audio.utils.map.SignalCurveMap.Point;

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

        ThanosConfig thanos();
    }

    interface ThanosConfig {
        String baseUrl();

        @WithDefault("false")
        boolean ignoreCerts();
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

        String sound();

        @WithName("source-type")
        Optional<SourceType> sourceType();

        Optional<String> query();

        @WithName("metric")
        Optional<ClusterMetric> metric();

        Optional<CurveConfig> curve();
    }

    public enum SourceType {
        @WithName("prometheus") PROMETHEUS,
        @WithName("kubernetes_event") KUBERNETES_EVENT,
        @WithName("kubernetes_stats") KUBERNETES_STATS,
    }

    public enum ClusterMetric {
        @WithName("cpu") CPU,
        @WithName("memory") MEMORY,
        @WithName("node_readiness") NODE_READINESS,
        @WithName("pending_pods") PENDING_PODS,
        @WithName("deployment_health") DEPLOYMENT_HEALTH,
    }

    interface Soundscape {
        String name();

        List<SoundDefinition> sounds();
    }

    interface SoundDefinition {
        String name();

        @WithName("sound-type")
        SoundType soundType();

        Optional<LoopConfig> loop();
        Optional<DropConfig> drop();
        Optional<ProceduralConfig> procedural();
        Optional<StochasticConfig> stochastic();
    }

    public enum SoundType {
        @WithName("drop") DROP,
        @WithName("loop") LOOP,
        @WithName("procedural") PROCEDURAL,
        @WithName("stochastic") STOCHASTIC
    }

    interface StochasticConfig {
        String directory();
        @WithDefault("32")
        int maxSimultaneous();
        @WithDefault("100.0")
        @WithName("ceiling")
        Double ceiling();
        @WithDefault("0.1")
        double pitchRandomization();
        @WithDefault("0.05")
        @WithName("smoothingrate")
        double smoothingrate();
    }

    interface LoopConfig {
        String filename();

        @WithDefault("1.0")
        @WithName("output-scale")
        Double outputScale();
        @WithDefault("0.02")
        @WithName("smoothingrate")
        Double smoothingrate();
    }

    interface DropConfig {
        String filename();
        @WithDefault("100.0")
        @WithName("ceiling")
        Double ceiling();
    }

    interface ProceduralConfig {
        @WithName("generator")
        GeneratorType generatorType();

        @WithDefault("1.0")
        @WithName("output-scale")
        Double outputScale();

        @WithDefault("0.02")
        @WithName("smoothingrate")
        Double smoothingrate();

        @WithName("intensity")
        @WithDefault("50.0")
        Double intensity();
    }

    public enum GeneratorType {
        @WithName("wind") WIND
    }

    interface CurveConfig {
        @WithDefault("linear")
        Interpolation interpolation();

        List<CurvePoint> points();
    }

    public enum Interpolation {
        @WithName("linear")   LINEAR,
        @WithName("smooth")   SMOOTH,
        @WithName("monotone") MONOTONE
    }

    interface CurvePoint {
        @WithName("in")
        double input();

        @WithName("out")
        double output();

        default Point toRecord() {
            return new Point(input(), output());
        }
    }
}
