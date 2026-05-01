package com.coherentnetworksolutions.reson8.audio.mixer;

import java.util.List;
import java.util.Map;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.output.from.MixerOutputToClientManagerChannel;

/**
 * Domain interface for the audio mixer. No GStreamer types ({@code Element},
 * {@code Pipeline}) appear in this interface; callers outside {@code audio.mixer}
 * and {@code audio.providers} do not require gst1-java-core on the classpath.
 * {@code GsMixer} casts the {@code Object} return values of the low-level accessors
 * at the point of use.
 */
public interface Mixer {

    String CAPS = "audio/x-raw,format=S16LE,layout=interleaved,channels=2,rate=48000,channel-mask=(bitmask)0x3";

    String CHANNEL_DELINEATOR = "::";

    void initGStreamer();

    void start();

    void stop();

    boolean isReady();

    void addInputChannel(InputChannel inputChannel);

    void addOutputChannel(MixerOutputToClientManagerChannel outputChannel);

    void setMasterVolume(double uiVolume);

    double getMasterVolume();

    void setInputChannelVolume(String channelName, double uiVolume);

    double getInputChannelVolume(String channelName);

    void setOutputChannelVolume(String channelName, double uiVolume);

    InputChannel getInputChannel(String channelName);

    MixerOutputToClientManagerChannel getOutputChannel(String channelName);

    /**
     * Returns a read-only snapshot of currently registered input channels.
     */
    Map<String, InputChannel> getInputChannels();

    /**
     * Returns a read-only snapshot of currently registered output channels.
     */
    Map<String, MixerOutputToClientManagerChannel> getOutputChannels();

    /**
     * Returns the underlying native mixer element. Typed as {@link Object} to keep
     * GStreamer types out of this interface; {@code GsMixer} callers cast as needed.
     */
    Object getMixerElement();

    /**
     * Returns the underlying native pipeline. Typed as {@link Object} to keep
     * GStreamer types out of this interface; {@code GsMixer} callers cast as needed.
     */
    Object getPipeline();

    void removeInputChannel(String channelName);

    void removeOutputChannel(String channelName);

    MixerOutputToClientManagerChannel getMasterOutput();

    /** Registers a bus watcher that removes one-shot drop channels after EOS. */
    void setupDropCleanup();

    void setupDebugStuff();

    /**
     * Returns all pipeline elements for diagnostics. Typed as {@link List}{@code <Object>}
     * to keep GStreamer types out of the interface.
     */
    List<Object> dumpAllElements();

    void dispose();

    String dumpMixerState();
}