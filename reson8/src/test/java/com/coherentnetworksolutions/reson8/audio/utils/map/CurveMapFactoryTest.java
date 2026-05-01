package com.coherentnetworksolutions.reson8.audio.utils.map;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain JUnit5 tests for CurveMapFactory.
 *
 * CurveConfig and CurvePoint are interfaces — instantiated here as anonymous
 * classes to avoid Quarkus CDI overhead and remain compatible with PITest.
 */
class CurveMapFactoryTest {

    private CurveMapFactory factory;

    @BeforeEach
    void setup() {
        factory = new CurveMapFactory();
    }

    // -----------------------------------------------------------------------
    // null config → default LinearSignalCurveMap (0-100 → 0-100)
    // -----------------------------------------------------------------------

    @Test
    void createCurve_nullConfig_returnsLinearDefault() {
        SignalCurveMap curve = factory.createCurve(null);
        assertInstanceOf(LinearSignalCurveMap.class, curve);
        assertEquals(0.0,   curve.map(0.0),   1e-9);
        assertEquals(50.0,  curve.map(50.0),  1e-9);
        assertEquals(100.0, curve.map(100.0), 1e-9);
    }

    // -----------------------------------------------------------------------
    // LINEAR interpolation
    // -----------------------------------------------------------------------

    @Test
    void createCurve_linear_returnsLinearMap() {
        SignalCurveMap curve = factory.createCurve(curveConfig(
            Reson8Config.Interpolation.LINEAR,
            curvePoint(0, 0), curvePoint(100, 1)));
        assertInstanceOf(LinearSignalCurveMap.class, curve);
        assertEquals(0.0, curve.map(0.0),   1e-9);
        assertEquals(0.5, curve.map(50.0),  1e-9);
        assertEquals(1.0, curve.map(100.0), 1e-9);
    }

    // -----------------------------------------------------------------------
    // SMOOTH interpolation (spline)
    // -----------------------------------------------------------------------

    @Test
    void createCurve_smooth_returnsSplineMap() {
        SignalCurveMap curve = factory.createCurve(curveConfig(
            Reson8Config.Interpolation.SMOOTH,
            curvePoint(0, 0), curvePoint(50, 0.8), curvePoint(100, 1)));
        assertInstanceOf(SplineSignalCurveMap.class, curve);
        assertEquals(0.0, curve.map(0.0),   1e-9);
        assertEquals(0.8, curve.map(50.0),  1e-9);
        assertEquals(1.0, curve.map(100.0), 1e-9);
    }

    // -----------------------------------------------------------------------
    // MONOTONE interpolation (PCHIP)
    // -----------------------------------------------------------------------

    @Test
    void createCurve_monotone_returnsMonotoneHermiteMap() {
        SignalCurveMap curve = factory.createCurve(curveConfig(
            Reson8Config.Interpolation.MONOTONE,
            curvePoint(0, 0), curvePoint(50, 0.7), curvePoint(100, 1)));
        assertInstanceOf(MonotoneHermiteSignalCurveMap.class, curve);
        assertEquals(0.0, curve.map(0.0),   1e-9);
        assertEquals(0.7, curve.map(50.0),  1e-9);
        assertEquals(1.0, curve.map(100.0), 1e-9);
    }

    // -----------------------------------------------------------------------
    // point conversion — configPointsToRecords exercises the mapping lambda
    // -----------------------------------------------------------------------

    @Test
    void createCurve_pointsAreConvertedCorrectly() {
        SignalCurveMap curve = factory.createCurve(curveConfig(
            Reson8Config.Interpolation.LINEAR,
            curvePoint(10, 0.1), curvePoint(90, 0.9)));
        assertEquals(10.0, curve.minInput(), 1e-9);
        assertEquals(90.0, curve.maxInput(), 1e-9);
    }

    // -----------------------------------------------------------------------
    // Helpers — anonymous implementations of the Quarkus config interfaces
    // -----------------------------------------------------------------------

    private Reson8Config.CurveConfig curveConfig(
            Reson8Config.Interpolation interpolation,
            Reson8Config.CurvePoint... points) {
        return new Reson8Config.CurveConfig() {
            @Override public Reson8Config.Interpolation interpolation() { return interpolation; }
            @Override public List<Reson8Config.CurvePoint> points()     { return List.of(points); }
        };
    }

    private Reson8Config.CurvePoint curvePoint(double in, double out) {
        return new Reson8Config.CurvePoint() {
            @Override public double input()  { return in; }
            @Override public double output() { return out; }
        };
    }
}
