package com.coherentnetworksolutions.reson8.audio.utils;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class GstFormatMapperTest {

    @Inject
    GstFormatMapper mapper;

    @ParameterizedTest
    @MethodSource("audioFormatProvider")
    @DisplayName("Verify Java AudioFormat maps correctly to GStreamer strings")
    void testMapToGstFormat(AudioFormat javaFormat, String expectedGstFormat) {
        String result = mapper.mapToGstFormat(javaFormat);
        assertEquals(expectedGstFormat, result);
    }

    private static Stream<Arguments> audioFormatProvider() {
        return Stream.of(
            // S16LE: Your working file
            Arguments.of(
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 2, 4, 48000, false), 
                "S16LE"
            ),
            // S24LE: Your high-res file
            Arguments.of(
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 96000, 24, 1, 3, 96000, false), 
                "S24LE"
            ),
            // S32LE: High precision
            Arguments.of(
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 32, 2, 8, 44100, false), 
                "S32LE"
            ),
            // S16BE: Big Endian check
            Arguments.of(
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 2, 4, 48000, true), 
                "S16BE"
            )
        );
    }
}