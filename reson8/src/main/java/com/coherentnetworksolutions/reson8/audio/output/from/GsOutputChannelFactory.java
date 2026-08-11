package com.coherentnetworksolutions.reson8.audio.output.from;

import com.coherentnetworksolutions.reson8.audio.output.to.GsBrowserSessionManager;
import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@io.quarkus.arc.properties.IfBuildProperty(name = "reson8dev.audiopath", stringValue = "gs")
public class GsOutputChannelFactory implements ClientChannelFactory {

    @Inject
    GsBrowserSessionManager browserSessionManager;

    @Inject
    GsToolkit toolkit;

    @Override
    public MixerOutputToClientManagerChannel create(String kind, String channelName, double volume) {
        MixerOutputToClientManagerChannel channel = null;
    
        switch (kind) {
            case "browser":
                channel = createBrowserChannel(channelName, volume);
                break;
            // Add more cases here for other
            default:
                throw new UnsupportedOperationException("Unsupported otuput kind: " + kind);
        }
        return channel;
    }
    
    private MixerOutputToClientManagerChannel createBrowserChannel(String channelName, double volume) {
        GsMixerToBrowserManagerChannel boc = new GsMixerToBrowserManagerChannel(channelName, browserSessionManager, toolkit);
        // mixer.setOutputChannelVolume(channelName, volume);
        return boc;
    }
}
