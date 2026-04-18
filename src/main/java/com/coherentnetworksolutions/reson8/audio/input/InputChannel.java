package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// ----------------- Input Channel -----------------
public interface InputChannel {
        void start();
        String getChannelName();
        double getGain();
        // boolean supportsGain();
        
        /**
         * @param volume coming out of input channel 0-1, unscaled
         */
        void setGain(@Min(0) @Max(100) double volume);
        // Element getElement();
        Element getSrcElement();
        Caps getCaps();
        // boolean supportsIntensity();
        void dispose();
        void setIntensity(@Min(0) @Max(100) double d);
        double getIntensity();
}