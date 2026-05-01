package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;

import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;

public class SilentDropChannel extends BaseInputChannel implements DropChannel {

    public SilentDropChannel(SignalBucket signalEndpoint) {
        super(signalEndpoint.getName(), 0.0, 0.0);
    }

    @Override
    public void start() {}

    @Override
    public void dispose() {}

    @Override
    public void trigger() {
        Log.debug("BANG");
    }

    @Override
    public Element getSrcElement() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSrcElement'");
    }

    @Override
    public Caps getCaps() {
        throw new UnsupportedOperationException("Unimplemented method 'getCaps'");
    }
}
