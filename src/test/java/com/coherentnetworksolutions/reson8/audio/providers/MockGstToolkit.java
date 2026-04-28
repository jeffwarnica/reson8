package com.coherentnetworksolutions.reson8.audio.providers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.elements.PlayBin.ABOUT_TO_FINISH;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "reson8dev.audiopath", stringValue = "silent")
public class MockGstToolkit implements GstToolkit {
    // Trackers for "Refcount" style verification
    public Map<String, Element> createdElements = new ConcurrentHashMap<>();
    public Set<Pad> requestedPads = ConcurrentHashMap.newKeySet();
    public Set<Pad> releasedPads = ConcurrentHashMap.newKeySet();
    private Map<Buffer, Integer> sizeTracker = new ConcurrentHashMap<>();

    /**
     * Cache of static pads keyed by element + pad name.
     * <p>
     * Ensures {@code getStaticPad(el, "src")} always returns the <em>same</em> mock
     * pad for a given element, mirroring real GStreamer where the ghost pad is a
     * stable object on the element. Without this, {@code linkPads} and
     * {@code unlinkPads} would operate on completely different mock objects.
     */
    private final ConcurrentHashMap<Element, ConcurrentHashMap<String, Pad>> staticPadCache =
            new ConcurrentHashMap<>();

    public volatile AppSrc lastCreatedAppSrc;
    public final Queue<AppSrc.NEED_DATA> allCapturedNeedData = new ConcurrentLinkedQueue<>();
    public volatile AppSrc.NEED_DATA capturedNeedData;
    public volatile Runnable capturedCleanupTask;
    /** Ordered history of every EOS-probe cleanup task, one entry per trigger. */
    public final Queue<Runnable> allCapturedCleanupTasks = new ConcurrentLinkedQueue<>();
    public volatile ABOUT_TO_FINISH capturedAboutToFinish;

    @Override
    public PlayBin createPlayBin(String name) {
        PlayBin mockPlayBin = mock(PlayBin.class);
        when(mockPlayBin.getName()).thenReturn(name);
        // Delegate to the cache so element.getStaticPad(n) == toolkit.getStaticPad(element, n)
        when(mockPlayBin.getStaticPad(anyString())).thenAnswer(inv ->
                getStaticPad(mockPlayBin, inv.getArgument(0)));
        createdElements.put(name, mockPlayBin);
        return mockPlayBin;
    }
    
    @Override
    public Bin createBin(String name) {
        Bin mockBin = mock(Bin.class);
        when(mockBin.getName()).thenReturn(name);
        // Delegate to the cache so element.getStaticPad(n) == toolkit.getStaticPad(element, n)
        when(mockBin.getStaticPad(anyString())).thenAnswer(inv ->
                getStaticPad(mockBin, inv.getArgument(0)));
        createdElements.put(name, mockBin);
        return mockBin;
    }

    @Override
    public Element makeElement(String factory, String name) {
        Element el = "playbin".equals(factory) ? mock(PlayBin.class) : mock(Element.class);
        when(el.getName()).thenReturn(name);
        // Delegate to the cache so element.getStaticPad(n) == toolkit.getStaticPad(element, n)
        when(el.getStaticPad(anyString())).thenAnswer(inv ->
                getStaticPad(el, inv.getArgument(0)));
        createdElements.put(name, el);
        return el;
    }

    @Override
    public AppSrc makeAppSrc(String name) {
        AppSrc mockSrc = mock(AppSrc.class);
        when(mockSrc.getName()).thenReturn(name);
        // Delegate to the cache so element.getStaticPad(n) == toolkit.getStaticPad(element, n)
        when(mockSrc.getStaticPad(anyString())).thenAnswer(inv ->
                getStaticPad(mockSrc, inv.getArgument(0)));
        this.lastCreatedAppSrc = mockSrc;
        this.createdElements.put(name, mockSrc);
        return mockSrc;
    }

    @Override
    public void releaseRequestPad(Element mixer, Pad pad) {
        releasedPads.add(pad);
        mixer.releaseRequestPad(pad);
    }

    @Override
    public void setElementState(Element element, State state) {
        if (element != null) {
            element.setState(state);
        }
    }

    @Override
    public void linkElements(Element src, Element dest) {
        // Logic verification only
    }

    @Override
    public void addElementToBin(Bin bin, Element element) {
        // Logic verification only
    }

    @Override
    public void addMany(Bin bin, Element... elements) {
        // In a test, we can verify that the bin "contains" these elements
        for (Element el : elements) {
            bin.add(el);
        }
    }

    @Override
    public void addPad(Bin bin, Pad pad) {
        // Verify that the pad was attached to the bin
        // You can use a Mockito verify() here
        // verify(bin).addPad(pad);

    }

    @Override
    public GhostPad createGhostPad(String name, Pad target) {
        // Return a mock GhostPad so we don't trigger native logic
        GhostPad mockGhost = mock(GhostPad.class);
        when(mockGhost.getName()).thenReturn(name);
        return mockGhost;
    }

