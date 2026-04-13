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

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.OneShotChannel;
import com.coherentnetworksolutions.reson8.audio.utils.GstFormatMapper;
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
    
    @Inject GstFormatMapper mapper;
    // @Inject DropFactory dropController;
    @Inject Mixer mixer;

    @Inject Reson8Config config;

    public OneShotChannel createDropDefinition(SignalEndpoint signalEndpoint) {
        String filename = signalEndpoint.getSoundDefinition().drop().orElseThrow().filename();

        if (!preloadedSounds.containsKey(filename)) {
            cacheFile(filename);
        }

        return new OneShotChannel(signalEndpoint, this, mixer);
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
            CachedWav cached = new CachedWav(
                    pcmData,
                    (int) af.getSampleRate(),
                    af.getChannels(),
                    mapper.mapToGstFormat(af));

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
        Log.debugf("getDatFor([%s]", dropName);
        return preloadedSounds.get(dropName);
    }

    public class CachedWav {
        public final byte[] pcmData;
        public final int sampleRate;
        public final int channels;
        public final String format; // e.g., "S16LE"

        public CachedWav(byte[] pcmData, int sampleRate, int channels, String format) {
            this.pcmData = pcmData;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.format = format;
        }
    }


}
