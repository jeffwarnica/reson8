package com.coherentnetworksolutions.reson8.audio.output.to;

import io.smallrye.mutiny.Multi;

// import java.io.OutputStream;

//TODO: This may not need multiple implementations.
public interface BrowserSessionManager {
    public Multi<byte[]> subscribe();
    public void broadcast(byte[] data);
}