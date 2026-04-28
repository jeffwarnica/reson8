package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;

import com.coherentnetworksolutions.reson8.audio.providers.GstToolkit;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class SilentInputChannel implements InputChannel {

    private String channelName;
    private @Min(0) @Max(100) double intensity;
    private @Min(0) @Max(100) double gain;
    private GstToolkit toolkit;

    public SilentInputChannel(SignalBucket signalEndpoint, GstToolkit toolkit) {
        channelName = signalEndpoint.getName();
        this.toolkit = toolkit;
    }

    @Override
    public void start() {

    }

    @Override
    public String getChannelName() {
        return channelName;
    }

    @Override
    public double getGain() {
        return gain;
    }

    // @Override
    // public boolean supportsGain() { return true; }
    // @Override
    // public boolean supportsIntensity() {return true;}

    @Override
    public void setGain(@Min(0) @Max(100) double gain) {
        this.gain = gain;
    }

    @Override
    public Element getSrcElement() {
        return null;

    }

    @Override
    public Caps getCaps() {
        return toolkit.capsFromString("audio/x-raw");
    }

    @Override
    public void dispose() {
    }

    @Override
    public void setTargetIntensity(@Min(0) @Max(100) double intensity) {
        this.intensity = intensity;
    }

    @Override
    public double getTargetIntensity() {
        return intensity;
    }

    @Override
    public double getCurrentIntensity() {
        return intensity;
    }

}
