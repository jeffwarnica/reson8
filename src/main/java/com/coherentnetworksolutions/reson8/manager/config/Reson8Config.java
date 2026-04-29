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

        // String token();
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

        Optional<CurveConfig> curve();
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
        Optional<StochasticConfig> stochastic();
    }

    public enum SoundType {
        @WithName("drop") DROP,
        @WithName("loop") LOOP,
        @WithName("procedural") PROCEDURAL,
        @WithName("stochastic") STOCHASTIC
    }

    interface StochasticConfig {
        String directory(); // directory containing multiple sound files to randomly choose from
        @WithDefault("32")
        int maxSimultaneous(); // max number of simultaneous sounds from this directory
        @WithDefault("0.1")
        double pitchRandomization(); // random pitch variation in semitones (e.g. 0.1 = +/- 0.1 semitones)
        @WithDefault("poisson")
        StochasticDistribution distribution(); // distribution for random selection of files
        double variance();
        @WithDefault("0.05")
        @WithName("smoothingrate")
        double smoothingrate();
    }

    public enum StochasticDistribution {
        @WithName("poisson") POISSON,
        @WithName("weighted") JITTERED,
        @WithName("bursty") BURSTY
    }

    interface LoopConfig {
        String filename();
        
        @WithDefault("1.0")
        Double gain();
        @WithDefault("0.02")
        @WithName("smoothingrate")
        Double smoothingrate();
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
        Double gain();

        @WithName("cutoffmin")
        Optional<Double> cutoffmin();

        @WithName("cutoffscale")
        Optional<Double> cutoffscale();

        @WithDefault("0.02")
        @WithName("smoothingrate")
        Double smoothingrate();

        @WithName("intensity")
        @WithDefault("50.0") //todo, check if this is necessary; why are we configuring a intensity?
        Double intensity();

        Optional<CurveConfig> curve();
    }

    interface CurveConfig {
        @WithDefault("linear")
        Interpolation interpolation(); // linear, smooth, step

        List<CurvePoint> points();
        @WithDefault("false")
        Boolean extrapolate();

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
        
        default Point toRecord() {
            return new Point(input(), output());
        }
    }
}