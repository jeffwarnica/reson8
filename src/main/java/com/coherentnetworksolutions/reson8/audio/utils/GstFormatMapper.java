package com.coherentnetworksolutions.reson8.audio.utils;

import jakarta.enterprise.context.ApplicationScoped;
import javax.sound.sampled.AudioFormat;

@ApplicationScoped
public class GstFormatMapper {

    public String mapToGstFormat(AudioFormat fmt) {
        int bits = fmt.getSampleSizeInBits();
        boolean signed = fmt.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED);
        boolean bigEndian = fmt.isBigEndian();

        StringBuilder sb = new StringBuilder();
        sb.append(signed ? "S" : "U");
        sb.append(bits);
        sb.append(bigEndian ? "BE" : "LE");
        
        return sb.toString();
    }
}