    @Override
    public Pad getStaticPad(Element el, String name) {
        // Return the same mock pad for a given element + pad name so that linkPads and
        // unlinkPads operate on the same object, mirroring real GStreamer behaviour.
        return staticPadCache
                .computeIfAbsent(el, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, n -> {
                    Pad p = mock(Pad.class);
                    when(p.getName()).thenReturn(n);
                    return p;
                });
    }

    @Override
    public void linkPads(Pad src, Pad sink) {
        if (src == null || sink == null) {
            throw new IllegalArgumentException(String.format("MockToolkit: Link failed! src is [%s], sink is [%s]. "
                    + "Ensure the parent elements are stubbed to return mock pads.", src, sink));
        }
        Log.debugf("MockToolkit: Linked Pad [%s] -> [%s]", src.getName(), sink.getName());
    }

    @Override
    public boolean unlinkPads(Pad src, Pad sink) {
        return true;
    }

    @Override
    public Pad getRequestPad(Element el, String name) {
        Pad mockPad = mock(Pad.class);
        // We name it something distinct so we can track it in logs
        when(mockPad.getName()).thenReturn("requested_pad_" + System.nanoTime());
        requestedPads.add(mockPad);
        return mockPad;
    }

    @Override
    public Buffer createBuffer(int size) {
        // Return a mock that stores its intended size
        Buffer mockBuffer = mock(Buffer.class);
        sizeTracker.put(mockBuffer, size);

        return mockBuffer;
    }

    @Override
    public void fillBuffer(Buffer buffer, byte[] data, int offset, int length) {
        Integer allocatedSize = sizeTracker.get(buffer);

        // 1. Safety Check: Did we allocate enough?
        if (allocatedSize == null) {
            throw new IllegalStateException("Attempted to fill a buffer that wasn't created by this toolkit!");
        }

        if (length > allocatedSize) {
            throw new IllegalArgumentException(String.format("Overflow! Data: %d, Buffer: %d", length, allocatedSize));
        }

        // 2. Bounds Check: Is the PCM data slice valid?
        if (offset + length > data.length) {
            throw new ArrayIndexOutOfBoundsException("Attempted to read past the end of the source PCM data!");
        }

        Log.debugf("MockToolkit: Logic Check Passed for %d bytes at offset %d", length, offset);
    }

    @Override
    public void syncStateWithParent(Bin instanceBin) {
        Log.debugf("MockToolkit: syncStateWithParent called for [%s]", instanceBin.getName());
    }

    @Override
    public void removeElementFromBin(Bin parentBin, Bin instanceBin) {
        // We physically call the mock's remove method
        // so that if the test checks bin.getElements(), it's gone.
        parentBin.remove(instanceBin);

        // Optional: If you are tracking createdElements in a Set,
        // you could remove it here to verify a "Zero Leak" state.
        // this.createdElements.remove(element);

        Log.debugf("MockToolkit: Removed [%s] from bin [%s]", instanceBin.getName(), parentBin.getName());
    }

    @Override
    public void setDuration(Buffer buffer, long durationNano) {
        buffer.setDuration(durationNano);

        Log.debugf("MockToolkit: Set duration %d on buffer", durationNano);
    }

    @Override
    public void pushBuffer(AppSrc appSrc, Buffer buffer) {
        // In a test, we verify that the buffer was actually sent
        appSrc.pushBuffer(buffer);
    }

    @Override
    public void connectNeedData(AppSrc appSrc, AppSrc.NEED_DATA callback) {
        this.capturedNeedData = callback;
        this.allCapturedNeedData.add(callback);
    }
    
    @Override
    public void connectAboutToFinish(PlayBin playBin, PlayBin.ABOUT_TO_FINISH callback) {
        this.capturedAboutToFinish = callback;
    }

    @Override
    public void addEosProbe(Pad pad, Runnable cleanupTask) {
        this.capturedCleanupTask = cleanupTask;
        this.allCapturedCleanupTasks.add(cleanupTask);
    }

    @Override
    public void linkMany(Element... elements) {
        for (int i = 0; i < elements.length - 1; i++) {
            // We call our own linkElements so that any logic
            // (like the null checks we added) is applied consistently.
            linkElements(elements[i], elements[i + 1]);
        }
    }

    @Override
    public void setElementProperty(Element element, String property, Object value) {
        // We log it for "human" debugging in the console
        Log.debugf("MockToolkit: Setting property [%s] to [%s] on element [%s]", property, value, element.getName());

        // We execute the 'set' on the mock object so that
        // mockElement.get(property) would return the value if needed.
        element.set(property, value);
    }

    @Override
    public Object getElementProperty(Element element, String property) {
        // If your code uses toolkit.getElementProperty, we return what the mock has
        return element.get(property);
    }

    @Override
    public Caps capsFromString(String capsString) {
        Caps mockCaps = mock(Caps.class);
        when(mockCaps.toString()).thenReturn(capsString);
        return mockCaps;
    }
}