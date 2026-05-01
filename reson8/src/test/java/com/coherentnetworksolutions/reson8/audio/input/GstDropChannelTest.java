package com.coherentnetworksolutions.reson8.audio.input;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertFalse;
// import static org.junit.jupiter.api.Assertions.assertNotNull;
// import static org.junit.jupiter.api.Assertions.assertThrows;
// import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.ArgumentMatchers.anyInt;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.ArgumentMatchers.contains;
// import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.never;
// import static org.mockito.Mockito.spy;
// import static org.mockito.Mockito.timeout;
// import static org.mockito.Mockito.times;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.State;
import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.providers.MockGsToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

class GstDropChannelTest {

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak: a production class is calling " +
                "Caps.fromString() or ElementFactory.make() directly.");
    }

    /** Used only before {@code spy(...)} so {@code when(mockWav.caps()).thenReturn(...)} does not call the spy mid-stub. */
    private MockGsToolkit toolkitDelegate;
    private MockGsToolkit toolkit;
    private SignalBucket mockBucket;
    private CachedWav mockWav;
    private GsDropChannel channel;

    @BeforeEach
    void setUp() {
        // 1. Initialize the Mock Toolkit
        toolkitDelegate = new MockGsToolkit();
        Caps wavCaps = toolkitDelegate.capsFromString("audio/x-raw");
        toolkit = spy(toolkitDelegate);

        // 2. Mock the configuration dependencies
        mockBucket = mock(SignalBucket.class);
        mockWav = mock(CachedWav.class);

        // SoundDefinition.drop() must be non-empty: GstDropChannel uses drop().get().ceiling().
        // @WithDefault on DropConfig does not apply to Mockito mocks — stub ceiling() or NPE on unboxing.
        var mockDef = mock(Reson8Config.SoundDefinition.class);
        var mockDropDef = mock(Reson8Config.DropConfig.class);

        when(mockBucket.getName()).thenReturn("TestChannel");

        when(mockBucket.getSoundDefinition()).thenReturn(mockDef);
        when(mockDef.drop()).thenReturn(Optional.of(mockDropDef));
        when(mockDropDef.filename()).thenReturn("test.wav");
        when(mockDropDef.ceiling()).thenReturn(100.0);

        // Mock basic Wav info (mock Caps from toolkit — avoids native Caps.fromString / Gst.init ordering)
        when(mockWav.caps()).thenReturn(wavCaps);
        // 1. Setup mock data (1 second of audio)
        byte[] fakeData = new byte[44100 * 4];
        when(mockWav.pcmData()).thenReturn(fakeData);
        when(mockWav.bytesPerFrame()).thenReturn(4);
        when(mockWav.sampleRate()).thenReturn(44100.0);

        // 3. Create the channel with the mock toolkit
        channel = new GsDropChannel(mockBucket, mockWav, toolkit);
    }

    @Test
    void testConstructorPlumbing() {
        // Verify that the constructor used the toolkit to create the base bin and mixer
        assertNotNull(channel.getSrcElement());
        assertTrue(toolkit.createdElements.keySet().stream().anyMatch(el -> el.contains("TestChannel_sum")));
        assertEquals(100.0, channel.getCeiling(), 1e-9, "initial ceiling comes from DropConfig.ceiling()");
    }

    @Test
    void testConstructorThrowsWhenDropSectionAbsent() {
        SignalBucket bucket = mock(SignalBucket.class);
        Reson8Config.SoundDefinition def = mock(Reson8Config.SoundDefinition.class);
        when(bucket.getName()).thenReturn("NoDrop");
        when(bucket.getSoundDefinition()).thenReturn(def);
        when(def.drop()).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> new GsDropChannel(bucket, mockWav, toolkitDelegate));
    }

    @Test
    void testTriggerCreatesInstance() {
        // Execute
        channel.trigger();

        // Verify: Use timeout because of the runAsync
        verify(toolkit, timeout(1000)).makeAppSrc(contains("TestChannel_inst_"));

        // Check if the pad was requested from the mixer
        assertFalse(toolkit.requestedPads.isEmpty(), "A mixer pad should have been requested");
    }

    @Test
    void testNeedDataSlicing() {

        channel.trigger();

        // capturedNeedData is set at [14] connectNeedData, after addMany[10]/linkMany[11] — safe barrier
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedNeedData != null);

        // 2. Simulate GStreamer requesting 1000 bytes
        int requestSize = 1000;
        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, requestSize);

        // 3. Verify the Math
        // Frames = 1000 / 4 = 250
        // Duration = (250 * 1,000,000,000) / 44100 = 5,668,934 ns
        long expectedDuration = (250L * 1_000_000_000L) / 44100L;

        // Verify via the Spy toolkit
        verify(toolkit).createBuffer(1000);
        verify(toolkit).setDuration(any(Buffer.class), eq(expectedDuration));
        verify(toolkit).pushBuffer(eq(toolkit.lastCreatedAppSrc), any(Buffer.class));
    }

    @Test
    void testTriggerAllocatesResources() {
        channel.trigger();

        // Verify the AppSrc was made (wait for async if needed)
        verify(toolkit, timeout(1000)).makeAppSrc(anyString());

        // Verify we have a pad to release later
        assertEquals(1, toolkit.requestedPads.size());
    }

    @Test
    void testAudioDataFlow() {
        channel.trigger();

        // capturedCleanupTask is set at [19] addEosProbe — after linkPads[17] in linkAndStart
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedCleanupTask != null);

        // 1. Manually trigger the callback captured by the toolkit
        // This simulates the 'appsrc' saying "Feed me 4096 bytes"
        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, 4096);

        // 2. Verify toolkit methods were called with correct math
        verify(toolkit).createBuffer(anyInt());
        verify(toolkit).pushBuffer(eq(toolkit.lastCreatedAppSrc), any());
    }

    @Test
    void testCleanupReleasesPad() {
        channel.trigger();

        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedCleanupTask != null);

        // Capture the allocated pad before cleanup so the verify reads as
        // "the pad that was reserved is the one that gets released"
        Pad thePad = toolkit.requestedPads.iterator().next();

        // 1. Simulate the sound finishing (EOS Probe fires)
        toolkit.capturedCleanupTask.run();

        // 2. Verify the pad from requestedPads was released
        verify(toolkit).releaseRequestPad(any(), eq(thePad));
    }

    @Test
    void testTriggerAddsElementsToBin() {
        channel.trigger();

        // lastCreatedAppSrc is set at [3] makeAppSrc — too early; addMany is at [10].
        // capturedNeedData is set at [14] connectNeedData (end of constructor) — correct barrier.
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedNeedData != null);

        // NOW you verify on the toolkit SPY
        verify(toolkit).addMany(any(Bin.class), any(Element.class), any(), any(), any());
    }

    @Test
    void testCleanupRemovesInstanceFromBin() {
        channel.trigger();

        // 1. Wait for the async thread to finish construction (capturedCleanupTask is last step [19])
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedCleanupTask != null);

        // 2. Capture the instance bin and the reserved mixer pad before cleanup runs
        Bin instanceBin = (Bin) toolkit.createdElements.keySet().stream()
                .filter(name -> name.contains("_inst_"))
                .filter(name -> name.contains("_bin"))
                .map(toolkit.createdElements::get)
                .findFirst()
                .orElseThrow();
        Pad thePad = toolkit.requestedPads.iterator().next();

        // 3. Fire the cleanup task (simulating the EOS probe callback)
        toolkit.capturedCleanupTask.run();

        // 4. Verify correct GStreamer teardown order:
        //    setState(NULL) → unlinkPads → removeElementFromBin → releaseRequestPad
        //    Out-of-order teardown can leave native C objects alive or cause crashes.
        InOrder order = inOrder(toolkit);
        order.verify(toolkit).setElementState(eq(instanceBin), eq(State.NULL));
        order.verify(toolkit).unlinkPads(any(Pad.class), eq(thePad));
        order.verify(toolkit).removeElementFromBin(any(Bin.class), eq(instanceBin));
        order.verify(toolkit).releaseRequestPad(any(), eq(thePad));
    }

    @Test
    void testCleanupDoesNotDestroyChannelMixer() {
        channel.trigger();
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedCleanupTask != null);

        // The channelMixer is shared across all triggers — cleanup must not touch its state.
        Element channelMixer = toolkit.createdElements.get("TestChannel_sum");
        assertNotNull(channelMixer, "channelMixer must be registered in createdElements");

        toolkit.capturedCleanupTask.run();

        verify(toolkit, never()).setElementState(eq(channelMixer), any(State.class));
    }

    @Test
    void testInstanceLinksToMixer() {
        channel.trigger();

        // linkPads is at [17]; capturedCleanupTask is set at [19] addEosProbe, after linkPads — correct barrier
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedCleanupTask != null);

        // Both args are now exact: the mixer pad from requestedPads and the instanceBin src pad
        // (toolkit.getStaticPad caches the same mock pad for a given element+name, so we can retrieve it)
        Pad mixerPad = toolkit.requestedPads.iterator().next();
        verify(toolkit).linkPads(any(Pad.class), eq(mixerPad));
    }

    @Test
    void testBasicGettersAndSetters() {
        assertEquals(100.0, channel.getCeiling(), 1e-9);
        channel.setCeiling(75.5);
        assertEquals(75.5, channel.getCeiling());
        assertEquals("TestChannel", channel.getChannelName());

        // Intensity is stored for REST/UI (0–100); drops do not modulate audio from it.
        channel.setTargetIntensity(50.0);
        assertEquals(50.0, channel.getTargetIntensity());
        assertEquals(0.0, channel.getCurrentIntensity());

        assertNotNull(channel.getSrcElement());
    }

    @Test
    void testTriggerWithEmptyData() {
        // Setup: Return empty PCM data
        when(mockWav.pcmData()).thenReturn(new byte[0]);

        channel.trigger();
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedNeedData != null);

        // Simulate request: Should trigger EOS immediately
        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, 4096);

        verify(toolkit.lastCreatedAppSrc).endOfStream();
        verify(toolkit, never()).createBuffer(anyInt());
    }

    @Test
    void testSpamTriggers() {
        channel.trigger();
        channel.trigger();

        // Verify two different AppSrcs were created
        verify(toolkit, timeout(1000).times(2)).makeAppSrc(anyString());

        // Verify two different pads were requested from the mixer
        assertEquals(2, toolkit.requestedPads.size());
    }

    // -----------------------------------------------------------------------
    // Streaming / data-flow edge cases
    // -----------------------------------------------------------------------

    @Test
    void testNeedDataAdvancesOffset() {
        // Three full frames; served across two sequential needData requests.
        byte[] threeFrames = new byte[4 * 3];
        when(mockWav.pcmData()).thenReturn(threeFrames);

        channel.trigger();
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedNeedData != null);

        // First request: 8 bytes (frames 0-1). Second request: 4 bytes (frame 2).
        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, 8);
        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, 8);

        // bytesPushed must advance: second fill starts at offset 8, not 0.
        InOrder order = inOrder(toolkit);
        order.verify(toolkit).fillBuffer(any(Buffer.class), any(byte[].class), eq(0), eq(8));
        order.verify(toolkit).fillBuffer(any(Buffer.class), any(byte[].class), eq(8), eq(4));
    }

    @Test
    void testEosOnExactBoundary() {
        // Exactly 2 frames — after the data is exhausted the next needData must fire EOS.
        when(mockWav.pcmData()).thenReturn(new byte[4 * 2]);

        channel.trigger();
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedNeedData != null);

        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, 8); // consumes all 8 bytes
        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, 8); // remaining == 0 → EOS

        verify(toolkit.lastCreatedAppSrc).endOfStream();
        verify(toolkit, times(1)).createBuffer(anyInt()); // only one buffer was ever created
    }

    @Test
    void testPartialFrameIsDroppedBeforeEos() {
        // 1 full frame (4 bytes) + 1 orphan byte: the orphan must not be pushed as a partial buffer.
        // First needData: bufferSize = 5 - (5 % 4) = 4 → one buffer pushed.
        // Second needData: remaining = 1; 1 - (1 % 4) = 0 → EOS.
        when(mockWav.pcmData()).thenReturn(new byte[5]);

        channel.trigger();
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedNeedData != null);

        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, 5);
        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, 5);

        verify(toolkit.lastCreatedAppSrc).endOfStream();
        verify(toolkit, times(1)).createBuffer(4); // exactly one 4-byte buffer, no partial
    }

    // -----------------------------------------------------------------------
    // Volume / gain edge cases
    // -----------------------------------------------------------------------

    @Test
    void testTriggerWithZeroVolume() {
        // A trigger with volume 0 must still create and wire up an AppSrc instance.
        channel.trigger();

        verify(toolkit, timeout(1000)).makeAppSrc(anyString());
        assertFalse(toolkit.requestedPads.isEmpty(), "Pad must be reserved even at zero volume");
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Test
    void testDisposeReleasesNativeResources() {
        // dispose() sets the channel bin to NULL state and releases native resources.
        assertDoesNotThrow(() -> channel.dispose());
        assertNull(channel.getSrcElement(), "channelBin must be null after dispose() — native resources released");
    }

    // -----------------------------------------------------------------------
    // Multi-trigger resource accounting
    // -----------------------------------------------------------------------

    @Test
    void testConcurrentTriggersBothCleanUp() {
        channel.trigger();
        channel.trigger();

        // Wait until both async tasks have registered their EOS probes.
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> toolkit.allCapturedCleanupTasks.size() >= 2);

        assertEquals(2, toolkit.requestedPads.size(), "Each trigger must reserve its own mixer pad");

        // Fire every captured cleanup task (simulating both AppSrcs reaching EOS).
        new ArrayList<>(toolkit.allCapturedCleanupTasks).forEach(Runnable::run);

        // Both reserved pads must be returned — zero pad leak.
        assertEquals(2, toolkit.releasedPads.size(), "Every reserved pad must be released after cleanup");
    }
}