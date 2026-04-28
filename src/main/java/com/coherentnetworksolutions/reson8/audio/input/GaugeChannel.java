package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Element;

public interface GaugeChannel extends InputChannel {


    String getChannelName();

    Element getSrcElement();

}
