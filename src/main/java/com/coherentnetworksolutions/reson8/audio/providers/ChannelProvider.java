package com.coherentnetworksolutions.reson8.audio.providers;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;

public interface ChannelProvider {
    void populate(Mixer mixer);
}