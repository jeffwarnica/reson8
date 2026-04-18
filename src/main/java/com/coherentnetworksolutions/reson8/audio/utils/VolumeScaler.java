package com.coherentnetworksolutions.reson8.audio.utils;

import javax.management.RuntimeErrorException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// @ApplicationScoped
public class VolumeScaler {

    /**
     * Maps a human % value (0.0 to 100.0) to a GStreamer linear amplitude.
     * Uses a cubic curve (x^3) which provides a very natural feel for audio.
     */
    public static double humanToGstVolume(/*@Min(0) @Max(100) */ double humanValue) {
        if (humanValue <= 0)
            return 0.0; // Safety first
        if (humanValue >= 100.0)
            return 1.0;
        return Math.pow(humanValue/100, 3);
    }
    
    public static double gstToHumanVolume(double gstValue) {
        if (gstValue > 1) {
            return 100.0;
        }
        if (gstValue <= 0) {
            return 0.0;
        }
        
        // Inverse of cubic scaling (x^3) is the cube root (∛x)
        return Math.cbrt(gstValue)*100;
    }
}
