package com.coherentnetworksolutions.reson8.audio.input;

import java.nio.ByteBuffer;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.elements.AppSrc;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.factories.DropFactory;
import com.coherentnetworksolutions.reson8.audio.factories.DropFactory.CachedWav;
import com.coherentnetworksolutions.reson8.signal.SignalEndpoint;

import io.quarkus.logging.Log;

/**
 * A persistent channel for 'Drop' type sounds. 
 * It stays connected to the Mixer but only pushes data when triggered.
 */
public class OneShotChannel implements InputChannel {
    private final String channelName;
    private final CachedWav cachedPcmData;
    private final Mixer mixer;
    private final AppSrc srcElement;
    private double volume;

    public OneShotChannel(SignalEndpoint signalEndpoint, DropFactory dropFactory, Mixer mixer) {
        this.channelName = signalEndpoint.getName();
        this.mixer = mixer;
        this.cachedPcmData = dropFactory.getDataFor(signalEndpoint.getSoundDefinition().drop().map(d -> d.filename()).orElseThrow());
        this.volume = signalEndpoint.getSoundDefinition().drop().map(d -> d.gain()).orElse(1.0);

        if (this.cachedPcmData == null) {
            throw new IllegalArgumentException("No PCM data provided for [" + channelName + "]");
        }

        // 1. Setup GStreamer AppSrc
        this.srcElement = (AppSrc) ElementFactory.make("appsrc", channelName + "_src_" + System.nanoTime());
        
        // 2. Configure for "on-demand" pushing
        srcElement.set("format", Format.TIME);
        srcElement.set("is-live", false); 
        srcElement.set("do-timestamp", true);
        // srcElement.set("volume", volume);
        
        // Handling Mono vs Stereo bitmask
        String channelMask = (cachedPcmData.channels == 1) ? "0x0" : "0x3";
        String capsStr = String.format("audio/x-raw,format=%s,channels=%d,rate=%d,layout=interleaved,channel-mask=(bitmask)%s",
                cachedPcmData.format, cachedPcmData.channels, (int)cachedPcmData.sampleRate, channelMask);
        
        srcElement.setCaps(Caps.fromString(capsStr));

        Log.debugf("OneShotChannel [%s] registered and ready.", channelName);
    }


    /**
     * Triggered by the MappingManager or SPA.
     * This 'fires' the drop.
     */
    public void trigger() {
        Log.infof("Triggering drop: %s", channelName);
        pushData();
    }

    private void pushData() {
        byte[] data = cachedPcmData.pcmData;

        // Calculate bytes per frame to ensure alignment
        // (BitsPerSample / 8) * Channels
        int bytesPerSample = 2; // Assuming S16LE, otherwise extract from cachedPcmData.format
        int bytesPerFrame = bytesPerSample * cachedPcmData.channels;
        int safeLength = data.length - (data.length % bytesPerFrame);

        Buffer buffer = new Buffer(safeLength);
        buffer.map(true).put(ByteBuffer.wrap(data, 0, safeLength));
        buffer.unmap();
        
        // Presentation logic: Play relative to current pipeline time
        long runningTime = mixer.getPipeline().getClock().getTime();
        long baseTime = mixer.getPipeline().getBaseTime();
        buffer.setPresentationTimestamp(runningTime - baseTime);
        
        srcElement.pushBuffer(buffer);

        // Note: We do NOT call endOfStream() here because we want to reuse 
        // this srcElement for the next trigger. 
    }

    @Override
    public void start() {
        // No-op for OneShot; it waits for trigger()
        Log.debugf("OneShotChannel [%s] started (waiting for triggers).", channelName);
    }

    @Override
    public String getChannelName() { return channelName; }

    @Override
    public void setGain(double volume) { 
        this.volume = volume; 
        // srcElement.set("volume", volume);        
    }

    @Override
    public double getGain() { return volume; }

    @Override
    public Element getSrcElement() { return srcElement; }


    @Override
    public boolean supportsGain() { return true; }

    @Override
    public boolean supportsIntensity() { return false; }

    @Override 
    public void setIntensity(double intensity) { }

    @Override
    public double getIntensity() { return 0.0; }

    @Override
    public void dispose() {
        if (srcElement != null) {
            srcElement.endOfStream();
        }
    }
}