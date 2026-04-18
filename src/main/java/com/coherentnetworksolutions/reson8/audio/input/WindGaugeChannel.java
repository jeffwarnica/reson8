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
import com.coherentnetworksolutions.reson8.audio.utils.VolumeScaler;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;


public class WindGaugeChannel implements GaugeChannel {
    private final String name;
    private final Bin bin;
    private final Element filter;
    private final Element volume;
    private Element noiseSrc;
    private double targetIntensity;
    private double currentIntensity = 0.0;
    private double phase = 0.0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private double smoothingRate;
    private double baseGain;
    private double targetVolume = 50;
    private double currentVolumeMidpoint = 50;

    public WindGaugeChannel(SignalBucket signalEndpoint) {
        this.name = signalEndpoint.getName();
        String prefix = name + "::" + System.nanoTime() + "::";
        ProceduralConfig procedureConfig = signalEndpoint.getProcedureConf();
        
        // Config values
        this.targetIntensity = procedureConfig.intensity().orElse(50.0);
        this.smoothingRate = procedureConfig.smoothingrate().orElse(0.02);

        // Capture the static Gain from config to use as our 'ceiling'
        this.baseGain = signalEndpoint.getSoundDefinition().procedural().orElseThrow().gain().orElse(100.0);

        bin = new Bin(prefix + "_bin");

        // 1. Elements
        noiseSrc = ElementFactory.make("audiotestsrc", prefix + "noise");
        noiseSrc.set("wave", 6); // PINK
        noiseSrc.set("is-live", true);
        noiseSrc.set("do-timestamp", true);
                
        //allow audiowsinclimit to mess with timing
        Element queue = ElementFactory.make("queue", prefix+"queue");

        // Ensure we are working with standard stereo early
        Element sourceCaps = ElementFactory.make("capsfilter", prefix + "src_caps");
        sourceCaps.setCaps(Caps.fromString("audio/x-raw, channels=2, channel-mask=(bitmask)0x3"));

        // The 'Character' of the wind (cutoff frequency)
        // filter = ElementFactory.make("audiowsinclimit", prefix + "filter");
        // filter.set("mode", 0); // Low Pass
        filter = ElementFactory.make("audiocheblimit", prefix+"filter");
        // filter.set("cuttoff")

        // The internal VCA (Intensity * BaseGain)
        volume = ElementFactory.make("volume", prefix + "vol");
        
        // INITIAL STATE
        // We set intensity low initially, but it will be ramped by the scheduler
        this.currentIntensity = 0.0; 
        double gsVol = VolumeScaler.humanToGstVolume(targetVolume);
        volume.set("volume", gsVol);

        bin.addMany(noiseSrc, queue, sourceCaps, filter, volume);
        Element.linkMany(noiseSrc, queue, sourceCaps, filter, volume);

        bin.addPad(new GhostPad("src", volume.getStaticPad("src")));
    
        bin.setState(State.READY);
        
        // Start the physics engine
        scheduler.scheduleWithFixedDelay(this::modulateWind, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void modulateWind() {
        // 1. Smooth the intensity (All calculations here in 0-100 scale)
        if (Math.abs(currentIntensity - targetIntensity) > 0.1) { // Adjusted epsilon for 0-100 scale
            currentIntensity += (targetIntensity - currentIntensity) * smoothingRate;
        }

        // 2. Advance Phase (Gust Speed)
        // High intensity (100) = faster gusts.
        // We divide intensity by 100 here just for the 'speed' coefficient.
        double gustFrequency = 0.04;
        double speed = 0.05 + ((currentIntensity / 100.0) * gustFrequency);
        phase += speed;

        // 3. Calculate "Instantaneous" Pressure (The Woosh)
        // currentIntensity (0-100) is our center point.
        // gustWidth is +/- 15% of the current intensity.
        double gustWidth = 0.15 * currentIntensity;
        double oscillation = Math.sin(phase) * gustWidth;

        // finalDrive is our current "wind power" in 0-100 range
        double finalDrive = currentIntensity + oscillation;

        // Safety clamp to ensure we stay within 0-100
        finalDrive = Math.max(0.0, Math.min(100.0, finalDrive));

        // 4. Convert to Physics
        // We stay in 0-100 range.
        // We use a quadratic curve for the internal "perceived" intensity.
        double driveNormalized = finalDrive / 100.0;
        double curve = Math.pow(driveNormalized, 2);

        // Cutoff mapping (Still needs 0.0-1.0 curve)
        double cutoffHz = 40.0 + (curve * 2500.0);
        filter.set("cutoff", cutoffHz);

        // Volume: Calculate what the "Human" volume should be (0-100)
        // baseGain here should be a 0-100 value.
        double gsVol = baseGain * curve;
        double humanVol = VolumeScaler.gstToHumanVolume(gsVol);

        // 5. Apply to GStreamer using the shared Scaler
        // The Scaler provides the Cubic curve for the final output.
        volume.set("volume", gsVol);

        Log.tracef("[%s]-[%s] Intensity(cur/tar): %.1f/%.1f | Drive: %.1f | Cutoff: %.0fHz | Vol: %.3f | GsVol: %.3f", 
            name, System.identityHashCode(this), currentIntensity, targetIntensity, finalDrive, cutoffHz, humanVol, gsVol);
        
    }

    // private void modulateWind() {
    //     // 1. Smooth the intensity
    //     if (Math.abs(currentIntensity - targetIntensity) > 0.001) {
    //         currentIntensity += (targetIntensity - currentIntensity) * smoothingRate;
    //     }

    //     // 2. Advance Phase
    //     double gustFrequency = 0.04;
    //     double speed = 0.05 + (currentIntensity * gustFrequency);
    //     phase += speed;

    //     // 3. Calculate "Instantaneous" Pressure
    //     // Instead of adding currentIntensity + gust, we treat currentIntensity
    //     // as the CENTER and the gust as the VARIANCE.
    //     double gustWidth = 0.15 * currentIntensity; // The 'swing' range
    //     double oscillation = Math.sin(phase) * gustWidth;

    //     // We clamp the final drive strictly between 0 and 1
    //     double finalDrive = currentIntensity + oscillation;
    //     finalDrive = Math.clamp(finalDrive, 0.01, 1.0); // Use Math.min/max if not on Java 21+

    //     // 4. Update the GStreamer Elements
    //     applyPhysics(finalDrive);
    // }

    // private void applyPhysics(double drive) {
    //     // 1. Map Cutoff: 40Hz (low rumble) to ~2540Hz (whistling wind)
    //     double cutoff = 40 + (Math.pow(drive, 2) * 2500);
    //     filter.set("cutoff", cutoff);
        
    //     // 2. Map Volume: baseGain is the absolute MAX this channel should ever be.
    //     // Ensure vcaValue can never exceed baseGain.
    //     double vcaValue = baseGain * Math.pow(drive, 2);
        
    //     // Safety Clamp: GStreamer volume 1.0 is 0dB. Anything over 1.0 is digital gain.
    //     vcaValue = Math.min(vcaValue, baseGain);
        
    //     volume.set("volume", vcaValue);
    //     Log.debugf("applyPhysics([%s]) intensity current: [%s] target: [%s] set cuttoff: [%s], volume: [%s]", drive, currentIntensity, targetIntensity, cutoff, vcaValue);
    // }

    // private void modulateWind() {

    //     // move toward the intensity 'Weather' (Target)
    //     if (Math.abs(currentIntensity - targetIntensity) > 0.001) {
    //         currentIntensity += (targetIntensity - currentIntensity) * smoothingRate;
    //     }

    //     // 2. Advance Phase (Gust Frequency)
    //     // We scale the speed of the woosh by the intensity:
    //     // High intensity = faster gusts.
    //     double gustFrequency = 0.04;
    //     double speed = 0.05 + (currentIntensity * gustFrequency);
    //     phase += speed;

    //     // 3. Calculate "Instantaneous" Pressure (The Woosh)
    //     // We oscillate around currentIntensity.
    //     // We use a bit of Math.sin and some random 'jitter' for character.
    //     double gustFactor = 0.15; // gust intensity expansion is +/- 15% of middle 
    //     double gust = Math.sin(phase) * (currentIntensity * gustFactor); 
    //     double finalDrive = Math.max(0.01, currentIntensity + gust);

    //     Log.tracef("Target Intensity: [%.2f] Smoothed Intensity: [%.2f] Phase: [%.2f] finalDrive ", targetIntensity, currentIntensity, phase, finalDrive);

    //     // 4. Update the GStreamer Elements
    //     applyPhysics(finalDrive);
    // }

    // private void applyPhysics(double drive) {
    //     Log.debugf("[%s] targetIntensity:[%s] applyPhysics([%s])", name, targetIntensity, drive);
    //     // Cutoff frequency maps to 'whistle'
    //     double cutoff = 40 + (Math.pow(drive, 2) * 2500);
    //     filter.set("cutoff", cutoff);

    //     // Volume maps to 'pressure'
    //     // We use baseGain from config as our ceiling
    //     double vcaValue = baseGain * Math.pow(drive, 2);
    //     volume.set("volume", vcaValue);
    //     Log.debugf("Cuttoff -> [%s], volume -> [%s]", cutoff, vcaValue);
    // }

    // private void updateInternalVolume() {
    //     // The "Generator Output" is the combination of its base gain and current intensity
    //     // We use a square or cube curve for volume so it feels more natural
    //     double intensityFactor = Math.pow(currentIntensity, 2); 
    //     volume.set("volume", baseGain * intensityFactor);
    // }

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
        return (double) VolumeScaler.gstToHumanVolume((double) volume.get("volume"));
    }

    // @Override
    // public boolean supportsGain() {return true; }

    // @Override
    // public boolean supportsIntensity() {return true;}

    @Override
    public void setGain(@Min(0) @Max(100) double vol) {
        Log.debugf("setGain([%s]", vol);
        double gsVol = VolumeScaler.humanToGstVolume(vol);
        volume.set("volume", gsVol);
    }

    @Override
    public Element getSrcElement() {
        Log.debugf("my 'src' element is [%s]", bin);
        return bin;
    }
    

    @Override
    public void dispose() {
        scheduler.shutdown();
        bin.setState(State.NULL);

    }

    @Override
    public void setIntensity(@Min(0) @Max(100) double intensity) {
        Log.debugf("setIntensity([%s]) on [%s]", intensity, name);
        targetIntensity = intensity;
    }


}
