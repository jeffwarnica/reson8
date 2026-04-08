package com.coherentnetworksolutions.reson8.audio.factory;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.OneShotDropChannel;
import com.coherentnetworksolutions.reson8.controllers.DropController;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class OneShotDropFactory {
   
    @Inject
    private DropController dropController;

    @Inject
    private Mixer mixer;

    public OneShotDropChannel create(String channelName, String dropName, double volume) {
        return new OneShotDropChannel(channelName, dropName, volume, dropController, mixer);
    }

    /**
     * The "Fire and Forget" method.
     * Looks up the wav, builds the GStreamer branch, adds it to the mixer, and starts playback.
     */
    public void playDrop(String dropName, double volume ) {
        
        // Generate a unique ID for this specific playback instance
        String channelName = "drop-" + dropName + "-" + System.nanoTime();

        // 1. Construct the channel (internalizing the GStreamer element creation)
        OneShotDropChannel channel = new OneShotDropChannel(channelName, dropName, volume, dropController, mixer);

        // 2. Register it with the mixer (handles adding to pipeline and linking pads)
        mixer.addInputChannel(channel);

        // 3. Start pushing the actual PCM bytes
        channel.start();
        
        Log.debugf("Successfully triggered playback for %s", channelName);
    }    
}
