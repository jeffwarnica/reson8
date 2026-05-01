package com.coherentnetworksolutions.reson8.audio.input;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.State;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import com.coherentnetworksolutions.reson8.audio.utils.map.VolumeScaler;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;

public class WindGaugeChannel extends BaseInputChannel implements GaugeChannel {

    private final Bin bin;
    private final Element filter;
    private final Element volume;
    private Element noiseSrc;
    private double phase = 0.0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final double smoothingRate;
    private final double outputScale;
    private final GsToolkit toolkit;
    private final String capsString;

    public WindGaugeChannel(SignalBucket signalBucket, GsToolkit toolkit) {
        
        super(
               signalBucket.getName(),
               signalBucket.getProcedureConf().intensity(),
                0.0);
        this.toolkit = toolkit;
        this.capsString = Mixer.CAPS;
        ProceduralConfig procedureConfig =signalBucket.getProcedureConf();
        this.smoothingRate = procedureConfig.smoothingrate();
        this.outputScale = signalBucket.getSoundDefinition().procedural().orElseThrow().outputScale();

        String prefix = getChannelName() + "::" + System.nanoTime() + "::";

        bin = toolkit.createBin(prefix + "bin");

        noiseSrc = toolkit.makeElement("audiotestsrc", prefix + "noise");
        toolkit.setElementProperty(noiseSrc, "wave", 6); // PINK
        toolkit.setElementProperty(noiseSrc, "is-live", true);
        toolkit.setElementProperty(noiseSrc, "do-timestamp", true);

        Element queue = toolkit.makeElement("queue", prefix + "queue");

        Element sourceCaps = toolkit.makeElement("capsfilter", prefix + "src_caps");
        toolkit.setElementProperty(sourceCaps, "caps", toolkit.capsFromString("audio/x-raw, channels=2, channel-mask=(bitmask)0x3"));

        filter = toolkit.makeElement("audiocheblimit", prefix + "filter");

        volume = toolkit.makeElement("volume", prefix + "vol");

        toolkit.addMany(bin, noiseSrc, queue, sourceCaps, filter, volume);
        toolkit.linkMany(noiseSrc, queue, sourceCaps, filter, volume);

        toolkit.addPad(bin, toolkit.createGhostPad("src", toolkit.getStaticPad(volume, "src")));

        toolkit.setElementState(bin, State.READY);

        scheduler.scheduleWithFixedDelay(this::modulateWind, 0, 500, TimeUnit.MILLISECONDS);
    }

    
    private void modulateWind() {
        stepSmoothTowardTarget(smoothingRate, 0.1);
        double currentIntensity = getCurrentIntensity();
        double gustFrequency = 0.04;
        double speed = 0.05 + ((currentIntensity / 100.0) * gustFrequency);
        phase += speed;

        double gustWidth = 0.15 * currentIntensity;
        double oscillation = Math.sin(phase) * gustWidth;
        
        double finalDrive = currentIntensity + oscillation;
        finalDrive = Math.max(0.0, Math.min(100.0, finalDrive));

        double driveNormalized = finalDrive / 100.0;
        double curve = Math.pow(driveNormalized, 2);

        double cutoffHz = 40.0 + (curve * 2500.0);
        toolkit.setElementProperty(filter, "cutoff", cutoffHz);

        double gsCeiling = VolumeScaler.humanToGsVolume(getCeiling());

        // clamp to [0,1] before setting GStreamer volume
        double gsVol = Math.max(0.0, Math.min(gsCeiling, outputScale * curve));
        toolkit.setElementProperty(volume, "volume", gsVol);

        Log.tracef("[%s]-[%s] Intensity(cur/tar): %.1f/%.1f | Drive: %.1f | Cutoff: %.0fHz | GsVol: %.3f",
                getChannelName(), System.identityHashCode(this), currentIntensity, getTargetIntensity(), finalDrive, cutoffHz, gsVol);
    }
    

    @Override
    public String getCapsString() {
        return capsString;
    }

    @Override
    public void start() {
    }

    @Override
    public Object getSrcElement() {
        Log.debugf("my 'src' element is [%s]", bin);
        return bin;
    }

    @Override
    public void dispose() {
        scheduler.shutdown();
        toolkit.setElementState(bin, State.NULL);
    }

}
