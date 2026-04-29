package com.coherentnetworksolutions.reson8.audio.sound;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.*;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * SoundManager holds our SoundDefinitions, allowing for lookup by "path"
 */
@ApplicationScoped
public class SoundDefinitionRegistry {
    
    private final Map<String, SoundDefinition> registry = new ConcurrentHashMap<>();

    @Inject
    Reson8Config config;

    @Inject
    InputChannelFactory channelFactory;

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

    // public void register(String configPath, SoundDefinition def) {
    //     Log.debugf("Registering sound [%s]", configPath);
    //     registry.put(configPath, def);
    // }

    
    /**
     * @param configPath To get {@SoundDefinition} by path
     * @return
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