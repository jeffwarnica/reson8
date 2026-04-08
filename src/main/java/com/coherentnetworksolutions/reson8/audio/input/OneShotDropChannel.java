package com.coherentnetworksolutions.reson8.audio.input;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.elements.AppSrc;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.controllers.DropController;
import com.coherentnetworksolutions.reson8.controllers.DropController.CachedWav;

import io.quarkus.logging.Log;

public class OneShotDropChannel implements InputChannel {
    private final String channelName;
    private final String dropName;
    private double volume;
    private AppSrc srcElement;

    // private DropController dropController;

    private Mixer mixer;
    private CachedWav cachedPcmData;

    /**
     * 
     * @param channelName
     * @param dropName
     * @param volume
     * @param dropController
     * @param mixer
     */
    public OneShotDropChannel(String channelName, String dropName, double volume, DropController dropController, Mixer mixer) {
        this.channelName = channelName;
        this.dropName = dropName;
        this.volume = volume;
        // this.dropController = dropController;
        this.mixer = mixer;

        // 1. Get cached data immediately to verify and set caps
        cachedPcmData = dropController.getDataFor(dropName);
        if (cachedPcmData == null) {
            throw new IllegalArgumentException("No PCM data cached for file: " + dropName);
        }

        // 2. Create the element in the constructor so Mixer can see it immediately
        // Use a unique name to avoid GStreamer naming collisions
        this.srcElement = (AppSrc) ElementFactory.make("appsrc", channelName + "_src_" + System.nanoTime());
        
        // 3. Configure AppSrc
        srcElement.set("format", Format.TIME);
        srcElement.set("is-live", false); 
        srcElement.set("do-timestamp", true);
        
        String capsStr = String.format("audio/x-raw,format=%s,channels=%d,rate=%d,layout=interleaved",
                cachedPcmData.format, cachedPcmData.channels, (int)cachedPcmData.sampleRate);
        srcElement.setCaps(Caps.fromString(capsStr));

        Log.debugf("OneShotDropChannel [%s] initialized with caps: %s", channelName, capsStr);
    }

    @Override
    public void start() {
        
        // Push the byte array into the appsrc
        // We use a background thread or the GStreamer 'need-data' signal
        // For a POC one-shot, a simple push works:
        pushDataToAppsrc(srcElement, cachedPcmData);
        
        Log.infof("Started playing drop: %s", dropName);
    }

    private void pushDataToAppsrc(AppSrc src, CachedWav cachedWav) {
        byte[] data = cachedWav.pcmData;

        // We only want to send full frames, so figure this out
        int bytesPerFrame = (cachedWav.sampleRate / 8) * cachedWav.channels;

        // Ensure we only push full frames (multiple of bytesPerFrame)
        // by dropping any left over Bytes
        int safeLength = data.length - (data.length % bytesPerFrame);

        Buffer buffer = new Buffer(safeLength);
        buffer.map(true).put(ByteBuffer.wrap(data, 0, safeLength));
        buffer.unmap();
        
        // This tells the live mixer "play this as soon as you get it"
        buffer.setPresentationTimestamp(-1);
        buffer.setDuration(-1);

        // Use the Mixer's current running time to ensure it plays NOW
        // If you use -1 here with is-live=true, it often works, 
        // but explicitly setting the clock time is safer.
        long runningTime = mixer.getPipeline().getClock().getTime();
        long baseTime = mixer.getPipeline().getBaseTime();
        
        // Calculate the 'now' timestamp for the pipeline
        buffer.setPresentationTimestamp(runningTime - baseTime);
        
        // Push the data
        src.pushBuffer(buffer);
        
        // Use the delay to allow the data to actually exit the appsrc and enter the mixer
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
            Log.debugf("Signaling end of stream for %s", channelName);
            src.endOfStream();
            
            // MANUALLY trigger the cleanup since the global Bus won't see it
            Log.infof("One-shot %s finished playback, triggering removal.", channelName);
            mixer.removeInputChannel(channelName);
        });
    }

    @Override
    public String getChannelName() { return channelName; }
    @Override
    public double getGain() { return volume; }
    @Override
    public Element getSrcElement() { return srcElement; }

    @Override
    public void setGain(double volume) {
        this.volume = volume;
    }

    @Override
    public boolean supportsGain() {
        return false;
    }    
}