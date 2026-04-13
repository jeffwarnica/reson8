package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Element;

public interface GaugeChannel extends InputChannel {

    void setIntensity(double intensity);

    String getChannelName();

    Element getSrcElement();

}
