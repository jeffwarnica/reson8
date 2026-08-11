package com.coherentnetworksolutions.reson8.audio.output.from;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.output.to.BrowserSessionManager;
import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import io.smallrye.mutiny.Multi;

import java.nio.ByteBuffer;


public class GsMixerToBrowserManagerChannel implements MixerOutputToClientManagerChannel {
    private AppSink appsink;
    private String channelName;
    
    BrowserSessionManager browserSessionManager;

    public GsMixerToBrowserManagerChannel(String channelName, 
        BrowserSessionManager browserSessionManager, GsToolkit toolkit) {
        this.channelName = channelName;
        this.browserSessionManager = browserSessionManager;
        this.appsink = (AppSink) toolkit.makeElement("appsink", channelName);

        // 1. Configure the sink properties immediately
        appsink.setCaps(toolkit.capsFromString(Mixer.CAPS));
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
   
    public Multi<byte[]> subscribe() {
        return browserSessionManager.subscribe();
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

}
