package com.coherentnetworksolutions.reson8.audio.output.from;

import org.freedesktop.gstreamer.Element;
import io.smallrye.mutiny.Multi;

public interface MixerOutputToClientManagerChannel {
        
    String getChannelName();
    Element getElement();
    Multi<byte[]> subscribe();
    
}