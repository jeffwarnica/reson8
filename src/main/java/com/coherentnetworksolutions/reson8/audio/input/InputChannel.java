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
        
        /**
         * Sets the target intensity (what user/k8s wants)
         * @param d target intensity 0-100
         */
        void setTargetIntensity(@Min(0) @Max(100) double d);
        
        /**
         * Gets the target intensity (what was set via setIntensity)
         * @return target intensity 0-100
         */
        double getTargetIntensity();
        
        /**
         * Gets the current/observed intensity (may differ from target)
         * @return current intensity 0-100
         */
        double getCurrentIntensity();
        
}