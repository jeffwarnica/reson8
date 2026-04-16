package com.coherentnetworksolutions.reson8.audio.input;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.State;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.signal.SignalEndpoint;

import io.quarkus.logging.Log;


public class WindGaugeChannel implements GaugeChannel {
    private final String name;
    private final Bin bin;
    private final Element filter;
    private final Element volume;
    private Element noiseSrc;
    private double targetIntensity = 0.2;
    // private double smoothedIntensity = 0.2;
    // private double currentCutoff = 1000.0;
    private double phase = 0.0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private double smoothingRate;
    private double baseGain;
    private double currentIntensity = 0.0;

    public WindGaugeChannel(SignalEndpoint signalEndpoint) {
        this.name = signalEndpoint.getName();
        ProceduralConfig procedureConfig = signalEndpoint.getProcedureConf();
        
        // Config values
        this.targetIntensity = procedureConfig.intensity().orElse(0.5);
        this.smoothingRate = procedureConfig.smoothingrate().orElse(0.02);
        // Capture the static Gain from config to use as our 'ceiling'
        this.baseGain = signalEndpoint.getSoundDefinition().procedural().orElseThrow().gain().orElse(1.0);

        bin = new Bin(name + "_bin");
        String prefix = name + "::" + System.nanoTime() + "::";

        // 1. Elements
        noiseSrc = ElementFactory.make("audiotestsrc", prefix + "noise");
        noiseSrc.set("wave", 6); // PINK
        noiseSrc.set("is-live", true);
        noiseSrc.set("do-timestamp", true);
        
        // Ensure we are working with standard stereo early
        Element sourceCaps = ElementFactory.make("capsfilter", prefix + "src_caps");
        sourceCaps.setCaps(Caps.fromString("audio/x-raw, channels=2, channel-mask=(bitmask)0x3"));

        // The 'Character' of the wind (cutoff frequency)
        filter = ElementFactory.make("audiowsinclimit", prefix + "filter");
        filter.set("mode", 0); // Low Pass

        // The internal VCA (Intensity * BaseGain)
        volume = ElementFactory.make("volume", prefix + "vol");
        
        // INITIAL STATE
        // We set intensity low initially, but it will be ramped by the scheduler
        this.currentIntensity = 0.0; 
        updateInternalVolume(); 

        bin.addMany(noiseSrc, sourceCaps, filter, volume);
        Element.linkMany(noiseSrc, sourceCaps, filter, volume);

        bin.addPad(new GhostPad("src", volume.getStaticPad("src")));
    
        bin.setState(State.READY);
        
        // Start the physics engine
        scheduler.scheduleAtFixedRate(this::modulateWind, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void modulateWind() {
        // 1. Move toward the 'Weather' (Target)
        if (Math.abs(currentIntensity - targetIntensity) > 0.001) {
            currentIntensity += (targetIntensity - currentIntensity) * smoothingRate;
        }

        // 2. Advance Phase (Gust Frequency)
        // We scale the speed of the woosh by the intensity:
        // High intensity = faster gusts.
        double gustFrequency = 0.04;
        double speed = 0.05 + (currentIntensity * gustFrequency);
        phase += speed;

        // 3. Calculate "Instantaneous" Pressure (The Woosh)
        // We oscillate around currentIntensity.
        // We use a bit of Math.sin and some random 'jitter' for character.
        double gustFactor = 0.15; // gust intensity expansion is +/- 15% of middle 
        double gust = Math.sin(phase) * (currentIntensity * gustFactor); 
        double finalDrive = Math.max(0.01, currentIntensity + gust);

        // 4. Update the GStreamer Elements
        applyPhysics(finalDrive);
    }

    private void applyPhysics(double drive) {
        // Cutoff frequency maps to 'whistle'
        double cutoff = 40 + (Math.pow(drive, 2) * 2500);
        filter.set("cutoff", cutoff);

        // Volume maps to 'pressure'
        // We use baseGain from config as our ceiling
        double vcaValue = baseGain * Math.pow(drive, 2);
        volume.set("volume", vcaValue);
    }

    private void updateInternalVolume() {
        // The "Generator Output" is the combination of its base gain and current intensity
        // We use a square or cube curve for volume so it feels more natural
        double intensityFactor = Math.pow(currentIntensity, 2); 
        volume.set("volume", baseGain * intensityFactor);
    }

    @Override
    public Caps getCaps() {
        // Procedural wind is generated to match the mixer exactly
        return Caps.fromString(Mixer.CAPS);
    }
    
    @Override
    public double getIntensity() { return targetIntensity; }
    
    @Override
    public String getChannelName() { return name; }

    
    @Override
    public void start() {
        // Log.debug("start()");
        // bin.setState(State.PLAYING);
    }

    @Override
    public double getGain() {
        return 0f;
    }

    @Override
    public boolean supportsGain() {
        return true;
    }

    @Override
    public void setGain(double vol) {
        Log.debugf("setGain([%s]", vol);
        volume.set("volume", vol);
    }

    @Override
    public Element getSrcElement() {
        Log.debugf("my 'src' element is [%s]", bin);
        return bin;
    }
    
    @Override
    public boolean supportsIntensity() {
        return true;
    }

    @Override
    public void dispose() {
        scheduler.shutdown();
        bin.setState(State.NULL);

    }

    @Override
    public void setIntensity(double intensity) {
        this.targetIntensity = intensity;
    }


}
