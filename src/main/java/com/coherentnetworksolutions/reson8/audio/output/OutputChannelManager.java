package com.coherentnetworksolutions.reson8.audio.output;

import java.io.OutputStream;

public interface OutputChannelManager {
    public void subscribe(OutputStream os);
    public void broadcast(byte[] data);
}