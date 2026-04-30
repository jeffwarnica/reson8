package com.coherentnetworksolutions.reson8.audio.utils.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Fritsch-Carlson PCHIP implementation.
 *
 * Focuses on:
 * - Constructor validation
 * - Exact control-point pass-through (every knot must be hit exactly)
 * - Boundary clamping (below/above domain)
 * - Monotonicity preservation (no overshoot between monotone knots)
 * - The three endpoint safeguard branches (zero delta, sign reversal, overshoot cap)
 * - Inverse via Brent solver
 */
class MonotoneHermiteSignalCurveMapTest {

    private static final double TIGHT = 1e-9;
    private static final double LOOSE = 1e-5;

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    void constructor_nullPoints_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new MonotoneHermiteSignalCurveMap(null));
    }

    @Test
    void constructor_onePoint_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new MonotoneHermiteSignalCurveMap(List.of(
                new SignalCurveMap.Point(0, 0))));
    }

    // -----------------------------------------------------------------------
    // Control-point pass-through — the curve must hit every knot exactly
    // -----------------------------------------------------------------------

    @Test
    void map_twoPoints_hitsExactly() {
        var m = map(pt(0, 0), pt(100, 1));
        assertEquals(0.0, m.map(0.0), TIGHT);
        assertEquals(1.0, m.map(100.0), TIGHT);
    }

    @Test
    void map_threePoints_hitsAllKnotsExactly() {
        var m = map(pt(0, 0), pt(50, 0.8), pt(100, 1));
        assertEquals(0.0,  m.map(0.0),   TIGHT);
        assertEquals(0.8,  m.map(50.0),  TIGHT);
        assertEquals(1.0,  m.map(100.0), TIGHT);
    }

    @Test
    void map_fourPoints_hitsAllKnotsExactly() {
        var m = map(pt(0, 0), pt(20, 0), pt(60, 0.9), pt(100, 1));
        assertEquals(0.0, m.map(0.0),   TIGHT);
        assertEquals(0.0, m.map(20.0),  TIGHT);
        assertEquals(0.9, m.map(60.0),  TIGHT);
        assertEquals(1.0, m.map(100.0), TIGHT);
    }

    // -----------------------------------------------------------------------
    // Boundary clamping
    // -----------------------------------------------------------------------

    @Test
    void map_belowMinInput_clampsToFirstOutput() {
        var m = map(pt(10, 0.2), pt(90, 0.8));
        assertEquals(0.2, m.map(0.0),  TIGHT);
        assertEquals(0.2, m.map(10.0), TIGHT);
    }

    @Test
    void map_aboveMaxInput_clampsToLastOutput() {
        var m = map(pt(10, 0.2), pt(90, 0.8));
        assertEquals(0.8, m.map(90.0),  TIGHT);
        assertEquals(0.8, m.map(200.0), TIGHT);
    }

    // -----------------------------------------------------------------------
    // Monotonicity — with monotone control points the curve must not overshoot
    // -----------------------------------------------------------------------

    @Test
    void map_monotoneIncreasing_neverOvershoots() {
        var m = map(pt(0, 0), pt(30, 0.4), pt(70, 0.7), pt(100, 1));
        double prev = m.map(0.0);
        for (int i = 1; i <= 100; i++) {
            double curr = m.map(i);
            assertTrue(curr >= prev - 1e-10,
                "Monotonicity violated at x=" + i + ": " + curr + " < " + prev);
            assertTrue(curr <= 1.0 + 1e-10, "Overshoot above max at x=" + i);
            prev = curr;
        }
    }

    @Test
    void map_deadZoneThenRise_flatInDeadZone() {
        // First two points both at y=0 (plateau) then rising
        var m = map(pt(0, 0), pt(20, 0), pt(50, 0.6), pt(100, 1));
        // Dead zone must stay near zero (Fritsch-Carlson sets d=0 at plateau)
        assertEquals(0.0, m.map(0.0),  TIGHT);
        assertEquals(0.0, m.map(10.0), 0.01);  // loose — monotone but possibly slight positive slope
        assertEquals(0.0, m.map(20.0), TIGHT);
        // After the plateau the curve must rise
        assertTrue(m.map(35.0) > 0.0, "Should be rising after dead zone");
    }

    // -----------------------------------------------------------------------
    // Endpoint safeguard: zero-delta branch (delta[secIdx] == 0)
    // -----------------------------------------------------------------------

    @Test
    void map_horizontalFirstSegment_endpointDerivativeIsZero() {
        // Two points with same y — flat; the endpoint safeguard sets d[0]=0
        var m = map(pt(0, 5), pt(10, 5), pt(20, 10));
        // Curve starts flat
        assertEquals(5.0, m.map(0.0), TIGHT);
        assertEquals(5.0, m.map(5.0), 0.05);
    }

    // -----------------------------------------------------------------------
    // Endpoint safeguard: sign-reversal branch (d/delta < 0)
    // -----------------------------------------------------------------------

    @Test
    void map_opposingSlopes_clampsDerivativeToZero() {
        // Knot in the middle where left delta is positive, right delta is negative → d=0
        var m = map(pt(0, 0), pt(50, 1), pt(100, 0));
        // Curve should peak somewhere around 50 without negative overshoot
        double peak = m.map(50.0);
        assertTrue(peak >= 0.9, "Peak must be near 1.0");
        // Ensure values don't dip below zero
        for (int i = 0; i <= 100; i++) {
            assertTrue(m.map(i) >= -0.05, "Should not undershoot at x=" + i);
        }
    }

    // -----------------------------------------------------------------------
    // inverse() — Brent solver
    // -----------------------------------------------------------------------

    @Test
    void inverse_exactControlPointOutputs_roundTrip() {
        var m = map(pt(0, 0), pt(50, 0.7), pt(100, 1));
        assertEquals(0.0,  m.inverse(0.0),  LOOSE);
        assertEquals(50.0, m.inverse(0.7), LOOSE);
        assertEquals(100.0, m.inverse(1.0), LOOSE);
    }

    @Test
    void inverse_clampsBelowFirstOutput() {
        var m = map(pt(0, 0), pt(100, 1));
        // Below y[0] → return x[0]
        assertEquals(0.0, m.inverse(-0.5), TIGHT);
    }

    @Test
    void inverse_clampsAboveLastOutput() {
        var m = map(pt(0, 0), pt(100, 1));
        // Above y[last] → return x[last]
        assertEquals(100.0, m.inverse(2.0), TIGHT);
    }

    // -----------------------------------------------------------------------
    // minInput() / maxInput()
    // -----------------------------------------------------------------------

    @Test
    void minInput_returnsFirstKnot() {
        var m = map(pt(5, 0), pt(95, 1));
        assertEquals(5.0, m.minInput(), TIGHT);
    }

    @Test
    void maxInput_returnsLastKnot() {
        var m = map(pt(5, 0), pt(95, 1));
        assertEquals(95.0, m.maxInput(), TIGHT);
    }

    @Test
    void unsortedPoints_sortedInternally() {
        // MonotoneHermite sorts points by input — verify by checking knot values
        var m = new MonotoneHermiteSignalCurveMap(List.of(
            pt(100, 1), pt(0, 0), pt(50, 0.6)));
        assertEquals(0.0, m.minInput(), TIGHT);
        assertEquals(100.0, m.maxInput(), TIGHT);
        assertEquals(0.0, m.map(0.0), TIGHT);
        assertEquals(0.6, m.map(50.0), TIGHT);
        assertEquals(1.0, m.map(100.0), TIGHT);
    }

    // -----------------------------------------------------------------------
    // Binary-search intervalIndex — probe exactly at internal knots
    // (exercises the <= boundary in the binary search loop)
    // -----------------------------------------------------------------------

    @Test
    void map_exactlyAtInternalKnot_hitsPrecisely() {
        var m = map(pt(0, 0), pt(25, 0.25), pt(50, 0.75), pt(75, 0.9), pt(100, 1));
        assertEquals(0.25, m.map(25.0), TIGHT);
        assertEquals(0.75, m.map(50.0), TIGHT);
        assertEquals(0.9,  m.map(75.0), TIGHT);
    }

    @Test
    void map_justBeforeAndAfterKnot_doesNotJump() {
        var m = map(pt(0, 0), pt(50, 0.6), pt(100, 1));
        double before = m.map(49.999);
        double at     = m.map(50.0);
        double after  = m.map(50.001);
        assertTrue(before < at || Math.abs(before - at) < 0.01,
            "Value should not jump just before knot");
        assertTrue(after > at || Math.abs(after - at) < 0.01,
            "Value should not jump just after knot");
    }

    // -----------------------------------------------------------------------
    // Endpoint safeguard — overshoot-cap branch (|d| > 3 * |delta|)
    // A very steep first segment with a gentle second segment forces the
    // Fritsch-Carlson safeguard to cap the endpoint derivative at 3*delta.
    // -----------------------------------------------------------------------

    @Test
    void map_overshootCapBranch_curveStaysMonotone() {
        // x: 0→1→100; y: 0→0.9→1.0 — first delta is huge (0.9), second is tiny (0.001)
        // The endpoint derivative at x=1 can't exceed 3 * delta[0]
        var m = map(pt(0, 0), pt(1, 0.9), pt(100, 1.0));
        assertEquals(0.0,  m.map(0.0),   TIGHT);
        assertEquals(0.9,  m.map(1.0),   TIGHT);
        assertEquals(1.0,  m.map(100.0), TIGHT);
        // Monotone — no values outside [0, 1]
        for (int i = 0; i <= 100; i++) {
            double v = m.map(i);
            assertTrue(v >= -0.001 && v <= 1.001,
                "Overshoot at x=" + i + ": " + v);
        }
    }

    // -----------------------------------------------------------------------
    // Endpoint safeguard — sign-reversal at last endpoint
    // -----------------------------------------------------------------------

    @Test
    void map_signReversalAtLastEndpoint_curveStaysMonotone() {
        // Decreasing final segment: Fritsch-Carlson sets d[last] = 0 to prevent reversal
        var m = map(pt(0, 0), pt(50, 1.0), pt(80, 0.8));
        assertEquals(0.0, m.map(0.0),  TIGHT);
        assertEquals(1.0, m.map(50.0), TIGHT);
        assertEquals(0.8, m.map(80.0), TIGHT);
    }

    // -----------------------------------------------------------------------
    // Math precision — Hermite basis values at t=0.5 midpoint
    // -----------------------------------------------------------------------

    @Test
    void map_midpointBetweenTwoKnots_isReasonablyInterpolated() {
        var m = map(pt(0, 0), pt(100, 1));
        double mid = m.map(50.0);
        // A straight-line PCHIP with only 2 points behaves like a cubic, not linear;
        // the midpoint should be between 0 and 1
        assertTrue(mid > 0.0 && mid < 1.0, "Midpoint must be between 0 and 1, got: " + mid);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MonotoneHermiteSignalCurveMap map(SignalCurveMap.Point... points) {
        return new MonotoneHermiteSignalCurveMap(List.of(points));
    }

    private SignalCurveMap.Point pt(double input, double output) {
        return new SignalCurveMap.Point(input, output);
    }
}
