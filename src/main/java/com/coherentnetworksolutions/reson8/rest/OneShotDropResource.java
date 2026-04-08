package com.coherentnetworksolutions.reson8.rest;

import java.util.List;

import com.coherentnetworksolutions.reson8.audio.factory.OneShotDropFactory;
import com.coherentnetworksolutions.reson8.controllers.DropController;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/audio/drop")
public class OneShotDropResource {

    @Inject
    OneShotDropFactory oneShotDropFactory;
    

    @Inject
    DropController dropController;


    // Return a list of available WAV files from resources/sounds/
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> listDrops() {
        return dropController.getDrops();
    }
    /*
    *
     * Trigger a one-shot WAV drop in the mixer
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response triggerDrop(DropRequest req) {
        if (req.dropName == null || req.dropName.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("dropName is required").build();
        }

        oneShotDropFactory.playDrop(req.dropName, 1.0);

        return Response.ok().build();
    }

    public static class DropRequest {
        public String dropName;
        public double volume = 1.0;
    }
}