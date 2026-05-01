package com.coherentnetworksolutions.reson8.rest;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.output.to.BrowserSessionManager;
import com.coherentnetworksolutions.reson8.audio.utils.WavHeaderUtils;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.GET;

// @Singleton rather than @ApplicationScoped: this resource is on the hot streaming
// path and has no instance state; CDI proxying is intentionally bypassed.
// Do NOT add interceptor annotations (@RolesAllowed, @Transactional, etc.) here —
// they will have no effect on a @Singleton bean.
@Singleton
@Path("/audio")
public class AudioStreamResource {

    @Inject
    public Mixer mixer;
    @Inject
    private BrowserSessionManager sessionManager;

    @GET
    @Path("/stream")
    @Produces("audio/wav")
    public Multi<byte[]> getStream() {
        Log.debug(".");
        Log.infof("My BrowserSessionManager is type [%s]", sessionManager.getClass());
        byte[] header = WavHeaderUtils.createPcmHeader(48000, 16, 2);
    
        // Concatenate the one-time header with the live stream of audio buffers
        return Multi.createBy().merging()
            .streams(
                Multi.createFrom().items(header),
                sessionManager.subscribe()
            );
        
    }

   
}