package com.coherentnetworksolutions.reson8.audio.output;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.utils.WavHeaderUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class WavHeaderTest {
    @Test
    void testWavHeaderConstruction() {
        // No GStreamer needed!
        byte[] header = WavHeaderUtils.createPcmHeader(44100, 16, 2);

        assertEquals('R', (char)header[0]);
        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(44100, bb.getInt(24));
        assertEquals(16, bb.getShort(34));
    }
}