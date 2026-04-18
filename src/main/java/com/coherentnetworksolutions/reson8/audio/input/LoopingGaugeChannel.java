package com.coherentnetworksolutions.reson8.audio.input;

import java.nio.file.Path;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.PlayBin;

import com.coherentnetworksolutions.reson8.audio.utils.VolumeScaler;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class LoopingGaugeChannel implements GaugeChannel {
    private final String name;
    private final Bin bin;
    private final Element filter;
    private final Element volume;
    private PlayBin playBin;
    private double intensity;
    private String gsUri;
    // kinda "max volume" fader for the sound. 0.0 to 1.0, default 1.0 (full volume).
    private double baseGain = 1.0f;

    public LoopingGaugeChannel(SignalBucket signalEndpoint, Reson8Config config) {
        this.name = signalEndpoint.getName();
        this.bin = new Bin(name + "_bin");
        String prefix = name + "::" + System.nanoTime() + "::";

        // 1. Setup Playbin (The Source)
        playBin = (PlayBin) ElementFactory.make("playbin", prefix + "playbin");
        String filePath = signalEndpoint.getSoundDefinition().loop().map(l -> l.filename()).orElseThrow();
        gsUri = Path.of(config.audioPath()).resolve(filePath).toAbsolutePath().toUri().toString();
        playBin.set("uri", gsUri);

        // 2. Setup the Processing Chain (The Sink Bin)
        Bin sinkBin = new Bin(prefix + "sink_bin");
        filter = ElementFactory.make("audiocheblimit", prefix + "filter");
        filter.set("mode", 0);

        volume = ElementFactory.make("volume", prefix + "vol");
        double initialGain = signalEndpoint.getSoundDefinition().loop().orElseThrow().gain();
        volume.set("volume", initialGain * 0);// TODO

        Element endPoint = ElementFactory.make("identity", prefix + "end");

        // Add them to the sinkBin (DO NOT add playBin here)
        sinkBin.addMany(filter, volume, endPoint);
        filter.link(volume);
        volume.link(endPoint);

        // Add the GHOST SINK pad so playbin has somewhere to plug in
        sinkBin.addPad(new GhostPad("sink", filter.getStaticPad("sink")));

        // 3. Attach the chain to playbin
        playBin.set("audio-sink", sinkBin);

        playBin.connect((PlayBin.ABOUT_TO_FINISH) (pb) -> {
            Log.debugf("[%s] ABOUT_TO_FINISH. Looping file...", name);
            pb.set("uri", gsUri);
        });

        // 4. Add playBin to our main outer bin
        bin.add(playBin);

        // 5. EXTREMELY IMPORTANT:
        // We need to ghost the "src" of our processing chain to the outside of our main
        // bin.
        // Since sinkBin is now INSIDE playbin, we have to ghost from playbin's
        // internal sinkBin endpoint to the outer world.
        bin.addPad(new GhostPad("src", endPoint.getStaticPad("src")));

        bin.setState(State.READY);
    }
    
    @Override
    public void setIntensity(@Min(0) @Max(100) double intensity) {
        this.intensity = intensity;

        // Normalize for math: 0.0 to 1.0
        double norm = intensity / 100.0;
        double curve = Math.pow(norm, 2); // Quadratic response

        // 1. Muffle Logic (The "Distance" effect)
        // Low intensity = distant/muffled. High = splashing in your ears.
        // 600Hz (murmur) to 12,000Hz (full splash detail)
        double cutoff = 600.0 + (curve * 11400.0);
        filter.set("cutoff", cutoff);

        // 2. Resonant Ripple (The "Babble" effect)
        // In audiocheblimit, 'ripple' adds peaks to the passband.
        // Too much ripple (15.0) can sound like a metallic whistle.
        // We'll scale from 0.5 (smooth) to 10.0 (sharp splashes).
        double ripple = 0.5 + (norm * 9.5);
        filter.set("ripple", ripple);

        // 3. Amplitude Mapping
        // We don't want the brook to be 0 volume at 0 intensity;
        // it should be a quiet background murmur.
        // Range: 0.1 (murmur) to 1.0 (rushing stream)
        double vcaValue = 0.1 + (norm * 0.9);

        // Apply baseGain fader (0.0 - 1.0)
        volume.set("volume", vcaValue * baseGain);

        Log.debugf("[%s] Water flow intensity: %.1f%% (Cutoff: %.0fHz, Ripple: %.1f, Vol: %.2f)", name, intensity,
                cutoff, ripple, vcaValue);
    }
    
    @Override
    public double getIntensity() { return intensity; }

    @Override
    public Caps getCaps() {
        return Caps.fromString("audio/x-raw");
    }

    @Override
    public Element getSrcElement() { return bin; }
    
    @Override public String getChannelName() { return name; }
    @Override public void setGain(@Min(0) @Max(100) double vol) {  volume.set("volume", VolumeScaler.humanToGstVolume(vol)); }
    // @Override public boolean supportsGain() { return true; }
    // @Override public boolean supportsIntensity() { return true; }

    @Override
    public void start() {
        bin.setState(State.PLAYING);
        Log.debug("tried to setState(State.PLAYING)");
    }

    @Override
    public double getGain() {
        return (double) volume.get("volume");
    }

    @Override
    public void dispose() {
        playBin.setState(State.NULL); // Releases file locks and native decoders
        bin.setState(State.NULL);
    }


}