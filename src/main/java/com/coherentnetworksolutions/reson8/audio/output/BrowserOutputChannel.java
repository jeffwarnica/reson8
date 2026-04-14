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
