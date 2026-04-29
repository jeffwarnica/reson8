package com.coherentnetworksolutions.reson8.audio.providers;

import java.nio.ByteBuffer;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeReturn;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.elements.PlayBin;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "reson8dev.audiopath", stringValue = "gst")
public class NativeGstToolkit implements GstToolkit {

    @Override
    public PlayBin createPlayBin(String name) {
        return (PlayBin) ElementFactory.make("playbin", name);
    }
    
    @Override
    public Bin createBin(String name) {
        return new Bin(name);
    }

    @Override
    public Element makeElement(String factory, String name) {
        return ElementFactory.make(factory, name);
    }

    @Override
    public AppSrc makeAppSrc(String name) {
        return (AppSrc) ElementFactory.make("appsrc", name);
    }

    @Override
    public void setElementState(Element element, State state) {
        element.setState(state);
    }

    @Override
    public void releaseRequestPad(Element mixer, Pad pad) {
        mixer.releaseRequestPad(pad);
    }

    @Override
    public void linkElements(Element src, Element dest) {
        src.link(dest);
    }

    @Override
    public void addElementToBin(Bin bin, Element element) {
        bin.add(element);
    }

    @Override
    public void addMany(Bin bin, Element... elements) {
        bin.addMany(elements);
    }

    @Override
    public void addPad(Bin bin, Pad pad) {
        bin.addPad(pad);
    }

    @Override
    public GhostPad createGhostPad(String name, Pad target) {
        // This constructor performs native plumbing immediately
        return new GhostPad(name, target);
    }

    @Override
    public Pad getStaticPad(Element el, String name) {
        return el.getStaticPad(name);
    }

    @Override
    public void linkPads(Pad src, Pad sink) {
        src.link(sink);
    }

    @Override
    public boolean unlinkPads(Pad src, Pad sink) {
        return src.unlink(sink);
    }

    @Override
    public Pad getRequestPad(Element el, String name) {
        return el.getRequestPad(name);
    }

    @Override
    public Buffer createBuffer(int size) {
        return new Buffer(size);
    }

    @Override
    public void syncStateWithParent(Bin instanceBin) {
        instanceBin.syncStateWithParent();
    }

    @Override
    public void removeElementFromBin(Bin parentBin, Bin instanceBin) {
        parentBin.remove(instanceBin);
    }

    @Override
    public void fillBuffer(Buffer buffer, byte[] data, int offset, int length) {
        // This part actually touches the C-memory
        ByteBuffer bb = buffer.map(true);
        if (bb != null) {
            bb.put(data, offset, length);
            buffer.unmap();
        } else {
            throw new RuntimeException("GStreamer failed to map buffer for writing!");
        }
    }

    @Override
    public void setDuration(Buffer buffer, long durationNano) {
        buffer.setDuration(durationNano);
    }

    @Override
    public void pushBuffer(AppSrc appSrc, Buffer buffer) {
        appSrc.pushBuffer(buffer);
    }

    @Override
    public void setAppSrcCaps(AppSrc appSrc, Caps caps) {
        appSrc.setCaps(caps);
    }

    @Override
    public void connectNeedData(AppSrc appSrc, AppSrc.NEED_DATA callback) {
        appSrc.connect(callback);
    }
    
    @Override
    public void connectAboutToFinish(PlayBin playBin, PlayBin.ABOUT_TO_FINISH callback) {
        playBin.connect(callback);
    }

    @Override
    public void addEosProbe(Pad pad, Runnable cleanupTask) {
        pad.addProbe(PadProbeType.EVENT_DOWNSTREAM, (p, info) -> {
            if (info.getEvent() instanceof org.freedesktop.gstreamer.event.EOSEvent) {
                // Run the cleanup logic on a new thread as discussed
                new Thread(cleanupTask).start();
                return PadProbeReturn.DROP;
            }
            return PadProbeReturn.OK;
        });
    }

    @Override
    public void linkMany(Element... elements) {
        for (int i = 0; i < elements.length - 1; i++) {
            elements[i].link(elements[i + 1]);
        }
    }

    @Override
    public void setElementProperty(Element element, String property, Object value) {
        element.set(property, value);
    }

    @Override
    public Object getElementProperty(Element element, String property) {
        return element.get(property);
    }

    @Override
    public Caps capsFromString(String capsString) {
        return Caps.fromString(capsString);
    }

}