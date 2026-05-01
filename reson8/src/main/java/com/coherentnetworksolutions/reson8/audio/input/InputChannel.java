package com.coherentnetworksolutions.reson8.audio.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Domain interface for a mixer input channel. All types are toolkit-neutral:
 * no GStreamer ({@code Caps}, {@code Element}) types appear here. Implementations
 * in the {@code audio.input} package may hold GStreamer objects, but callers outside
 * that package need not depend on gst1-java-core.
 */
public interface InputChannel {
        void start();
        String getChannelName();
        double getCeiling();

        /**
         * Sets the output ceiling for this channel (0–100, human scale).
         * Capped to [0, 100] and converted to the GStreamer 0–1 range internally via
         * {@link com.coherentnetworksolutions.reson8.audio.utils.map.VolumeScaler}.
         *
         * @param ceiling output ceiling, 0–100
         */
        void setCeiling(@Min(0) @Max(100) double ceiling);

        /**
         * Returns the GStreamer caps string describing the PCM format of this channel
         * (e.g. {@code "audio/x-raw,format=S16LE,rate=48000,channels=2"}).
         * Callers that need a native {@code Caps} object must resolve it via
         * {@code GsToolkit.capsFromString(channel.getCapsString())}.
         */
        String getCapsString();

        /**
         * Returns the native GStreamer source element that feeds the mixer pipeline.
         * This method is intentionally typed as {@link Object} so that callers outside
         * the {@code audio.input} / {@code audio.mixer} packages do not require
         * gst1-java-core on the classpath. {@code GsMixer} casts the return value to
         * {@code org.freedesktop.gstreamer.Element} at the point of use.
         */
        Object getSrcElement();

        void dispose();

        /**
         * Sets the target intensity (what user/k8s wants), 0–100.
         */
        void setTargetIntensity(@Min(0) @Max(100) double d);

        /**
         * Gets the target intensity (what was set via setTargetIntensity), 0–100.
         */
        double getTargetIntensity();

        /**
         * Gets the current/observed intensity (may differ from target due to smoothing), 0–100.
         */
        double getCurrentIntensity();
}