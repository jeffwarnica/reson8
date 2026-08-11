package com.coherentnetworksolutions.reson8.audio.output.to;

import io.quarkus.logging.Log;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;


@Mock
@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "reson8dev.audiopath", stringValue = "silent")
public class MockBrowserSessionManager implements BrowserSessionManager {
    private final BroadcastProcessor<byte[]> processor = BroadcastProcessor.create();

    @Override
    public Multi<byte[]> subscribe() {
        Log.debug(".");
        // Use a small buffer so the first broadcast doesn't vanish
        // if the test is faster than the REST wiring.
        return processor.onOverflow().buffer(10).onItem().transform(b -> b);
    }

    @Override
    public void broadcast(byte[] data) {
        Log.debug(".");
        processor.onNext(data);
    }
}