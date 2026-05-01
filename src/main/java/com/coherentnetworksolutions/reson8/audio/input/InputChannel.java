package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// ----------------- Input Channel -----------------
public interface InputChannel {
        void start();
        String getChannelName();
        double getCeiling();
        // boolean supportsGain();
        
        /**
         * Sets the output ceiling for this channel (0–100, human scale).
         * Capped to [0, 100] and converted to the GStreamer 0–1 range internally via
         * {@link com.coherentnetworksolutions.reson8.audio.utils.map.VolumeScaler}.
         *
         * @param ceiling output ceiling, 0–100
         */
        void setCeiling(@Min(0) @Max(100) double ceiling);
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
         * Gets the target intensity (what was set via setTargetIntensity)
         * @return target intensity 0-100
         */
        double getTargetIntensity();
        
        /**
         * Gets the current/observed intensity (may differ from target)
         * @return current intensity 0-100
         */
        double getCurrentIntensity();
        
}