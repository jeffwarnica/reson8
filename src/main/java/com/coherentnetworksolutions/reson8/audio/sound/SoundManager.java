package com.coherentnetworksolutions.reson8.audio.sound;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.coherentnetworksolutions.reson8.audio.factories.ChannelFactory;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.*;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * SoundManager holds our SoundDefinitions, allowing for lookup by "path"
 */
@ApplicationScoped
public class SoundManager {
    // Map of "soundscape/soundName" -> SoundDefinition
    private final Map<String, SoundDefinition> registry = new ConcurrentHashMap<>();
    @Inject
    Reson8Config config;

    @Inject
    ChannelFactory channelFactory;

    public void onStart() {
        config.soundscapes().stream()
            .forEach(soundscape -> {
                soundscape.sounds().stream()
                    .forEach( soundDef-> {
                        String effectivePath = soundscape.name() + "/" + soundDef.name();
                        registry.put(effectivePath, soundDef);
                    });
            });
    }

    public void register(String path, SoundDefinition def) {
        Log.debugf("Registering sound [%s]", path);
        registry.put(path, def);
    }

    
    /**
     * @param path To get {@SoundDefinition} by path
     * @return
     */
    public SoundDefinition get(String path) {
        Log.debugf("Looking for sound definition: [%s]", path);
        return registry.get(path);
    }

    @Override
    public String toString() {
    return registry.entrySet()
              .stream()
              .map(e -> e.getKey() + " (" + e.getValue().type() + ")")
              .collect(Collectors.joining("\n"));
    }

}