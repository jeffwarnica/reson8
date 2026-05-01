package com.coherentnetworksolutions.reson8.audio.input;

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
    public Object getSrcElement() {
        throw new UnsupportedOperationException("SilentDropChannel has no native source element");
    }

    @Override
    public String getCapsString() {
        throw new UnsupportedOperationException("SilentDropChannel has no caps");
    }
}
