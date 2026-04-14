package com.coherentnetworksolutions.reson8.audio.factories;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.freedesktop.gstreamer.Caps;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.DropChannel;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalEndpoint;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DropFactory {
    
    /**
     * The raw sounds, which could be used in several drops
     */
    private Map<String, CachedWav> preloadedSounds = new HashMap<>();

    // private Map<String, SoundDefinition> soundConfigs = new HashMap<>();
    
    // @Inject DropFactory dropController;
    @Inject Mixer mixer;

    @Inject Reson8Config config;

    public DropChannel createDropDefinition(SignalEndpoint signalEndpoint) {
        String filename = signalEndpoint.getSoundDefinition().drop().orElseThrow().filename();

        if (!preloadedSounds.containsKey(filename)) {
            cacheFile(filename);
        }

        return new DropChannel(signalEndpoint, this, mixer);
    }

    private void cacheFile(String filePathFragment) {
        URL gsUri = null;
        try {
            gsUri = Path.of(config.audioPath()).resolve(filePathFragment).toAbsolutePath().toUri().toURL();
        } catch (MalformedURLException e) {
            Log.fatalf("Unable to turn [%s] into URL", filePathFragment);
            e.printStackTrace();
        }

        Log.infof("Resource found at: [%s]", gsUri);

        try (InputStream in = gsUri.openStream()) {
            
            if (in == null) {
                Log.errorf("Resource NOT FOUND: [%s]. Ensure it is in src/main/resources/", filePathFragment);
                return;
            }

            AudioInputStream ais = AudioSystem.getAudioInputStream(in);

            // Grab on disk format
            AudioFormat af = ais.getFormat();
            Log.debugf("File [%s] is of format [%s]", filePathFragment, af);

            byte[] pcmData = ais.readAllBytes();
            
            CachedWav cached = CachedWav.create(pcmData,af);

            preloadedSounds.put(filePathFragment, cached);

            Log.infof("Cached wav file [%s] into memory as PCM", filePathFragment);

        } catch (IOException e) {
            Log.fatalf("File load problems: [%s]", e.getMessage());
            e.printStackTrace();
        } catch (UnsupportedAudioFileException e) {
            Log.fatalf("Unsupported audio file at [%s]", filePathFragment);
            e.printStackTrace();
        }

    }

    public List<String> getCachedFiles() {
        
        return new ArrayList<>(preloadedSounds.keySet());
    }
    public CachedWav getDataFor(String dropName) {
        Log.debugf("getDataFor([%s]", dropName);
        return preloadedSounds.get(dropName);
    }

    public record CachedWav(byte[] pcmData, AudioFormat audioFormat, String capsString, Caps caps) {
        
        public static CachedWav create(byte[] pcmData, AudioFormat audioFormat) { 
            if (pcmData == null)
                throw new IllegalArgumentException("pcmData cannot be null");
            if (audioFormat == null)
                throw new IllegalArgumentException("audioFormat cannot be null");
            String capsString = generateCapsString(audioFormat);
            Log.debugf("Creating cachedWav with format: [%s]", audioFormat);
            return new CachedWav(pcmData, audioFormat, capsString, Caps.fromString(capsString));

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
