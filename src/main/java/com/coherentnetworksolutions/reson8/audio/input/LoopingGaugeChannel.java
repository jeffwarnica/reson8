package com.coherentnetworksolutions.reson8.audio.input;

import java.nio.file.Path;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
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
        volume.set("volume", initialGain * 0.2);

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
    public Caps getCaps() {
        return Caps.fromString("audio/x-raw");
    }

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