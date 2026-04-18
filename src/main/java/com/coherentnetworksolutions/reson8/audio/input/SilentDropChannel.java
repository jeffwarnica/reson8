package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;

import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class SilentDropChannel implements DropChannel, InputChannel {

    private String channelName;
    
    public SilentDropChannel(SignalBucket signalEndpoint) {
        channelName = signalEndpoint.getName();
    }


    // public SilentDropChannel(SignalEndpoint signalEndpoint) {
    //     channelName = signalEndpoint.getName();
    // }

    @Override
    public void start() {

    }

    @Override
    public String getChannelName() {
        return channelName;
    }

    @Override
    public double getGain() {
        return 6.66;
    }

    // @Override
    // public boolean supportsGain() {return true;    }
    // @Override
    // public boolean supportsIntensity() { return true;}

    @Override
    public void setGain(@Min(0) @Max(100) double volume) {
        return;
    }

    @Override
    public Element getSrcElement() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSrcElement'");
    }

    @Override
    public Caps getCaps() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCaps'");
    }


    @Override
    public void dispose() {}

    @Override
    public void setIntensity(@Min(0) @Max(100) double d) {
        return;
    }

    @Override
    public double getIntensity() {
        return 6.66;
    }


    @Override
    public void trigger(double volume) {
        Log.debug("BANG");
    }

    
}
