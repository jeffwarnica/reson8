package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;

import jakarta.inject.Inject;

public class GenericNoiseChanel implements InputChannel {
    private final String channelName;
    private double volume;
    private Element srcElement;
    private Float freq = 440.0f; // the default, anyway

    @Inject
    Mixer mixer; // Injected mixer, if necessary

    // Constructor to inject dependencies
    public GenericNoiseChanel(String channelName, double volume, NoiseType type) {
        this.channelName = channelName;
        this.volume = volume;

        srcElement = ElementFactory.make("audiotestsrc", channelName);
        srcElement.set("wave", type.getId()); 
        srcElement.set("is-live", true); // Live flag
        srcElement.set("do-timestamp", true);
        srcElement.set("freq", freq);// 
        
    }

    @Override
    public void start() {
        // Create the pink noise element in the start method

        // Create and apply caps (audio format, channels, rate)
        // Caps pinkNoiseCaps = Caps.fromString("audio/x-raw, format=(string)S16LE,
        // channels=(int)3, channel-mask=(int)0x4, rate=(int)48000");
        // Pad srcPad = element.getStaticPad("src");
        // srcPad.set("caps", pinkNoiseCaps); // Apply the caps to the src pad
    }

    @Override
    public String getChannelName() {
        return channelName;
    }

    @Override
    public double getGain() {
        return volume;
    }

    @Override
    public Element getSrcElement() {
        return srcElement;
    }

    @Override
    public void setGain(double volume) {
        this.volume = volume;
        srcElement.set("volume", volume);
    }

    public void setFreq(Float freq){
        this.freq = freq;
        srcElement.set("freq", freq);
    }

    public enum NoiseType {
        SINE(0),
        SQUARE(1),
        SAW(2),
        TRIANGLE(3),
        SILENCE(4),
        WHITE(5),
        PINK(6),
        RED(9),
        BLUE(10);

        private final int id;

        NoiseType(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    @Override
    public boolean supportsGain() {
        return false;
    }
}