package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Element;

// ----------------- Input Channel -----------------
public interface InputChannel {
        void start();
        String getChannelName();
        double getGain();
        boolean supportsGain();
        
        /**
         * @param volume 0-1, unscaled
         */
        void setGain(double volume);
        // Element getElement();
        Element getSrcElement();
}