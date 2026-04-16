package com.coherentnetworksolutions.reson8.rest;

import java.util.List;

import com.coherentnetworksolutions.reson8.audio.sound.WavCache;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalMapRegistry;
import com.coherentnetworksolutions.reson8.signal.SignalEndpoint;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/audio/drop")
public class DropResource {

    @Inject
    Reson8Config config;

    @Inject
    WavCache dropFactory;

    @Inject
    SignalMapRegistry mappingManager;
    
        // Return a list of available drops
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> listDrops() {
        List<String> drops = mappingManager.getEndpoints().entrySet().stream()
            .filter(entry -> {
                SignalEndpoint endpoint = entry.getValue();
                return endpoint.getSoundType() == Reson8Config.SoundType.DROP;
            })
            .map(
                // entry -> new EndpointInfo(entry.getValue().getName(), entry.getValue().getFullSoundPath())
                entry -> entry.getValue().getName()
                )
            .toList();

        Log.debugf("Registered drops: [%s]", drops);
        return drops;
    }

    public record EndpointInfo(String alias, String soundPath) {}
    /*
    *
     * Trigger a one-shot WAV drop in the mixer
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response triggerDrop(DropRequest dropRequest) {
        
        Log.debugf("triggerDrop([%s])", dropRequest.drop);

        SignalEndpoint endpoint = mappingManager.getEndpoint(dropRequest.drop);

        endpoint.trigger();

        return Response.ok().build();
    }

    public static class DropRequest {
        public String drop;
    }
}