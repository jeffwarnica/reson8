package com.coherentnetworksolutions.reson8.audio.input;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.State;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.providers.GstToolkit;
import com.coherentnetworksolutions.reson8.audio.utils.map.VolumeScaler;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class WindGaugeChannel extends BaseInputChannel implements GaugeChannel {

    private final Bin bin;
    private final Element filter;
    private final Element volume;
    private Element noiseSrc;
    private double phase = 0.0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final double smoothingRate;
    private final double baseGain;
    private final double targetVolume = 50;
    private final GstToolkit toolkit;
    private final Caps caps;// = Caps.fromString(Mixer.CAPS);

    public WindGaugeChannel(SignalBucket signalBucket, GstToolkit toolkit) {
        
        super(
               signalBucket.getName(),
               signalBucket.getProcedureConf().intensity(),
                0.0);
        this.toolkit = toolkit;
        this.caps = toolkit.capsFromString(Mixer.CAPS);
        ProceduralConfig procedureConfig =signalBucket.getProcedureConf();
        this.smoothingRate = procedureConfig.smoothingrate();
        this.baseGain = signalBucket.getSoundDefinition().procedural().orElseThrow().gain();

        String prefix = getChannelName() + "::" + System.nanoTime() + "::";

        bin = toolkit.createBin(prefix + "bin");

        noiseSrc = toolkit.makeElement("audiotestsrc", prefix + "noise");
        toolkit.setElementProperty(noiseSrc, "wave", 6); // PINK
        toolkit.setElementProperty(noiseSrc, "is-live", true);
        toolkit.setElementProperty(noiseSrc, "do-timestamp", true);

        Element queue = toolkit.makeElement("queue", prefix + "queue");

        Element sourceCaps = toolkit.makeElement("capsfilter", prefix + "src_caps");
        sourceCaps.setCaps(toolkit.capsFromString("audio/x-raw, channels=2, channel-mask=(bitmask)0x3"));

        filter = toolkit.makeElement("audiocheblimit", prefix + "filter");

        volume = toolkit.makeElement("volume", prefix + "vol");

        double gsVol = VolumeScaler.humanToGstVolume(targetVolume);
        toolkit.setElementProperty(volume, "volume", gsVol);

        toolkit.addMany(bin, noiseSrc, queue, sourceCaps, filter, volume);
        toolkit.linkMany(noiseSrc, queue, sourceCaps, filter, volume);

        toolkit.addPad(bin, toolkit.createGhostPad("src", volume.getStaticPad("src")));

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

        double gsVol = baseGain * curve;
        double humanVol = VolumeScaler.gstToHumanVolume(gsVol);

        toolkit.setElementProperty(volume, "volume", gsVol);

        Log.tracef("[%s]-[%s] Intensity(cur/tar): %.1f/%.1f | Drive: %.1f | Cutoff: %.0fHz | Vol: %.3f | GsVol: %.3f",
                getChannelName(), System.identityHashCode(this), currentIntensity, getTargetIntensity(), finalDrive, cutoffHz, humanVol, gsVol);
    }

    @Override
    public Caps getCaps() {
        return caps;
    }

    @Override
    public void start() {
    }

    @Override
    public double getGain() {
        return (double) VolumeScaler.gstToHumanVolume((double) toolkit.getElementProperty(volume, "volume"));
    }

    @Override
    public void setGain(@Min(0) @Max(100) double vol) {
        Log.debugf("setGain([%s]", vol);
        double gsVol = VolumeScaler.humanToGstVolume(vol);
        toolkit.setElementProperty(volume, "volume", gsVol);
    }

    @Override
    public Element getSrcElement() {
        Log.debugf("my 'src' element is [%s]", bin);
        return bin;
    }

    @Override
    public void dispose() {
        scheduler.shutdown();
        toolkit.setElementState(bin, State.NULL);
    }

}
