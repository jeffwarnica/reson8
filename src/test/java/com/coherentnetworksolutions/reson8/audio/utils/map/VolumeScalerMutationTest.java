package com.coherentnetworksolutions.reson8.audio.utils.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain JUnit5 tests for VolumeScaler — no Quarkus CDI context required.
 *
 * These run under both the standard Surefire suite and PITest's mutation
 * engine. The @QuarkusTest sibling class (VolumeScalerTest) serves the
 * separate purpose of asserting that CDI startup does not leak a native
 * GStreamer initialisation.
 */
class VolumeScalerMutationTest {

    private static final double DELTA = 0.0001;

    // -----------------------------------------------------------------------
    // humanToGstVolume — boundary clamps
    // -----------------------------------------------------------------------

    @Test
    void humanToGst_negative_clampsToZero() {
        assertEquals(0.0, VolumeScaler.humanToGsVolume(-10.0), DELTA);
    }

    @Test
    void humanToGst_zero_isZero() {
        assertEquals(0.0, VolumeScaler.humanToGsVolume(0.0), DELTA);
    }

    @Test
    void humanToGst_hundredPercent_isOne() {
        assertEquals(1.0, VolumeScaler.humanToGsVolume(100.0), DELTA);
    }

    @Test
    void humanToGst_overHundred_clampsToOne() {
        assertEquals(1.0, VolumeScaler.humanToGsVolume(150.0), DELTA);
    }

    @ParameterizedTest
    @CsvSource({
        "50.0,  0.125",   // midpoint: (0.5)^3
        "10.0,  0.001",   // low end: (0.1)^3
        "80.0,  0.512",   // high end: (0.8)^3
        "25.0,  0.015625" // quarter: (0.25)^3
    })
    void humanToGst_cubicScaling(double human, double expectedGst) {
        assertEquals(expectedGst, VolumeScaler.humanToGsVolume(human), DELTA);
    }

    // -----------------------------------------------------------------------
    // gstToHumanVolume — boundary clamps
    // -----------------------------------------------------------------------

    @Test
    void gstToHuman_negative_clampsToZero() {
        assertEquals(0.0, VolumeScaler.gsToHumanVolume(-0.5), DELTA);
    }

    @Test
    void gstToHuman_zero_isZero() {
        assertEquals(0.0, VolumeScaler.gsToHumanVolume(0.0), DELTA);
    }

    @Test
    void gstToHuman_one_isHundred() {
        assertEquals(100.0, VolumeScaler.gsToHumanVolume(1.0), DELTA);
    }

    @Test
    void gstToHuman_overOne_clampsToHundred() {
        assertEquals(100.0, VolumeScaler.gsToHumanVolume(2.5), DELTA);
    }

    @ParameterizedTest
    @CsvSource({
        "0.125,  50.0",   // ∛0.125 * 100 = 50
        "0.001,  10.0",   // ∛0.001 * 100 = 10
        "0.512,  80.0",   // ∛0.512 * 100 = 80
        "0.015625, 25.0"  // ∛0.015625 * 100 = 25
    })
    void gstToHuman_cubeRootScaling(double gst, double expectedHuman) {
        assertEquals(expectedHuman, VolumeScaler.gsToHumanVolume(gst), 0.01);
    }

    // -----------------------------------------------------------------------
    // Round-trip: humanToGst then gstToHuman must be identity
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"1.0", "10.0", "25.0", "50.0", "75.0", "99.0", "100.0"})
    void roundTrip_humanToGstToHuman(double human) {
        double recovered = VolumeScaler.gsToHumanVolume(VolumeScaler.humanToGsVolume(human));
        assertEquals(human, recovered, DELTA, "Round-trip failed for human=" + human);
    }

    // -----------------------------------------------------------------------
    // Ordering: higher human input must produce higher GStreamer output
    // -----------------------------------------------------------------------

    @Test
    void humanToGst_isStrictlyMonotone() {
        double prev = VolumeScaler.humanToGsVolume(0.0);
        for (int i = 1; i <= 100; i++) {
            double curr = VolumeScaler.humanToGsVolume(i);
            assertTrue(curr >= prev, "Monotonicity violated at human=" + i);
            prev = curr;
        }
    }
}
