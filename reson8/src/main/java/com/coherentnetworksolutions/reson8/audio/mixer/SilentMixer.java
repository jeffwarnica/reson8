package com.coherentnetworksolutions.reson8.audio.mixer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.output.from.MixerOutputToClientManagerChannel;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name="reson8dev.audiopath", stringValue = "silent")
public class SilentMixer implements Mixer {
    private final Map<String, InputChannel> inputChannels = new HashMap<>(); 
    private final Map<String, MixerOutputToClientManagerChannel> outputChannels = new HashMap<>();     
    private MixerOutputToClientManagerChannel masterOutputChannel;
    private double volume;
    
    @Override
    public void initGStreamer() {
        snark();
    }

    @Override
    public void start() {
        snark();
    }

    @Override
    public void stop() {
        snark();
    }

    @Override
    public boolean isReady() {
        Log.debug("I was born ready.");
        return true;
    }

    @Override
    public void addInputChannel(InputChannel inputChannel) {
        inputChannels.put(inputChannel.getChannelName(), inputChannel);
    }

    @Override
    public void addOutputChannel(MixerOutputToClientManagerChannel outputChannel) {
        if (masterOutputChannel == null) {
            masterOutputChannel = outputChannel;
        }
        outputChannels.put(outputChannel.getChannelName(), outputChannel);
    }

    @Override
    public void setMasterVolume(double uiVolume) {
        volume = Math.max(0.0, Math.min(100.0, uiVolume));
    }

    @Override
    public double getMasterVolume() {
        return 0.0;
    }

    @Override
    public void setInputChannelVolume(String channelName, double uiVolume) {
        volume = Math.max(0.0, Math.min(100.0, uiVolume));
    }

    @Override
    public double getInputChannelVolume(String channelName) {
        return volume;
    }

    @Override
    public void setOutputChannelVolume(String channelName, double uiVolume) {
        volume = Math.max(0.0, Math.min(100.0, uiVolume));
        Log.debugf("SilentMixer ignored setOutputChannelVolume for [%s] at [%s]", channelName, volume);
    }

    @Override
    public InputChannel getInputChannel(String channelName) {
        return inputChannels.get(channelName);
    }

    @Override
    public MixerOutputToClientManagerChannel getOutputChannel(String channelName) {
        return outputChannels.get(channelName);
    }

    @Override
    public Map<String, InputChannel> getInputChannels() {
        return Map.copyOf(inputChannels);
    }

    @Override
    public Map<String, MixerOutputToClientManagerChannel> getOutputChannels() {
        return Map.copyOf(outputChannels);
    }

    @Override
    public Object getMixerElement() {
        return null;
    }

    @Override
    public Object getPipeline() {
        return null;
    }

    @Override
    public void removeInputChannel(String channelName) {
        inputChannels.remove(channelName);
    }

    @Override
    public void removeOutputChannel(String channelName) {
        outputChannels.remove(channelName);
    }

    @Override
    public MixerOutputToClientManagerChannel getMasterOutput() {
        return masterOutputChannel;
    }

    @Override
    public void setupDropCleanup() {
        snark();
    }

    @Override
    public void setupDebugStuff() {
        snark();
    }

    @Override
    public List<Object> dumpAllElements() {
        return List.of();
    }

    @Override
    public void dispose() {
        inputChannels.clear();
        outputChannels.clear();
        masterOutputChannel = null;
        volume = 0.0;
        Log.debug("SilentMixer dispose() completed as no-op cleanup.");
    }

    @Override
    public String dumpMixerState() {
        String state = "SilentMixer{inputs=%d, outputs=%d, volume=%.2f}"
            .formatted(inputChannels.size(), outputChannels.size(), volume);
        Log.debug(state);
        return state;
    }

    private void snark() {
        String caller = Thread.currentThread()
            .getStackTrace()[2]
            .getMethodName();
        Log.debugf("[%s] - easy to do nothing", caller);
    }
    
}
