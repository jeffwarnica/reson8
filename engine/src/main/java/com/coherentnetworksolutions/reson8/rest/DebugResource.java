package com.coherentnetworksolutions.reson8.rest;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/api/debug")
public class DebugResource {

    @Inject
    Mixer mixer;

    @GET
    @Path("/mixer-dump")
    @Produces(MediaType.TEXT_PLAIN)
    public String getMixerDump() {
        // This calls the method we designed earlier
        return mixer.dumpMixerState();
    }
}