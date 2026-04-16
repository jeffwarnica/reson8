package com.coherentnetworksolutions.reson8.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.GaugeChannel;
import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.signal.SignalMapRegistry;
import io.quarkus.logging.Log;

@Path("/audio/control")
public class AudioControlResource {

    @Inject 
    Mixer mixer;

    @Inject
    SignalMapRegistry mappingManager;

    @GET
    @Path("/channels")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ChannelInfo> getChannels() {
        return mappingManager.getEndpoints().entrySet().stream()
            .map( entry -> 
                    new ChannelInfo(entry.getValue().getName(),entry.getValue().getVolume(),
                        entry.getValue().getInputChannel().supportsIntensity(), entry.getValue().getIntensity())
            )
            .toList();
    // public List<ChannelInfo> getChannels() {
        // return mappingManager.getEndpoints();
        // return mixer.getInputChannels().values().stream()
        //         .map(ch -> new ChannelInfo(ch.getChannelName(), 
        //             mixer.getInputChannelVolume(ch.getChannelName()), 
        //             mixer.getInputChannelGain(ch.getChannelName()),
        //             ch.supportsGain(), ch.supportsIntensity()))
        //         .toList();
    }

    /**
     * GAIN: Controls the volume at the source (InputChannel)
     */
    @POST
    @Path("/gain")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setChannelGain(ChannelVolumeRequest req) {
        mappingManager.getEndpoint(req.channel).setVolume(req.volume);
        // mixer.setInputChannelGain(req.channel, req.volume);
        return Response.ok().build();
    }

    /**
     * FADER: Controls the volume at the Mixer Input Pad
     */
    @POST
    @Path("/fader")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setChannelFader(ChannelVolumeRequest req) {
        Log.debugf("[%s].setVolume([%s])", req.channel, req.volume);
        mixer.setInputChannelVolume(req.channel, req.volume);
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
        public double volume;
    }

    public static class ChannelInfo {
        public String name;
        public boolean supportsIntensity;
        public double volume;
        public double intensity;

        public ChannelInfo(String name, double volume, boolean supportsIntensity, double intensity) {
            this.name = name;
            this.volume = volume;
            this.supportsIntensity = supportsIntensity;
            this.intensity = intensity;

        }
    }
}