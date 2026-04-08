package com.coherentnetworksolutions.reson8.audio.output;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;

import io.quarkus.logging.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;


public class BrowserOutputChannel implements OutputChannel {
    private AppSink appsink;
    private String channelName;
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(100);
    // private final Set<OutputStream> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // private final Map<OutputStream, AtomicBoolean> activeSessions = new ConcurrentHashMap<>();

    BrowserSessionManager browserSessionManager;

    public BrowserOutputChannel(String channelName, BrowserSessionManager browserSessionManager) {
        this.channelName = channelName;
        this.browserSessionManager = browserSessionManager;
        this.appsink = (AppSink) ElementFactory.make("appsink", channelName);

        // 1. Configure the sink properties immediately
        appsink.setCaps(Caps.fromString(Mixer.CAPS));
        appsink.set("emit-signals", true);
        appsink.set("sync", false);
        appsink.set("async", false);
        appsink.set("drop", true);
        appsink.set("max-buffers", 1);

        // Connect the listener to the 'new-sample' signal
        appsink.connect(new AppSink.NEW_SAMPLE() {
            public FlowReturn newSample(AppSink elem) {
                Sample sample = elem.pullSample();
                Buffer buffer = sample.getBuffer();
                
                // Convert GStreamer Buffer to Java byte array
                ByteBuffer bb = buffer.map(false);
                byte[] bytes = new byte[bb.remaining()];
                bb.get(bytes);
                buffer.unmap();
                sample.dispose(); // CRITICAL: Prevent memory leaks

                browserSessionManager.broadcast(bytes); // Simple handoff
                sample.dispose();
                return FlowReturn.OK;
            }

        });
    }

    @Override
    public void start() {
        Log.debugf("Browser channel %s is ready for wiring", channelName);
    }

    
    public void subscribe(OutputStream os) throws IOException {
        browserSessionManager.subscribe(os);
    }

    public void broadcast(byte[] payload) {
        browserSessionManager.broadcast(payload);
    }    


    // public byte[] getWavHeader(long sampleRate, int bitDepth, int channels) {
    //     byte[] header = new byte[44];
    //     long byteRate = sampleRate * channels * bitDepth / 8;

    //     header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
    //     // File size (set to a very large number for streaming)
    //     header[4] = (byte) 0xff; header[5] = (byte) 0xff; header[6] = (byte) 0xff; header[7] = (byte) 0x7f;
    //     header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
    //     header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
    //     header[16] = 16; // Subchunk1Size (16 for PCM)
    //     header[17] = 0; header[18] = 0; header[19] = 0;
    //     header[20] = 1; // AudioFormat (1 for PCM)
    //     header[21] = 0;
    //     header[22] = (byte) channels;
    //     header[23] = 0;
    //     header[24] = (byte) (sampleRate & 0xff);
    //     header[25] = (byte) ((sampleRate >> 8) & 0xff);
    //     header[26] = (byte) ((sampleRate >> 16) & 0xff);
    //     header[27] = (byte) ((sampleRate >> 24) & 0xff);
    //     header[28] = (byte) (byteRate & 0xff);
    //     header[29] = (byte) ((byteRate >> 8) & 0xff);
    //     header[30] = (byte) ((byteRate >> 16) & 0xff);
    //     header[31] = (byte) ((byteRate >> 24) & 0xff);
    //     header[32] = (byte) (channels * bitDepth / 8); // BlockAlign
    //     header[33] = 0;
    //     header[34] = (byte) bitDepth;
    //     header[35] = 0;
    //     header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
    //     // Data size (set to a very large number)
    //     header[40] = (byte) 0xff; header[41] = (byte) 0xff; header[42] = (byte) 0xff; header[43] = (byte) 0x7f;

    //     return header;
    // }

    @Override
    public String getChannelName() {
        return channelName;
    }

    @Override
    public Element getElement() {
        return appsink;
    }

    public void clearQueue() {
        int purged = queue.size();
        queue.clear();
        Log.infof("Purged %d stale audio buffers from output queue.", purged);
    }

    public int getListenerCount() {
        return browserSessionManager.getSessionCount();
    }


    public Map<OutputStream,AtomicBoolean> getListeners(){
        return browserSessionManager.getSessions();
    }

}
