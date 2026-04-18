package com.coherentnetworksolutions.reson8.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.rest.AudioControlResource.MixerStateDTO;
import com.coherentnetworksolutions.reson8.audio.input.GaugeChannel;
import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.signal.SignalMapRegistry;
import com.coherentnetworksolutions.reson8.signal.SignalManager;
import io.quarkus.logging.Log;

@Path("/audio/control")
public class AudioControlResource {

    @Inject 
    Mixer mixer;

    @Inject
    SignalMapRegistry mappingManager;

    @Inject
    SignalManager signalManager;

    @Inject
    SignalMapRegistry signalMapRegistry;

    @GET
    @Path("/channels")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ChannelInfo> getChannels() {
        return mappingManager.getBuckets().stream()
            .map( entry -> 
                    new ChannelInfo(entry.getName(),
                        mixer.getInputChannelVolume(entry.getName()), entry.getVolume(),
                        entry.getIntensity(), entry.isDrop())
            )
            .toList();
    }

    @POST
    @Path("/k8s-sync/{active}")
    public void setK8sSync(@PathParam("active") boolean active) {
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
        state.channels = signalMapRegistry.getBuckets().stream().map(ch -> {
            MixerStateDTO.ChannelStateDTO dto = new MixerStateDTO.ChannelStateDTO();
            dto.name = ch.getName();
            dto.mixerVol = mixer.getInputChannelVolume(ch.getName());
            dto.chanVol = ch.getVolume();
            dto.intensity = ch.getIntensity();
            dto.isDrop = ch.isDrop();
            return dto;
        }).toList();

        return state;
    }

    // /**
    //  * GAIN: Controls the volume leaving the source (InputChannel)
    //  */
    // @POST
    // @Path("/gain")
    // @Consumes(MediaType.APPLICATION_JSON)
    // public Response setChannelGain(ChannelVolumeRequest req) {
    //     mappingManager.getBucket(req.channel).setVolume(req.volume);
    //     // mixer.setInputChannelGain(req.channel, req.volume);
    //     return Response.ok().build();
    // }

    /**
     * FADER: Controls the volume at the Mixer Input Pad
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
            mixer.getInputChannel(req.channel).setGain(req.chVol);
        }
        return Response.ok().build();
    }


    @PATCH
    //http://localhost:8090/audio/control/channels/cpu-wind/intensity/0.6
    @Path("/channels/{name}/intensity/{value}")
    public Response setIntensity(@PathParam("name") String name, @PathParam("value") double value) {
        Log.info("setIntensity()");
        InputChannel channel = mixer.getInputChannel(name);
        if (channel instanceof GaugeChannel gauge) {
            gauge.setIntensity(value);
            return Response.ok().build();
        }
        return Response.status(404).build();
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

        public ChannelInfo(String name, double mixVol, double chVol, double intensity, boolean isDrop) {
            this.name = name;
            this.mixVol = mixVol;
            this.chVol = chVol;
            this.intensity = intensity;
            this.isDrop = isDrop;

        }
    }

    public class MixerStateDTO {
        public double masterVolume;
        public boolean k8sSyncActive;
        public List<ChannelStateDTO> channels;

        public static class ChannelStateDTO {
            public String name;
            public double chanVol;
            public double mixerVol; // The actual fader level
            public double intensity; // The K8s signal level (0.0 - 1.0)
            public boolean isDrop; // To tell the UI to show the "Play" button
        }
    }
}