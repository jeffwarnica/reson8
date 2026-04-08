package com.coherentnetworksolutions.reson8.audio.input;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.output.BrowserOutputChannel;
import com.coherentnetworksolutions.reson8.audio.output.BrowserSessionManager;
import com.coherentnetworksolutions.reson8.audio.output.OutputChannel;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class OutputChannelFactory {

    @Inject
    private Mixer mixer;

    @Inject
    BrowserSessionManager browserSessionManager;
    
    public OutputChannel create(String kind, String channelName, double volume) {
        OutputChannel channel = null;
    
        // Create the appropriate noise generator based on the kind
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
    
    private OutputChannel createBrowserChannel(String channelName, double volume) {
        BrowserOutputChannel boc = new BrowserOutputChannel(channelName, browserSessionManager);
        mixer.setOutputChannelVolume(channelName, volume);
        return boc;
    }
}
