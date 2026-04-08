package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.State;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.GenericNoiseChanel.NoiseType;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NoiseGeneratorFactory {

    @Inject
    private Mixer mixer;  // Mixer injected here

    /**
     * Factory method to create different types of noise channels.
     * 
     * @param type The type of noise to create
     * @param channelName The name of the channel.
     * @param volume The volume for the noise generator.
     * @return The created InputChannel (e.g., PinkNoiseChannel).
     */
    public InputChannel create(NoiseType type, String channelName, double volume) {
        InputChannel channel = null;
        
        channel = new GenericNoiseChanel(channelName, volume, type);
                
        channel.getSrcElement().set("volume", volume);
        mixer.addInputChannel(channel);
        channel.getSrcElement().setState(State.PLAYING);

        Log.debugf("Created new noise generator of type [%s] calles [%s]", type, channelName);
        // Return the created channel
        return channel;
    }


}