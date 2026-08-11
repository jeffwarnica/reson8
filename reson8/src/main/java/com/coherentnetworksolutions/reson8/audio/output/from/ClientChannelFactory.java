package com.coherentnetworksolutions.reson8.audio.output.from;

public interface ClientChannelFactory {

    MixerOutputToClientManagerChannel create(String kind, String channelName, double volume);

}