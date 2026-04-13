package com.coherentnetworksolutions.reson8.signal;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.sound.SoundManager;
import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DummySignalProvider {
    @Inject
    MappingManager mappingManager;
    @Inject
    SoundManager soundRegistry;
    @Inject
    Mixer mixer;

    // @Scheduled(every = "1s")
    public void driveDummies() {
        mappingManager.getEndpoints().entrySet().stream()
                .filter(entry -> {
                    Log.debugf("considering channel [%s] of type [%s]", entry.getValue().getName(), entry.getValue().getSourceType());
                    return entry.getValue().getSourceType() == SourceType.DUMMY_LOOP
                        || entry.getValue().getSourceType() == SourceType.DUMMY_DROP
                        || entry.getValue().getSourceType() == SourceType.DUMMY_PROCEDURE;
                })
                .forEach(entry -> {
                    // Find the associated sound and give it a default "heartbeat" intensity
                    InputChannel channel = entry.getValue().getInputChannel();
                    Log.debugf("starting channel [%s]", channel.getChannelName());
                    if (channel != null) {
                        // Ensure it's started so the pipeline can PLAY
                        mixer.addInputChannel(channel);
                        mixer.setInputChannelVolume(channel.getChannelName(), 0.8);
                        // mixer.setInputChannelGain(channel.getChannelName(), channel.getGain());
                        // channel.setGain(entry.getValue().getGain());
                        // entry.getValue().getGain());
                        channel.setIntensity(0.5);
                        channel.start();
                    }
                });
    }
}
