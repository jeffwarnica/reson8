package com.coherentnetworksolutions.reson8.audio.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WavHeaderUtils {

    /**
     * Generates a 44-byte PCM WAV header.
     * Note: File and Data sizes are set to Integer.MAX_VALUE to support infinite streaming.
     */
    public static byte[] createPcmHeader(long sampleRate, int bitDepth, int channels) {
        byte[] header = new byte[44];
        long byteRate = sampleRate * channels * bitDepth / 8;

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        // ChunkSize: Set to max for streaming
        header[4] = (byte) 0xff; header[5] = (byte) 0xff; header[6] = (byte) 0xff; header[7] = (byte) 0x7f;
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        
        header[16] = 16; // Subchunk1Size (16 for PCM)
        header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; // AudioFormat (1 for PCM)
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        
        // Little Endian writes for the numeric values
        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(24, (int) sampleRate);
        bb.putInt(28, (int) byteRate);
        bb.putShort(32, (short) (channels * bitDepth / 8)); // BlockAlign
        bb.putShort(34, (short) bitDepth);

        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        // Subchunk2Size: Set to max for streaming
        header[40] = (byte) 0xff; header[41] = (byte) 0xff; header[42] = (byte) 0xff; header[43] = (byte) 0x7f;

        return header;
    }
}