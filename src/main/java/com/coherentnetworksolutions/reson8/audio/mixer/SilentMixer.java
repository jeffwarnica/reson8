package com.coherentnetworksolutions.reson8.audio.mixer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pipeline;

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
        return;
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setOutputChannelVolume'");
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
        return inputChannels;
    }

    @Override
    public Map<String, MixerOutputToClientManagerChannel> getOutputChannels() {
        return outputChannels;
    }

    @Override
    public Element getMixerElement() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMixerElement'");
    }

    @Override
    public Pipeline getPipeline() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPipeline'");
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
    public List<Element> dumpAllElements() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'dumpAllElements'");
    }

    @Override
    public void dispose() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'dispose'");
    }

    @Override
    public String dumpMixerState() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'dumpMixerState'");
    }

    private void snark() {
        String caller = Thread.currentThread()
            .getStackTrace()[2]
            .getMethodName();
        Log.debugf("[%s] - easy to do nothing", caller);
    }
    
}
