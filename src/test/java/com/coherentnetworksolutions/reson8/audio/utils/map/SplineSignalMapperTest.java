package com.coherentnetworksolutions.reson8.audio.utils.map;


import com.coherentnetworksolutions.reson8.audio.utils.map.SignalCurveMap.Point;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class SplineSignalMapperTest {

    @Test
    void testBadConfigDealtWith() {
        // Empty points list should throw
        assertThrows(IllegalArgumentException.class, () -> new SplineSignalCurveMap(new ArrayList<>()));

        // Single point should throw (can't create a spline)
        List<Point> onePoint = List.of(new Point(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SplineSignalCurveMap(onePoint));

        // Two points should throw (can't create a spline)
        List<Point> twoPoints = List.of(new Point(0, 0), new Point(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SplineSignalCurveMap(twoPoints));

        // Unsorted points should be sorted internally (or throw)
        List<Point> unsorted = List.of(new Point(20, 0), new Point(0, 0), new Point(7.0, 2.0));
        
        assertThrows(IllegalArgumentException.class, () -> new SplineSignalCurveMap(unsorted));
    
    }


    @Test
    void testDecreasingInputValuesBarfs() {
        
        List<Point> decreasingX = List.of(
            new Point(10.0, 0.0),
            new Point(5.0, 0.5),
            new Point(0.0, 1.0)
        );
        
        assertThrows(IllegalArgumentException.class, () -> new SplineSignalCurveMap(decreasingX));
        
    }

    @Test
    void testDecreasingOutputValues() {
        // Points with decreasing outputs
        List<Point> decreasingOutputs = List.of(
            new Point(0.0, 1.0),
            new Point(5.0, 0.5),
            new Point(10.0, 0.0)
        );
        
        SplineSignalCurveMap mapper = new SplineSignalCurveMap(decreasingOutputs);
        
        // Verify mapping works correctly
        assertEquals(1.0, mapper.map(0.0), 0.01);
        assertEquals(0.5, mapper.map(5.0), 0.01);
        assertEquals(0.0, mapper.map(10.0), 0.01);
        
        // Test interpolation
        assertTrue(mapper.map(2.5) > 0.5 && mapper.map(2.5) < 1.0);
    }
    

    @Test
    void testPrometheusToIntensityMapping() {
        
        List<Point> points = new ArrayList<>();
        points.add(new Point(0, 0.0));
        points.add(new Point(20, 0.0));
        points.add(new Point(35, 0.6));
        points.add(new Point(50, 0.9));
        points.add(new Point(70, 0.95));

        SplineSignalCurveMap mapper = new SplineSignalCurveMap(points);

        // 1. Verify Dead Zone (0 to 20)
        assertEquals(0.0, mapper.map(0.0), 1e-9);
        assertEquals(0.0, mapper.map(10.0), 1e-9);
        assertEquals(0.0, mapper.map(20.0), 1e-9);

        // 2. Verify Mid-Curve (interpolated)
        // At 35, it should be exactly 0.6
        assertEquals(0.6, mapper.map(35.0), 1e-9);

        // Between 20 and 35, it should be rising
        double midVal = mapper.map(27.5);
        assertTrue(midVal > 0.0 && midVal < 0.6, "Value should be rising in the active zone");

        // 3. Verify Ceiling/Clamping
        assertEquals(0.9, mapper.map(50.0), 1e-9);
        assertEquals(0.95, mapper.map(100.0), 1e-9, "Should clamp at maxIn");
    }

    @Test
    void testInverseLookup() {
        List<Point> points = List.of(new Point(0, 0.0), new Point(20, 0.0), new Point(35, 0.6), new Point(50, 0.9));
        SplineSignalCurveMap mapper = new SplineSignalCurveMap(new ArrayList<>(points));

        // 1. Inverse of a dead-zone value
        // Note: BrentSolver will return a value between 0 and 20.
        // In monotonic dead-zones, any value in [0,20] is technically correct.
        double invZero = mapper.inverse(0.0);
        assertTrue(invZero >= 0.0 && invZero <= 20.0);

        // 2. Inverse of an active value
        // If map(35) = 0.6, then inverse(0.6) must = 35
        assertEquals(35.0, mapper.inverse(0.6), 1e-5);

        // 3. Inverse of max
        assertEquals(50.0, mapper.inverse(0.9), 1e-5);
    }

    @Test
    void testAutoPrependZero() {
        // User forgot to add 0,0
        List<Point> points = new ArrayList<>();
        points.add(new Point(10, 0.5));
        points.add(new Point(20, 1.0));

        SplineSignalCurveMap mapper = new SplineSignalCurveMap(points);

        // Should have auto-inserted 0,0
        assertEquals(0.0, mapper.map(0.0), 1e-9);
        assertTrue(mapper.map(5.0) > 0.0);
    }
}