package com.coherentnetworksolutions.reson8.audio.providers;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.elements.PlayBin;

public interface GstToolkit {
    Bin createBin(String name);
    Element makeElement(String factory, String name);
    AppSrc makeAppSrc(String name);
    
    // Lifecycle and side-effect wrappers
    void setElementState(Element element, State state);
    void releaseRequestPad(Element mixer, Pad pad);
    void linkElements(Element src, Element dest);
    void addElementToBin(Bin bin, Element element);
    void addMany(Bin bin, Element... elements);
    void addPad(Bin bin, Pad pad);
    GhostPad createGhostPad(String name, Pad target);

    Pad getStaticPad(Element el, String name);
    void linkPads(Pad src, Pad sink);
    boolean unlinkPads(Pad src, Pad sink);
    Pad getRequestPad(Element el, String name);

    void syncStateWithParent(Bin instanceBin);
    void removeElementFromBin(Bin parentBin, Bin instanceBin);
    
    Buffer createBuffer(int size);
    void fillBuffer(Buffer buffer, byte[] data, int offset, int length);
    
    void setDuration(Buffer buffer, long durationNano);
    
    void pushBuffer(AppSrc appSrc, Buffer buffer);

    void setAppSrcCaps(AppSrc appSrc, Caps caps);

    void connectNeedData(AppSrc appSrc, AppSrc.NEED_DATA callback);
    void connectAboutToFinish(PlayBin playBin, PlayBin.ABOUT_TO_FINISH callback);

    void addEosProbe(Pad pad, Runnable cleanupTask);
    void linkMany(Element... elements);
    void setElementProperty(Element element, String string, Object value);
    Object getElementProperty(Element element, String property);
    Caps capsFromString(String capsString);
    PlayBin createPlayBin(String name);
    
}