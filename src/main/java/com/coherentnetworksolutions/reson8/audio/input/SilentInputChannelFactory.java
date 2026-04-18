package com.coherentnetworksolutions.reson8.audio.input;

import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundType;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "reson8dev.audiopath", stringValue = "silent")
public class SilentInputChannelFactory implements InputChannelFactory {

    @PostConstruct
    public void onStart() {
        Log.debug("I'm here");
    }

    @Override
    public InputChannel buildChannel(SignalBucket signalEndpoint) {
        if (signalEndpoint.getSoundType() == SoundType.DROP) {
            return new SilentDropChannel(signalEndpoint);
        }
        return new SilentInputChannel(signalEndpoint);
    }
    
}
