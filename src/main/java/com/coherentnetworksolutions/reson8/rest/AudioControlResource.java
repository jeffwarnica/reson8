package com.coherentnetworksolutions.reson8.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.utils.map.SignalCurveMap;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;
import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.signal.SignalManager;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;

@Path("/audio/control")
public class AudioControlResource {

    @Inject 
    Mixer mixer;

    @Inject
    SignalManager signalManager;

    @Inject
    EventBus eventBus;


    private static final int CURVE_SAMPLE_COUNT = 40;

    /** Samples a curve at {@value #CURVE_SAMPLE_COUNT} evenly-spaced points across its input domain. */
    private List<double[]> sampleCurve(SignalCurveMap curve) {
        double min = curve.minInput();
        double max = curve.maxInput();
        List<double[]> samples = new ArrayList<>(CURVE_SAMPLE_COUNT);
        for (int i = 0; i < CURVE_SAMPLE_COUNT; i++) {
            double x = min + (max - min) * i / (CURVE_SAMPLE_COUNT - 1);
            samples.add(new double[]{x, curve.map(x)});
        }
        return samples;
    }

    /** Like {@link #sampleCurve} but uses {@code mapUnclamped} — exposes spline oscillation. */
    private List<double[]> sampleCurveUnclamped(SignalCurveMap curve) {
        double min = curve.minInput();
        double max = curve.maxInput();
        List<double[]> samples = new ArrayList<>(CURVE_SAMPLE_COUNT);
        for (int i = 0; i < CURVE_SAMPLE_COUNT; i++) {
            double x = min + (max - min) * i / (CURVE_SAMPLE_COUNT - 1);
            samples.add(new double[]{x, curve.mapUnclamped(x)});
        }
        return samples;
    }

    @GET
    @Path("/channels")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ChannelStateDTO> getChannels() {
        return signalManager.getSignalBuckets().stream().map(ch -> {
            ChannelStateDTO dto = new ChannelStateDTO();
            dto.name = ch.getName();
            dto.mixerVol = mixer.getInputChannelVolume(ch.getName());
            dto.chanVol = ch.getVolume();
            dto.targetIntensity = ch.getIntensity();
            dto.currentIntensity = ch.getCurrentIntensity();
            dto.isDrop = ch.isDrop();
            dto.sourceType = ch.getSourceType();
            dto.soundName = ch.getSoundDefinition().name();
            dto.soundType = ch.getSoundDefinition().soundType().name();
            SignalCurveMap curve = ch.getCurve();
            dto.curveSamples = sampleCurve(curve);
            dto.curveRawSamples = sampleCurveUnclamped(curve);
            dto.curveMinInput = curve.minInput();
            dto.curveMaxInput = curve.maxInput();
            return dto;
        }).toList();
    }

        // return signalManager.getSignalBuckets().stream()
        //     .map( entry -> 
        //             new ChannelInfo(entry.getName(),
        //                 mixer.getInputChannelVolume(entry.getName()), entry.getVolume(),
        //                 entry.getIntensity(), entry.isDrop(), entry.getInputChannel().getClass().getName())
        //     )
        //     .toList();
    
    @POST
    @Path("/k8s-sync/{active}")
    public void setK8sSync(@PathParam("active") boolean active) {
        
        eventBus.publish("k8s-sync-enable", active);
        signalManager.setK8sSyncEnabled( active );
        Log.info("K8s Signal Sync set to: " + active);
    }

    @GET
    @Path("/state")
    public MixerStateDTO getCurrentState() {
        MixerStateDTO state = new MixerStateDTO();

        // 1. Global Engine State
        state.masterVolume = mixer.getMasterVolume();
        state.k8sSyncActive = signalManager.isK8sSyncEnabled();

        // 2. Map every channel to its current metrics
        state.channels = signalManager.getSignalBuckets().stream().map(ch -> {
            ChannelStateDTO dto = new ChannelStateDTO();
            dto.name = ch.getName();
            dto.mixerVol = mixer.getInputChannelVolume(ch.getName());
            dto.chanVol = ch.getVolume();
            dto.targetIntensity = ch.getIntensity();
            dto.currentIntensity = ch.getCurrentIntensity();
            dto.isDrop = ch.isDrop();
            dto.sourceType = ch.getSourceType();
            return dto;
        }).toList();

        return state;
    }

