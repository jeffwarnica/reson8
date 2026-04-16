package com.coherentnetworksolutions.reson8.audio.providers;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;

public interface ChannelProvider {
    void populate(Mixer mixer);
}