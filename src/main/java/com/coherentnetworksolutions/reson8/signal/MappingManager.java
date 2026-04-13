package com.coherentnetworksolutions.reson8.signal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.factories.ChannelFactory;
import com.coherentnetworksolutions.reson8.audio.sound.SoundManager;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MappingManager {
    @Inject
    Reson8Config config;
    private final Map<String, SignalEndpoint> endpoints = new ConcurrentHashMap<>();
    @Inject
    private ChannelFactory channelManager;
    @Inject
    private SoundManager soundFactory;
    @Inject 
    private Mixer mixer;

    public void onStart(/*@Observes StartupEvent ev*/) {
        Log.debug("onStart() starting");
        String defaultScape = config.signalMap().defaultSoundscape();

        config.signalMap().inputs().forEach(mapping -> {
            // Create the "Rich" object
            SignalEndpoint endpoint = new SignalEndpoint(mapping, defaultScape, channelManager, soundFactory, mixer);

            // Store it in our runtime registry
            endpoints.put(endpoint.getName(), endpoint);

            Log.infof("Mapped Signal [%s] -> [%s]",
                    endpoint.getName(), endpoint.getFullSoundPath());
        });
    }

    @Override
    public String toString() {
    return endpoints.entrySet()
              .stream()
              .map(e -> e.getKey() + "=" + e.getValue())
              .collect(Collectors.joining("\n"));
    }

    public Map<String,SignalEndpoint> getEndpoints() {
        return endpoints;
    }

    public SignalEndpoint getEndpoint(String endpointName) {
        Log.debugf("looking for [%s]", endpointName);
        Log.debugf("Have: [%s]", endpoints.keySet());
        return endpoints.get(endpointName);
    }

}