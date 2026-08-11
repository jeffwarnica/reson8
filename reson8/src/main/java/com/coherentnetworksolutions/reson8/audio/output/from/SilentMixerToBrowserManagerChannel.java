package com.coherentnetworksolutions.reson8.audio.output.from;

import org.freedesktop.gstreamer.Element;

import io.smallrye.mutiny.Multi;

public class SilentMixerToBrowserManagerChannel implements MixerOutputToClientManagerChannel {

    private final String channelName;

    public SilentMixerToBrowserManagerChannel(String kind, String channelName, double volume) {
        this.channelName = channelName;
    }

    @Override
    public String getChannelName() {
        return channelName;
    }

    @Override
    public Element getElement() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getElement'");
    }

    @Override
    public Multi<byte[]> subscribe() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'subscribe'");
    }

    
}
