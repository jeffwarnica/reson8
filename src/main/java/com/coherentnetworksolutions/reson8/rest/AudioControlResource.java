package com.coherentnetworksolutions.reson8.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;

@Path("/audio/control")
public class AudioControlResource {


    @Inject 
    Mixer mixer;

    @GET
    @Path("/channels")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ChannelInfo> getChannels() {
        return mixer.getInputChannels().values().stream()
                .map(ch -> new ChannelInfo(ch.getChannelName(), ch.supportsGain()))
                .toList();
    }

    /**
     * GAIN: Controls the volume at the source (InputChannel)
     */
    @POST
    @Path("/gain")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setChannelGain(ChannelVolumeRequest req) {
        // This targets the source element (e.g., the pink noise generator or file source)
        mixer.setChannelGain(req.channel, req.volume);
        return Response.ok().build();
    }

    /**
     * FADER: Controls the volume at the Mixer Input Pad
     */
    @POST
    @Path("/fader")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setChannelFader(ChannelVolumeRequest req) {
        // This targets the audiomixer sink pad
        mixer.setInputChannelVolume(req.channel, req.volume);
        return Response.ok().build();
    }

    public static class ChannelVolumeRequest {
        public String channel;
        public double volume;
    }

    public static class ChannelInfo {
        public String name;
        public boolean supportsGain;

        public ChannelInfo(String name, boolean supportsGain) {
            this.name = name;
            this.supportsGain = supportsGain;
        }
    }
}