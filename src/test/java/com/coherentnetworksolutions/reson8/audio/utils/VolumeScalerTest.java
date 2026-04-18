package com.coherentnetworksolutions.reson8.audio.utils;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@Timeout(10)
class VolumeScalerTest {

    private static final double DELTA = 0.0001;

    @ParameterizedTest
    @DisplayName("Human (0-100) to GStreamer (0-1) Scaling")
    @CsvSource({ "-10.0, 0.0", // Out of bounds low
            "0.0,   0.0", // Muted
            "50.0,  0.125", // Mid-point (0.5^3)
            "100.0, 1.0", // Max
            "150.0, 1.0" // Out of bounds high
    })
    void testHumanToGst(double input, double expected) {
        assertEquals(expected, VolumeScaler.humanToGstVolume(input), DELTA);
    }

    @ParameterizedTest
    @DisplayName("GStreamer (0-1) to Human (0-100) Scaling")
    @CsvSource({ "-0.5,   0.0", // Out of bounds low
            "0.0,    0.0", // Muted
            "0.125,  50.0", // Mid-point (∛0.125 * 100)
            "0.5,    79.37", // Common -6dB point check
            "1.0,    100.0", // Max
            "2.5,    100.0" // Out of bounds high
    })
    void testGstToHuman(double input, double expected) {
        assertEquals(expected, VolumeScaler.gstToHumanVolume(input), 0.01); // Slightly wider delta for cbrt
    }

    @ParameterizedTest
    @DisplayName("Round-trip consistency (Human -> Gst -> Human)")
    @CsvSource({ "10.0", "25.5", "50.0", "75.0", "99.9" })
    void testRoundTrip(double input) {
        double gst = VolumeScaler.humanToGstVolume(input);
        double backToHuman = VolumeScaler.gstToHumanVolume(gst);

        assertEquals(input, backToHuman, DELTA, "Round trip failed: Value drifted significantly during conversion");
    }
    
}
