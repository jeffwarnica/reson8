package com.coherentnetworksolutions.reson8.rest;

import java.util.List;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;
import com.coherentnetworksolutions.reson8.signal.SignalManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/audio/drop")
public class DropResource {

    @Inject
    Reson8Config config;

    @Inject
    SignalManager signalManager;
    
        // Return a list of available drops
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> listDrops() {
        List<String> drops = signalManager.getSignalBuckets().stream()
            .filter(entry -> {
                return entry.getSoundType() == Reson8Config.SoundType.DROP;
            })
            .map(
                // entry -> new EndpointInfo(entry.getValue().getName(), entry.getValue().getFullSoundPath())
                entry -> entry.getName()
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

        if (dropRequest.drop == null || dropRequest.drop.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"drop name is required\"}")
                    .build();
        }

        SignalBucket endpoint = signalManager.getSignalBucket(dropRequest.drop);
        if (endpoint == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Drop not found: " + dropRequest.drop + "\"}")
                    .build();
        }

        endpoint.trigger();

        return Response.ok().build();
    }

    @SuppressFBWarnings(value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Field is written by Jackson via reflection for JSON deserialization")
    public static class DropRequest {
        public String drop;
    }
}