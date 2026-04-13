package com.coherentnetworksolutions.reson8.audio.utils;

import java.util.List;
import java.util.Map;

import org.jboss.resteasy.reactive.common.NotImplementedYet;

public class IntensityCurve {
    public static double calculate(double input, List<Map<String, Double>> points, String interp) {
        // 1. Sort points by 'in' value
        // 2. Find the two points 'input' falls between
        // 3. Perform Linear or Smooth (Cubic) interpolation
        // 4. Handle 'extrapolate' logic
        throw new NotImplementedYet();
        // return result;
    }
}