    /**
     * FADER:
     *  Controls the volume at the Mixer Input Pad. This should be used to control
     *  overall mix of the channels, rather then the "intensity" of a given input.
     */
    @POST
    @Path("/fader")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setChannelFader(ChannelVolumeRequest req) {
        Log.debugf("[%s] chVol -> [%s], mixVol -> [%s]", req.channel, req.chVol, req.mixVol);
        if (req.chVol >= 0) {
            mixer.setInputChannelVolume(req.channel, req.mixVol);
        }
        if (req.mixVol >= 0) {
            mixer.getInputChannel(req.channel).setCeiling(req.chVol);
        }
        return Response.ok().build();
    }


    /**
     * Sets the desired signal intensity (0–100) for a channel: the control-plane / operator
     * target that generators and k8s sync converge toward. The eased value shown in the UI
     * is {@link ChannelStateDTO#currentIntensity}.
     */
    @PATCH
    @Path("/channels/{name}/target-intensity")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setTargetIntensity(@PathParam("name") String name, TargetIntensityPatch body) {
        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"JSON body required\"}").build();
        }
        Log.infof("PATCH targetIntensity channel=[%s] targetIntensity=[%s]", name, body.targetIntensity);
        InputChannel channel = mixer.getInputChannel(name);
        channel.setTargetIntensity(body.targetIntensity);
        return Response.ok().build();
    }

    /** Request body for {@link #setTargetIntensity(String, TargetIntensityPatch)}. */
    public static class TargetIntensityPatch {
        /** Desired signal level (0–100); same semantics as {@link ChannelStateDTO#targetIntensity}. */
        public double targetIntensity;
    }

    /**
     * Feeds a raw (unscaled) metric value through the channel's {@code SignalCurveMap} and sets
     * the result as the target intensity, bypassing the k8s-sync gate. Intended for manual
     * simulation when k8s sync is paused.
     *
     * @return JSON {@code {"mappedIntensity": <0-100>}} so the caller can show the translated value.
     */
    @POST
    @Path("/channels/{name}/simulate-metric")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response simulateRawMetric(@PathParam("name") String name, SimulateMetricRequest body) {
        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"JSON body required\"}").build();
        }
        var bucket = signalManager.getSignalBucket(name);
        if (bucket == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("{\"error\":\"Channel not found\"}").build();
        }
        double mapped = bucket.simulateRawMetric(body.rawMetricValue);
        return Response.ok("{\"mappedIntensity\":" + mapped + "}").build();
    }

    /** Request body for {@link #simulateRawMetric(String, SimulateMetricRequest)}. */
    public static class SimulateMetricRequest {
        /** Raw (unscaled) metric value in the same units as the Prometheus query (e.g. crashes/min). */
        public double rawMetricValue;
    }


    public static class ChannelVolumeRequest {
        public String channel;
        public double mixVol;
        public double chVol;
    }

    public static class ChannelInfo {
        public String name;
        public boolean supportsIntensity;
        public double mixVol;
        public double chVol;
        public double intensity;
        public boolean isDrop;
        public String sourceType; // Add this!

        public ChannelInfo(String name, double mixVol, double chVol, double intensity, boolean isDrop, String sourceType) {
            this.name = name;
            this.mixVol = mixVol;
            this.chVol = chVol;
            this.intensity = intensity;
            this.isDrop = isDrop;
            this.sourceType = sourceType;

        }
    }

    public class MixerStateDTO {
        public double masterVolume;
        public boolean k8sSyncActive;
        public List<ChannelStateDTO> channels;
    }

    public static class ChannelStateDTO {
        public String name;
        public double chanVol;
        public double mixerVol; // The actual fader level (0-100)
        public double targetIntensity; // What user/k8s wants to set (0-100)
        public double currentIntensity; // What is actually observed (0-100)
        public boolean isDrop; // To tell the UI to show the "Play" button
        public SourceType sourceType;
        public String soundName;
        public String soundType;
        /** Clamped samples [{x, y}] — what actually drives intensity. Populated on initial load only. */
        public List<double[]> curveSamples;
        /** Unclamped raw spline samples [{x, y}] — exposes oscillation/overshoot for diagnostics. */
        public List<double[]> curveRawSamples;
        /** Minimum value of the curve's input domain (k8s metric units). */
        public double curveMinInput;
        /** Maximum value of the curve's input domain (k8s metric units). */
        public double curveMaxInput;
    }
    
}