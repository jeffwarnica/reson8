package com.coherentnetworksolutions.reson8.rest;


import java.io.IOException;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.utils.WavHeaderUtils;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.GET;

@Singleton
@Path("/audio")
public class AudioStreamResource {

    // public final Mixer mixer = new Mixer();
    @Inject
    public Mixer mixer;

    @GET
    @Path("/stream")
    @Produces("audio/wav")
    public Response getStream() {
        StreamingOutput stream = output -> {
        try {
            // 1. Generate the header from our new Utility
            byte[] header = WavHeaderUtils.createPcmHeader(48000, 16, 2);
            
            // 2. Write and IMMEDIATELY flush to the browser
            output.write(header);
            output.flush(); 

            Log.info("WAV Header sent to browser. Subscribing to master stream...");

            // 3. This is the blocking call that waits for GStreamer buffers
            mixer.getMasterOutput().subscribe(output);
            
        } catch (IOException e) {
            Log.warn("Browser disconnected during stream setup: " + e.getMessage());
        }
    };


        return Response.ok(stream)
                .header("Content-Type", "audio/wav")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .header("Connection", "keep-alive")
                .build();
    }

   
}