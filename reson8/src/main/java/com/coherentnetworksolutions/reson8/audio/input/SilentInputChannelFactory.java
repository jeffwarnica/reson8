package com.coherentnetworksolutions.reson8.audio.input;

import com.coherentnetworksolutions.reson8.signal.SignalBucket;
import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundType;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "reson8dev.audiopath", stringValue = "silent")
public class SilentInputChannelFactory implements InputChannelFactory {


    @Inject public GsToolkit toolkit;

    @Override
    public InputChannel buildChannel(SignalBucket signalBucket) {
        if (signalBucket.getSoundType() == SoundType.DROP) {
            return new SilentDropChannel(signalBucket);
        }
        return new SilentInputChannel(signalBucket, toolkit);
    }
    
}
