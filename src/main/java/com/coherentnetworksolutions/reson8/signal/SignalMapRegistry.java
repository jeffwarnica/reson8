package com.coherentnetworksolutions.reson8.signal;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.sound.SoundRegistry;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundDefinition;

import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


/**
 * "registry": doesn't handle flow
 */
@ApplicationScoped
public class SignalMapRegistry {
    @Inject
    Reson8Config config;
    private final Map<String, SignalBucket> buckets = new ConcurrentHashMap<>();
    @Inject
    private InputChannelFactory channelFactory;
    @Inject
    private SoundRegistry soundRegistry;
    @Inject
    EventBus eventBus;
    public Boolean ready = false;
    @Inject
    private SignalManager signalManager;

    @PostConstruct
    public void onStart(/*@Observes StartupEvent ev*/) {
        Log.debug("onStart() starting");
        String defaultScape = config.signalMap().defaultSoundscape();

        config.signalMap().inputs().forEach(mapping -> {
            String rawSound = mapping.sound();
            String fullSoundPath = rawSound.contains("/") ? rawSound : defaultScape + "/" + rawSound;
            SoundDefinition soundDefinition = soundRegistry.get(fullSoundPath);

            // Create the "Rich" object
            SignalBucket bucket = new SignalBucket(mapping, soundDefinition, channelFactory, soundRegistry, signalManager);

            // Store it in our runtime registry
            buckets.put(bucket.getName(), bucket);

            Log.infof("Mapped Signal [%s] -> [%s]",
                    bucket.getName(), fullSoundPath);
        });
        this.ready = true;
        eventBus.publish("signalmap-ready", null);

    }

    @Override
    public String toString() {
    return buckets.entrySet()
              .stream()
              .map(e -> e.getKey() + "=" + e.getValue())
              .collect(Collectors.joining("\n"));
    }

    public Collection<SignalBucket> getBuckets() {
        return buckets.values();
    }

    public SignalBucket getBucket(String bucketName) {
        Log.debugf("looking for [%s]", bucketName);
        return buckets.get(bucketName);
    }

    public boolean isReady() {
        return ready;
    }

}