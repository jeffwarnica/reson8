package com.coherentnetworksolutions.reson8.controllers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.coherentnetworksolutions.reson8.audio.utils.GstFormatMapper;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DropController {

    private Map<String, CachedWav> preloadedDrops = new HashMap<>();

    @Inject
    GstFormatMapper mapper;

    // @Startup
    public void init() {
        List<String> files = listWavsOnDisk();

        for (String fileUrl : files) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("sounds/" + fileUrl)) {
                if (in == null) continue;

                AudioInputStream ais = AudioSystem.getAudioInputStream(in);
                
                // Grab on disk format
                AudioFormat af = ais.getFormat();
                Log.debugf("File [%s] is of format [%s]", fileUrl, af);
                
                byte[] pcmData = ais.readAllBytes();
                CachedWav cached = new CachedWav(
                    pcmData,
                    (int) af.getSampleRate(),
                    af.getChannels(),
                    mapper.mapToGstFormat(af)
                );

                preloadedDrops.put(fileUrl, cached);

                Log.infof("Cached wav file [%s] into memory as PCM", fileUrl);

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (UnsupportedAudioFileException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public List<String> getDrops() {
        return new ArrayList<>(preloadedDrops.keySet());
    }

    private List<String> listWavsOnDisk() {
        try {
            URL url = getClass().getClassLoader().getResource("sounds");
            if (url == null) return List.of();

            File dir = new File(url.toURI());
            if (!dir.exists() || !dir.isDirectory()) return List.of();

            return Arrays.stream(dir.listFiles())
                    .filter(f -> f.isFile() && f.getName().toLowerCase().endsWith(".wav"))
                    .map(File::getName)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public CachedWav getDataFor(String dropName) {
            return preloadedDrops.get(dropName);
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
