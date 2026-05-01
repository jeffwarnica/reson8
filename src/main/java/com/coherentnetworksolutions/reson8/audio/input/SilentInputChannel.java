package com.coherentnetworksolutions.reson8.audio.input;

import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

/**
 * No-op {@link InputChannel} for the {@code silent} audio profile. Extends
 * {@link BaseInputChannel} so all clamping, smoothing, and state management is
 * exercised by the same code path as the real GStreamer implementations.
 * <p>
 * The {@code GsToolkit} parameter is accepted for call-site compatibility but is
 * not used; the silent profile requires no native GStreamer interaction.
 */
public class SilentInputChannel extends BaseInputChannel {

    public SilentInputChannel(SignalBucket signalEndpoint, GsToolkit toolkit) {
        super(signalEndpoint.getName(), 0.0, 0.0);
    }

    @Override
    public void start() {}

    @Override
    public void dispose() {}

    @Override
    public Object getSrcElement() {
        return null;
    }

    @Override
    public String getCapsString() {
        return "audio/x-raw";
    }
}
