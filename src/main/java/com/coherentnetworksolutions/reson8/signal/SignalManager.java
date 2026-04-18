package com.coherentnetworksolutions.reson8.signal;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.sound.SoundRegistry;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;

import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SignalManager {
    @Inject
    SignalMapRegistry signalMapRegistry;
    @Inject
    SoundRegistry soundRegistry;
    @Inject
    Mixer mixer;
    @Inject
    EventBus eventBus;

    private boolean k8sSync = true;

    private final AtomicBoolean isWired = new AtomicBoolean(false);

    // @Scheduled(every = "1s")
    @PostConstruct
    void setupPlan() {
        Log.debug("@PostConstruct woke me up");
        attemptWiring();
    }

    @ConsumeEvent("mixer-ready")
    void onMixerReady(String msg) {
        Log.debug("Got a mixer-ready signal");
        attemptWiring();
    }

    @ConsumeEvent("signalmap-ready")
    void onRegistryReady(String msg) {
        Log.debug("Got a signalmap-ready signal");
        attemptWiring();
    }    

    public SignalBucket getSignalBucket(String bucket) {
        return signalMapRegistry.getBucket(bucket);
    }
    
    public Collection<SignalBucket> getSignalBuckets() {
        return signalMapRegistry.getBuckets();
    }

    private void attemptWiring() {
        Log.debug("attempting");
        if (mixer.isReady() && signalMapRegistry.isReady() && isWired.compareAndSet(false, true)) {
            Log.info("Wiring buckets to the Mixer...");
            
            signalMapRegistry.getBuckets().stream()
                    .filter(entry -> {
                        Log.debugf("considering channel [%s] of type [%s]", entry.getName(), entry.getSourceType());
                        return entry.getSourceType() == SourceType.KUBERNETES_EVENT
                            || entry.getSourceType() == SourceType.KUBERNETES_STATS
                            || entry.getSourceType() == SourceType.PROMETHEUS;
                    })
                    .forEach(entry -> {
                        // Find the associated sound and give it a default "heartbeat" intensity
                        InputChannel channel = entry.getInputChannel();
                        Log.debugf("starting channel [%s]", channel.getChannelName());
                        if (channel != null) {
                            // Ensure it's started so the pipeline can PLAY
                            mixer.addInputChannel(channel);
                            mixer.setInputChannelVolume(channel.getChannelName(), 80.0);
                            // mixer.setInputChannelGain(channel.getChannelName(), channel.getGain());
                            // channel.setGain(entry.getGain());
                            // entry.getGain());
                            channel.setIntensity(50);
                            channel.start();
                        }
                    });
            eventBus.send("signalManager-ready", null);
            }
    }

    public boolean isK8sSyncEnabled() {
        return k8sSync;
    }

    public void setK8sSyncEnabled(boolean state)  {
        k8sSync = state;
    }

}
