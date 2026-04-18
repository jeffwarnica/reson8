package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Element;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public interface GaugeChannel extends InputChannel {

    void setIntensity(@Min(0) @Max(100) double intensity);

    String getChannelName();

    Element getSrcElement();

}
