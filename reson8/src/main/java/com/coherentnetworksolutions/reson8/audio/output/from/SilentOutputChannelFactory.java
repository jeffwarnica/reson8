package com.coherentnetworksolutions.reson8.audio.output.from;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "reson8dev.audiopath", stringValue = "silent")
public class SilentOutputChannelFactory implements ClientChannelFactory {

    public MixerOutputToClientManagerChannel create(String kind, String channelName, double volume) {
        return new SilentMixerToBrowserManagerChannel(kind, channelName, volume);
    }
    
}
