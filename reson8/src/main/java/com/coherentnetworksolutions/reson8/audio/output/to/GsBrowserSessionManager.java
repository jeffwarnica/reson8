package com.coherentnetworksolutions.reson8.audio.output.to;

import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;

@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "reson8dev.audiopath", stringValue = "gs")
public class GsBrowserSessionManager implements BrowserSessionManager {

    // The central hub that fans out to all browsers
    private final BroadcastProcessor<byte[]> processor = BroadcastProcessor.create();

    // The sanitized, backpressure-aware stream
    private final Multi<byte[]> liveStream = processor.toHotStream()
                                .onOverflow().drop(); // Essential for live audio
                                

    public void broadcast(byte[] data) {
        // We push to the processor which handles the actual fan-out
        synchronized (processor) {
            processor.onNext(data);
        }
    }

    public Multi<byte[]> subscribe() {
        return liveStream;
    }

}