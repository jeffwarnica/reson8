package com.coherentnetworksolutions.reson8.audio.utils;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VolumeScaler {

    /**
     * Maps a UI slider value (0.0 to 1.0) to a GStreamer linear amplitude.
     * Uses a cubic curve (x^3) which provides a very natural feel for audio.
     */
    public double uiToGstVolume(double uiValue) {
        if (uiValue <= 0) return 0.0;
        if (uiValue >= 1.0) return 1.0;
        // Cubic scaling: Slider at 0.5 results in 0.125 amplitude (~ -18dB)
        return Math.pow(uiValue, 3);
    }
    
    public double gstToUiVolume(double gstValue) {
        if (gstValue <= 0) return 0.0;
        if (gstValue >= 1.0) return 1.0;
        
        // Inverse of cubic scaling (x^3) is the cube root (∛x)
        return Math.cbrt(gstValue);
    }
}
