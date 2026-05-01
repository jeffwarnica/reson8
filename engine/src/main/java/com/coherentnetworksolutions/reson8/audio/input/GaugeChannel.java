package com.coherentnetworksolutions.reson8.audio.input;

public interface GaugeChannel extends InputChannel {

    String getChannelName();

    /** Returns the native source element; typed as {@link Object} — see {@link InputChannel#getSrcElement()}. */
    Object getSrcElement();
}
