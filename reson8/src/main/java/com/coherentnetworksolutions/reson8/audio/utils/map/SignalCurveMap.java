package com.coherentnetworksolutions.reson8.audio.utils.map;

public interface SignalCurveMap {
    /**
     * Transforms a value from the input domain (e.g., Hits/sec) to the output
     * range (e.g., 0-100 Intensity).
     */
    double map(double value);

    /**
     * Reverses the transformation. Given an intensity, what was the original
     * input value?
     */
    double inverse(double value);

    /** The minimum value of the input domain (e.g., minimum metric value). */
    double minInput();

    /** The maximum value of the input domain (e.g., maximum metric value). */
    double maxInput();

    /**
     * Like {@link #map(double)} but without output clamping. Useful for visualising the raw
     * spline shape (including any undershoot or overshoot) so operators can see oscillation
     * caused by non-uniform knot spacing. Defaults to {@code map(value)} for implementations
     * that do not clamp (e.g. linear).
     */
    default double mapUnclamped(double value) {
        return map(value);
    }

    public record Point(double input, double output){}
    
}

