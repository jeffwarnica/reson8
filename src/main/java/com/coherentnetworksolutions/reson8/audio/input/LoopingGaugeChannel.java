package com.coherentnetworksolutions.reson8.audio.input;

import java.nio.file.Path;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.PlayBin;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalEndpoint;

import io.quarkus.logging.Log;

public class LoopingGaugeChannel implements GaugeChannel {
    private final String name;
    private final Bin bin;
    private final Element filter;
    private final Element volume;
    private PlayBin playBin;
    private double intensity;
    // private Reson8Config config;
    private String gsUri;

    public LoopingGaugeChannel(SignalEndpoint signalEndpoint, Reson8Config config) {
        Log.debugf("creating a channel named [%s]", signalEndpoint.getName());
        this.name = signalEndpoint.getName();
        // this.config = config;
        String filePath = signalEndpoint.getSoundDefinition().loop().map(l -> l.filename()).orElseThrow();
        Log.debugf("Source file: [%s]", filePath);

        this.bin = new Bin(name + "_bin");
        String prefix = name + "::" + System.nanoTime() + "::";

        // 1. Setup Playbin
        playBin = (PlayBin) ElementFactory.make("playbin", prefix + "playbin");
        gsUri = Path.of(config.audioPath()).resolve(filePath).toAbsolutePath().toUri().toString();
        Log.debugf("Source Uri: [%s]", gsUri);
        playBin.set("uri", gsUri);

        // 2. IMPORTANT: Tell playbin we only want the audio, and we want to process it
        // This makes playbin output raw audio to an 'audio-sink' we provide
        Bin sinkBin = new Bin(prefix + "sink_bin");
        filter = ElementFactory.make("audiocheblimit", prefix + "filter");
        filter.set("mode", 0); // High Pass for machinery whine
        
        volume = ElementFactory.make("volume", prefix + "vol");
        double initialGain = signalEndpoint.getSoundDefinition().loop().orElseThrow().gain();
        volume.set("volume", initialGain * 0.2); // Start at 20% weather intensity
        
        sinkBin.addMany(filter, volume);
        filter.link(volume);

        // Add a ghost pad so the playbin can "plug into" this mini-bin
        sinkBin.addPad(new GhostPad("sink", filter.getStaticPad("sink")));
        
        // Assign our processing chain as the sink for playbin
        playBin.set("audio-sink", sinkBin);

        playBin.connect(new PlayBin.ABOUT_TO_FINISH() {
            @Override
            public void aboutToFinish(PlayBin playbin) {
                Log.debugf("[%s] ABOUT_TO_FINISH. Looping file...", name);
                playbin.set("uri", gsUri);
            }
        });
        
        bin.add(playBin);
        
        // We ghost the volume's SRC pad to the outside world
        // Note: We have to wait for playbin to actually have a pad to ghost it,
        // OR we use a trick: add a fake sink/identity inside sinkBin and ghost that.
        // Easiest: Add an 'identity' element at the very end of sinkBin.
        Element endPoint = ElementFactory.make("identity", prefix + "end");
        sinkBin.add(endPoint);
        volume.link(endPoint);
        
        bin.addPad(new GhostPad("src", endPoint.getStaticPad("src")));
        
        Log.debugf("Bin pads are: [%s]", bin.getPads());
        Log.debugf("Bin elements are: [%s]", bin.getElements());
        bin.setState(State.READY);
        
    }
    
    @Override
    public void setIntensity(double intensity) {
        Log.debugf("setIntensity([%s])", intensity);
        this.intensity = intensity;
        // 1. Muffle Logic: 
        // At 0.0 (low intensity), the water is muffled (cutoff ~600Hz)
        // At 1.0 (high intensity), the water is fully open (cutoff ~8000Hz)
        double cutoff = 600 + (Math.pow(intensity, 2) * 7400);
        filter.set("cutoff", cutoff);
        
        // 2. Resonant Ripple:
        // As intensity increases, we increase the 'ripple' (resonance).
        // In Chebyshev, this will emphasize the "babbling" splash frequencies.
        filter.set("ripple", 0.1 + (intensity * 15.0));
        
        // 3. Amplitude mapping:
        // Water feels more intense when it's louder.
        volume.set("volume", 0.2 + (intensity * 0.8));
        
        Log.debugf("[%s] Water flow intensity: %f (Cutoff: %f)", name, intensity, cutoff);
    }
    
    @Override
    public double getIntensity() { return intensity; }

    @Override
    public Element getSrcElement() { return bin; }
    
    @Override public String getChannelName() { return name; }
    @Override public boolean supportsGain() { return true; }
    @Override public void setGain(double vol) { volume.set("volume", vol); }
    @Override public boolean supportsIntensity() { return true; }

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