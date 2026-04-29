package com.coherentnetworksolutions.reson8.audio.utils.map;

import java.util.List;

import com.coherentnetworksolutions.reson8.audio.utils.map.SignalCurveMap.Point;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import jakarta.enterprise.context.ApplicationScoped;

/*
    * Factory for creating SignalMapper instances based on Reson8Config.CurveConfig.
     * Handles both linear and smooth (spline) interpolation types.     
     * If no curveConfig is provided, defaults to a simple linear mapping from 0 to 100 input mapped to 0.0 to 1.0 output.  
     * Validates the provided configuration and throws IllegalArgumentException for unsupported interpolation types or invalid point configurations.    
     * 
 */
@ApplicationScoped
public class CurveMapFactory {
    public SignalCurveMap createCurve(Reson8Config.CurveConfig curveConfig) {
        if (curveConfig == null || curveConfig.interpolation() == Reson8Config.Interpolation.LINEAR) {
            List<Point> points = curveConfig != null 
                ? configPointsToRecords(curveConfig.points())
                : List.of(
                    new Point(0, 0.0),
                    new Point(100, 100.0)
                );
            return new LinearSignalCurveMap(points);
        }
        
        List<Point> points = configPointsToRecords(curveConfig.points());
        if (curveConfig.interpolation() == Reson8Config.Interpolation.SMOOTH) {
            return new SplineSignalCurveMap(points);
        }
        if (curveConfig.interpolation() == Reson8Config.Interpolation.MONOTONE) {
            return new MonotoneHermiteSignalCurveMap(points);
        }

        throw new IllegalArgumentException("Unsupported interpolation type: " + curveConfig.interpolation());
    }

    private List<SignalCurveMap.Point> configPointsToRecords(List<Reson8Config.CurvePoint> points) {
        return points.stream()
            .map(p -> new Point(p.input(), p.output()))
            .toList();
    }
}
