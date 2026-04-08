package com.coherentnetworksolutions.reson8.audio.output;


import java.io.IOException;
import java.io.OutputStream;

import org.freedesktop.gstreamer.Element;


public interface OutputChannel {
        
    void start();
    String getChannelName();
    Element getElement();
    void subscribe(OutputStream output) throws IOException;
    
}