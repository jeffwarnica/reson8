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

    @Inject
    VolumeScaler scaler;

    @ParameterizedTest
    @CsvSource({
        "0.0,   0.0",      // Muted
        "1.0,   1.0",      // Max
        "0.5,   0.125",    // Mid-point (Cubic)
        "0.794, 0.5"       // ~ -6dB point (0.794^3 approx 0.5)
    })
    @DisplayName("Verify UI slider maps correctly to cubic power curve")
    void testVolumeScaling(double input, double expected) {
        // Using 0.001 delta for floating point precision
        assertEquals(expected, scaler.uiToGstVolume(input), 0.001);
    }
    
}
