package com.coherentnetworksolutions.reson8.signal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.sound.SoundDefinitionRegistry;
import com.coherentnetworksolutions.reson8.audio.utils.map.CurveMapFactory;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundDefinition;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;

import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SignalManager {
    @Inject Reson8Config config;
    @Inject SoundDefinitionRegistry soundDefinitionRegistry;
    @Inject private InputChannelFactory channelFactory;
    @Inject Mixer mixer;
    @Inject EventBus eventBus;
    @Inject CurveMapFactory curveFactory;

    private final Map<String, SignalBucket> buckets = new ConcurrentHashMap<>();
    private volatile boolean ready = false;
    private volatile boolean k8sSync = true;

    private final AtomicBoolean isWired = new AtomicBoolean(false);

    private static final Configuration JSON_NODE_CONF = Configuration.builder()
        .jsonProvider(new JacksonJsonNodeJsonProvider())
        .options(Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS)
        .build();
  

    @PostConstruct
    public void onStart() {
        Log.debug("onStart() starting");
        String defaultScape = config.signalMap().defaultSoundscape();

        config.signalMap().inputs().forEach(mapping -> {
            String rawSound = mapping.sound();
            String fullSoundPath = rawSound.contains("/") ? rawSound : defaultScape + "/" + rawSound;
            SoundDefinition soundDefinition = soundDefinitionRegistry.get(fullSoundPath);

            // Create the "Rich" object
            SignalBucket bucket = new SignalBucket(mapping, soundDefinition, channelFactory, 
                soundDefinitionRegistry, this, curveFactory);

            // Store it in our runtime registry
            buckets.put(bucket.getName(), bucket);

            Log.infof("Mapped Signal [%s] -> [%s]",
                    bucket.getName(), fullSoundPath);
        });

        attemptWiring();
        this.ready = true;
        eventBus.publish("signalmap-ready", null);

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

    @ConsumeEvent("k8s-sync-enable")
    public void setK8sSyncEnabled(boolean state) {
        Log.debugf("Updating sync mode to [%s]", state);
        k8sSync = state;
    }

    public SignalBucket getSignalBucket(String bucket) {
        return buckets.get(bucket);
    }
    
    public Collection<SignalBucket> getSignalBuckets() {
        return buckets.values();
    }

    public Optional<SignalBucket> getBucketByMetric(Reson8Config.ClusterMetric metric) {
        return buckets.values().stream()
            .filter(b -> b.getMetric() == metric)
            .findFirst();
    }

    public void updateSignalIntensityFromRaw(String signalId, double rawValue) {
        SignalBucket bucket = buckets.get(signalId);
        if (bucket == null) {
            Log.warnf("No bucket found for signal [%s]", signalId);
            return;
        }
        double intensity = bucket.getCurve().map(rawValue);
        bucket.setIntensity(intensity);
        Log.debugf("Updated intensity for signal [%s] to [%f] from raw [%f]", signalId, intensity, rawValue);
    }

    public void processEvent(io.fabric8.kubernetes.api.model.events.v1.Event event) {
        Log.debugf("Signal Manager checking event [%s]", event);

        JsonNode eventNode = Serialization.jsonMapper().valueToTree(event);
        Log.debugf("Event as JsonNode: [%s]", eventNode);

        List<String> matchedTo = new ArrayList<String>();

        for (SignalBucket bucket : buckets.values()) {
            if (bucket.getSourceType() == SourceType.KUBERNETES_EVENT) {
                ArrayNode bucketMatch = bucket.getJsonPath().read(eventNode, JSON_NODE_CONF);
                
                if (!bucketMatch.isEmpty()) {
                    matchedTo.add(bucket.getName());
                    bucket.trigger();
                }
            }
        }
        
        Log.debugf("Signal Manager] processed event [%s] and matched [%s]", event, matchedTo);
        
    }

    /**
     * Updates the intensity of a signal based on an external input (e.g., Thanos metric value).
     * This method will do the translation from external to 1-100% level and then update the appropriate SignalBucket, which will in turn update the associated InputChannel's intensity.
     * @param signalId
     * @param promVal
     */
    public void updateSignalIntensityFromPromVal(String signalId, double promVal) {
        SignalBucket bucket = buckets.get(signalId);
        double intensity = bucket.getCurve().map(promVal);
        
        bucket.setIntensity(intensity);
        Log.debugf("Updated intensity for signal [%s] to [%f] from promVal [%f]", signalId, intensity, promVal);
    }

    private void attemptWiring() {
        Log.debug("attempting");
        if (mixer.isReady() && isWired.compareAndSet(false, true)) {
            try {
                Log.info("Wiring buckets to the Mixer...");
                
                buckets.values().stream()
                .filter(entry -> {
                    Log.debugf("considering channel [%s] of type [%s]", entry.getName(), entry.getSourceType());
                    return entry.getSourceType() == SourceType.KUBERNETES_EVENT
                    || entry.getSourceType() == SourceType.KUBERNETES_STATS
                    || entry.getSourceType() == SourceType.PROMETHEUS;
                })
                .forEach(entry -> {
                    // Find the associated sound and give it a default "heartbeat" intensity
                    InputChannel channel = entry.getInputChannel();
                    if (channel != null) {
                        Log.debugf("starting channel [%s]", channel.getChannelName());
                        // Ensure it's started so the pipeline can PLAY
                        mixer.addInputChannel(channel);
                        mixer.setInputChannelVolume(channel.getChannelName(), 80.0);
                        channel.setTargetIntensity(50);
                        channel.start();
                    } else {
                        Log.warnf("Signal Endpoint [%s] has no associated channel and will not be played", entry.getName());
                    }
                });
                eventBus.publish("signalManager-ready", null);
            } catch (Exception e) {
                Log.error("Failed to wire Signal Buckets to Mixer", e); 
                isWired.set(false);
            }
        }
    }
        
    public boolean isK8sSyncEnabled() {
        return k8sSync;
    }
    
    public boolean isReady() {
        return ready;
    }


}
