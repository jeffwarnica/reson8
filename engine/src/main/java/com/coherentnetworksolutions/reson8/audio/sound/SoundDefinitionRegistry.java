package com.coherentnetworksolutions.reson8.audio.sound;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.*;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
  * Holds registered {@link SoundDefinition}s, allowing lookup by config path.
 */
@ApplicationScoped
public class SoundDefinitionRegistry {
    
    private final Map<String, SoundDefinition> registry = new ConcurrentHashMap<>();

    @Inject
    Reson8Config config;

    @PostConstruct
    public void onStart() {
        config.soundscapes().stream()
            .forEach(soundscape -> {
                soundscape.sounds().stream()
                    .forEach( soundDef-> {
                        String configPath = soundscape.name() + "/" + soundDef.name();
                        registry.put(configPath, soundDef);
                    });
            });
    }

    /**
     * @param configPath the key used to look up the sound (format: {@code soundscape/sound})
     */
    public SoundDefinition get(String configPath) {
        Log.debugf("Looking for sound definition: [%s]", configPath);
        return registry.get(configPath);
    }

    @Override
    public String toString() {
    return registry.entrySet()
              .stream()
              .map(e -> e.getKey() + " (" + e.getValue().soundType() + ")")
              .collect(Collectors.joining("\n"));
    }

}