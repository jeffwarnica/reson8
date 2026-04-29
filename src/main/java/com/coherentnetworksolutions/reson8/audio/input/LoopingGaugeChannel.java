package com.coherentnetworksolutions.reson8.audio.input;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.PlayBin;

import com.coherentnetworksolutions.reson8.audio.providers.GstToolkit;
import com.coherentnetworksolutions.reson8.audio.utils.map.VolumeScaler;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class LoopingGaugeChannel extends BaseInputChannel implements GaugeChannel {

    private final Bin bin;
    private final Element filter;
    private final Element volume;
    private PlayBin playBin;
    private String gsUri;
    /** Linear multiplier on procedural VCA (0–1), from loop config output-scale. */
    private final double outputScale;
    private final GstToolkit toolkit;
    private final Caps caps;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Double smoothingRate;
    /** Match {@link WindGaugeChannel} default easing when loop config has no smoothing. */
    
    private static final double SMOOTHING_EPSILON = 0.1;

    public LoopingGaugeChannel(SignalBucket signalBucket, Reson8Config config, GstToolkit toolkit) {
        super(signalBucket.getName(), 0.0, 0.0);
        this.toolkit = toolkit;
        this.caps = toolkit.capsFromString("audio/x-raw");
        this.bin = toolkit.createBin(getChannelName() + "_bin");
        this.smoothingRate = signalBucket.getSoundDefinition().loop().get().smoothingrate();
        

        Log.debug("Past createBin");

        String prefix = getChannelName() + "::" + System.nanoTime() + "::";

        playBin = toolkit.createPlayBin(prefix + "playbin");

        Log.debug("About to get file path");

        String filePath = signalBucket.getSoundDefinition().loop().map(l -> l.filename()).orElseThrow();
        gsUri = Path.of(config.audioPath()).resolve(filePath).toAbsolutePath().toUri().toString();
        toolkit.setElementProperty(playBin, "uri", gsUri);

        Bin sinkBin = toolkit.createBin(prefix + "sink_bin");
        filter = toolkit.makeElement("audiocheblimit", prefix + "filter");
        toolkit.setElementProperty(filter, "mode", 0);

        Log.debug("About to create volume");

        volume = toolkit.makeElement("volume", prefix + "vol");
        double initialScale = signalBucket.getSoundDefinition().loop().orElseThrow().outputScale();
        this.outputScale = initialScale;
        toolkit.setElementProperty(volume, "volume", initialScale * 1.0);

        Element endPoint = toolkit.makeElement("identity", prefix + "end");

        toolkit.addMany(sinkBin, filter, volume, endPoint);
        toolkit.linkMany(filter, volume, endPoint);

        toolkit.addPad(sinkBin, toolkit.createGhostPad("sink", filter.getStaticPad("sink")));

        toolkit.setElementProperty(playBin, "audio-sink", sinkBin);

        Log.debug("About to connect loop listener");
        toolkit.connectAboutToFinish(playBin, (pb) -> {
            Log.debugf("[%s] ABOUT_TO_FINISH. Looping file...", getChannelName());
            toolkit.setElementProperty(pb, "uri", gsUri);
        });

        toolkit.addElementToBin(bin, playBin);

        toolkit.addPad(bin, toolkit.createGhostPad("src", endPoint.getStaticPad("src")));

        toolkit.setElementState(bin, State.READY);

        scheduler.scheduleWithFixedDelay(this::intensityTick, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void intensityTick() {
        stepSmoothTowardTarget(smoothingRate, SMOOTHING_EPSILON);
        applyLoopPhysics(getCurrentIntensity());
    }

    /** Maps smoothed intensity (0–100) to filter and linear volume. */
    private void applyLoopPhysics(double levelPercent) {
        double norm = levelPercent / 100.0;
        double curve = Math.pow(norm, 2);

        double cutoff = 600.0 + (curve * 11400.0);
        filter.set("cutoff", cutoff);

        double ripple = 0.5 + (norm * 9.);
        toolkit.setElementProperty(filter, "ripple", ripple);

        double vcaValue = 0.1 + (norm * 0.9);
        double gstLinear = vcaValue * outputScale;
        toolkit.setElementProperty(volume, "volume", gstLinear);

        Log.tracef("[%s] Water flow intensity: %.1f%% (Cutoff: %.0fHz, Ripple: %.1f, Vol: %.2f)",
                getChannelName(), levelPercent, cutoff, ripple, vcaValue);
    }

    @Override
    public Caps getCaps() {
        return caps;
    }

    @Override
    public Element getSrcElement() {
        return bin;
    }

    @Override
    public void setCeiling(@Min(0) @Max(100) double vol) {
        double gsVol = VolumeScaler.humanToGstVolume(vol);
        toolkit.setElementProperty(volume, "volume", gsVol);
    }

    @Override
    public void start() {
        toolkit.setElementState(bin, State.PLAYING);
        Log.debug("tried to setState(State.PLAYING)");
    }

    @Override
    public double getCeiling() {
        return VolumeScaler.gstToHumanVolume((double) toolkit.getElementProperty(volume, "volume"));
    }

    @Override
    public void dispose() {
        scheduler.shutdown();
        toolkit.setElementState(playBin, State.NULL);
        toolkit.setElementState(bin, State.NULL);
    }
}
