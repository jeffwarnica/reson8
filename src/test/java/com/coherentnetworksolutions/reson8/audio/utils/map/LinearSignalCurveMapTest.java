package com.coherentnetworksolutions.reson8.audio.utils.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinearSignalCurveMapTest {

    private static final double DELTA = 1e-9;

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    void constructor_nullPoints_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new LinearSignalCurveMap(null));
    }

    @Test
    void constructor_emptyList_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new LinearSignalCurveMap(List.of()));
    }

    @Test
    void constructor_onePoint_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new LinearSignalCurveMap(List.of(new SignalCurveMap.Point(0, 0))));
    }

    @Test
    void constructor_threePoints_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new LinearSignalCurveMap(List.of(
                new SignalCurveMap.Point(0, 0),
                new SignalCurveMap.Point(50, 50),
                new SignalCurveMap.Point(100, 100))));
    }

    @Test
    void constructor_duplicateInputs_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new LinearSignalCurveMap(List.of(
                new SignalCurveMap.Point(10, 0),
                new SignalCurveMap.Point(10, 1))));
    }

    // -----------------------------------------------------------------------
    // map() — linear interpolation
    // -----------------------------------------------------------------------

    @Test
    void map_exactEndpoints_returnsConfiguredOutputs() {
        LinearSignalCurveMap m = twoPoint(0, 0, 100, 100);
        assertEquals(0.0, m.map(0.0), DELTA);
        assertEquals(100.0, m.map(100.0), DELTA);
    }

    @Test
    void map_midpoint_returnsHalf() {
        LinearSignalCurveMap m = twoPoint(0, 0, 100, 100);
        assertEquals(50.0, m.map(50.0), DELTA);
    }

    @ParameterizedTest
    @CsvSource({
        "0.0,  0.0,   100.0, 1.0,   50.0,  0.5",    // scale 0-100 → 0-1
        "0.0,  0.0,   10.0,  100.0, 5.0,   50.0",    // scale 0-10 → 0-100
        "20.0, 0.0,   70.0,  100.0, 45.0,  50.0"     // shifted domain
    })
    void map_linearMidpoint(double x0, double y0, double x1, double y1, double xIn, double expected) {
        LinearSignalCurveMap m = twoPoint(x0, y0, x1, y1);
        assertEquals(expected, m.map(xIn), 1e-6);
    }

    @Test
    void map_negativeSlope_producesDecreasingOutput() {
        // Decreasing: 0→100 input maps to 100→0 output
        LinearSignalCurveMap m = twoPoint(0, 100, 100, 0);
        assertTrue(m.map(0.0) > m.map(100.0));
        assertEquals(50.0, m.map(50.0), 1e-6);
    }

    @Test
    void map_extrapolatesOutsideDomain() {
        // LinearSignalCurveMap does not clamp — values outside [min,max] are extrapolated
        LinearSignalCurveMap m = twoPoint(0, 0, 100, 100);
        assertTrue(m.map(150.0) > 100.0, "Should extrapolate above max");
        assertTrue(m.map(-10.0) < 0.0, "Should extrapolate below min");
    }

    // -----------------------------------------------------------------------
    // inverse()
    // -----------------------------------------------------------------------

    @Test
    void inverse_exactValues_roundTrips() {
        LinearSignalCurveMap m = twoPoint(0, 0, 100, 100);
        assertEquals(0.0, m.inverse(m.map(0.0)), DELTA);
        assertEquals(50.0, m.inverse(m.map(50.0)), DELTA);
        assertEquals(100.0, m.inverse(m.map(100.0)), DELTA);
    }

    @Test
    void inverse_zeroSlope_throws() {
        // Horizontal line: y is always the same regardless of x — not invertible
        LinearSignalCurveMap m = twoPoint(0, 5, 100, 5);
        assertThrows(IllegalStateException.class, () -> m.inverse(5.0));
    }

    // -----------------------------------------------------------------------
    // minInput() / maxInput()
    // -----------------------------------------------------------------------

    @Test
    void minInput_returnsSmaller() {
        LinearSignalCurveMap m = twoPoint(20, 0, 80, 1);
        assertEquals(20.0, m.minInput(), DELTA);
    }

    @Test
    void maxInput_returnsLarger() {
        LinearSignalCurveMap m = twoPoint(20, 0, 80, 1);
        assertEquals(80.0, m.maxInput(), DELTA);
    }

    @Test
    void minMax_reversedPointOrder_stillCorrect() {
        // Points provided in descending input order — min/max should still be right
        LinearSignalCurveMap m = new LinearSignalCurveMap(List.of(
            new SignalCurveMap.Point(80, 1),
            new SignalCurveMap.Point(20, 0)));
        assertEquals(20.0, m.minInput(), DELTA);
        assertEquals(80.0, m.maxInput(), DELTA);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private LinearSignalCurveMap twoPoint(double x0, double y0, double x1, double y1) {
        return new LinearSignalCurveMap(List.of(
            new SignalCurveMap.Point(x0, y0),
            new SignalCurveMap.Point(x1, y1)));
    }
}
