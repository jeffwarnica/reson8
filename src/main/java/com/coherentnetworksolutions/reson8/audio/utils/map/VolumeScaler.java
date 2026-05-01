package com.coherentnetworksolutions.reson8.audio.utils.map;

import io.quarkus.logging.Log;

public class VolumeScaler {

    /**
     * Maps a human % value (0.0 to 100.0) to a GStreamer linear amplitude.
     * Uses a cubic curve (x^3) which provides a very natural feel for audio.
     */
    public static double humanToGsVolume(double humanValue) {
        if (humanValue <= 0)
            return 0.0; // Safety first
        if (humanValue >= 100.0)
            return 1.0;
        return Math.pow(humanValue/100, 3);
    }
    
    public static double gsToHumanVolume(double gsValue) {
        if (gsValue > 1) {
            Log.error("GStreamer volume is greater than 1.0: " + gsValue);
            return 100.0;
        }
        if (gsValue <= 0) {
            return 0.0;
        }
        
        // Inverse of cubic scaling (x^3) is the cube root (∛x)
        return Math.cbrt(gsValue)*100;
    }
}
