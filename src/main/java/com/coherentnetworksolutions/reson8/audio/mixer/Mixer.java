package com.coherentnetworksolutions.reson8.audio.mixer;

import java.util.List;
import java.util.Map;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pipeline;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.output.from.MixerOutputToClientManagerChannel;

public interface Mixer {

    String CAPS = "audio/x-raw,format=S16LE,layout=interleaved,channels=2,rate=48000,channel-mask=(bitmask)0x3";

    String CHANNEL_DELINEATOR = "::";

    void initGStreamer();

    // Start the entire pipeline (play audio)
    void start();

    // Stop the pipeline
    void stop();

    boolean isReady();

    void addInputChannel(InputChannel inputChannel);

    void addOutputChannel(MixerOutputToClientManagerChannel outputChannel);

    // Set the master volume for the mixer
    void setMasterVolume(double uiVolume);

    double getMasterVolume();

    void setInputChannelVolume(String channelName, double uiVolume);

    double getInputChannelVolume(String channelName);

    // Set the volume for a specific output channel
    void setOutputChannelVolume(String channelName, double uiVolume);

    // Method to retrieve an input channel by name
    InputChannel getInputChannel(String channelName);

    // Method to retrieve an output channel by name
    MixerOutputToClientManagerChannel getOutputChannel(String channelName);

    // Method to retrieve an input channel by name
    Map<String, InputChannel> getInputChannels();

    // Method to retrieve an output channel by name
    Map<String, MixerOutputToClientManagerChannel> getOutputChannels();

    Element getMixerElement();

    Pipeline getPipeline();

    void removeInputChannel(String channelName);

    void removeOutputChannel(String channelName);

    MixerOutputToClientManagerChannel getMasterOutput();

    /**
     * Add a bus watcher that looks for our one-shot drops ending, and then removes that channel
     */
    void setupDropCleanup();

    void setupDebugStuff();

    List<Element> dumpAllElements();

    void dispose();

    String dumpMixerState();

}