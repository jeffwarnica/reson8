package com.coherentnetworksolutions.reson8.audio.sound;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.freedesktop.gstreamer.Caps;

import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WavCache {


    @Inject GsToolkit toolkit;

    private Map<String, CachedWav> cache = new ConcurrentHashMap<>();

    @Inject Reson8Config config;

    public CachedWav getOrLoad(String filePathFragment) {
        return cache.computeIfAbsent(filePathFragment, this::loadFromDisk);
    }

    private CachedWav loadFromDisk(String filePathFragment) {
        Log.infof("Loading and caching sound file: [%s]", filePathFragment);
    // private void cacheFile(String filePathFragment) {
        URL gsUri;
        try {
            gsUri = Path.of(config.audioPath()).resolve(filePathFragment).toAbsolutePath().toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unable to construct URL for audio file: " + filePathFragment, e);
        }

        Log.infof("Attempting to find file at: [%s]", gsUri);

        try (InputStream in = gsUri.openStream()) {
            
            AudioInputStream ais = AudioSystem.getAudioInputStream(in);

            AudioFormat af = ais.getFormat();
            Log.debugf("File [%s] is of format [%s]", filePathFragment, af);

            byte[] pcmData = ais.readAllBytes();
            
            return CachedWav.create(pcmData, af, toolkit);

        } catch (IOException | UnsupportedAudioFileException e) {
            Log.fatalf("File load problems: [%s]", e.getMessage());
            throw new RuntimeException("Required audio file missing or invalid: " + filePathFragment, e);

        }
        

    }

    public List<String> getCachedFiles() {
        return new ArrayList<>(cache.keySet());
    }
    public CachedWav getDataFor(String dropName) {
        Log.debugf("getDataFor([%s]", dropName);
        return cache.get(dropName);
    }

    public record CachedWav(byte[] pcmData, AudioFormat audioFormat, String capsString, Caps caps) {
        
        public static CachedWav create(byte[] pcmData, AudioFormat audioFormat, GsToolkit toolkit) {
            if (pcmData == null)
                throw new IllegalArgumentException("pcmData cannot be null");
            if (audioFormat == null)
                throw new IllegalArgumentException("audioFormat cannot be null");
            if (toolkit == null)
                throw new IllegalArgumentException("toolkit cannot be null");
            String capsString = generateCapsString(audioFormat);
            Log.debugf("Creating cachedWav with format: [%s]", audioFormat);
            return new CachedWav(pcmData, audioFormat, capsString, toolkit.capsFromString(capsString));
        }

        // --- Helper Getters for easier GStreamer integration ---

        public double sampleRate() {
            return audioFormat.getSampleRate();
        }

        public int channels() {
            return audioFormat.getChannels();
        }

        public int sampleSizeInBits() {
            int bits = audioFormat.getSampleSizeInBits();
            if (bits <= 0 && audioFormat.getFrameSize() > 0) {
                bits = (audioFormat.getFrameSize() / audioFormat.getChannels()) * 8;
            }

            // Final fallback to 16 if metadata is totally missing
            return (bits > 0) ? bits : 16;
        }

        /**
         * Bulletproof frame size calculation.
         * Uses manual math if audioFormat.getFrameSize() is invalid.
         */
        public int bytesPerFrame() {
            int fs = audioFormat.getFrameSize();
            if (fs <= 0) {
                // If getFrameSize() is invalid, we calculate it:
                // (Bits / 8) * Channels.
                // We use Math.max to ensure we never return 0.
                int bits = audioFormat.getSampleSizeInBits();
                if (bits <= 0)
                    bits = 16; // Default fallback

                fs = (bits / 8) * channels();
            }
            return Math.max(1, fs);
        }

        private static String generateCapsString(AudioFormat fmt) {
            int bits = fmt.getSampleSizeInBits();
            boolean signed = fmt.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED);
            String endian = fmt.isBigEndian() ? "BE" : "LE";
            String gstFmt = (signed ? "S" : "U") + bits + endian;
            String mask = (fmt.getChannels() == 1) ? "0x0" : "0x3";

            return String.format(
                    "audio/x-raw,format=%s,channels=%d,rate=%d,layout=interleaved,channel-mask=(bitmask)%s",
                    gstFmt, fmt.getChannels(), (int) fmt.getSampleRate(), mask);
        }

    }


}
