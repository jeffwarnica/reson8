package com.coherentnetworksolutions.reson8.audio.input;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.State;
import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.providers.MockGstToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

class GstDropChannelTest {

    /** Used only before {@code spy(...)} so {@code when(mockWav.caps()).thenReturn(...)} does not call the spy mid-stub. */
    private MockGstToolkit toolkitDelegate;
    private MockGstToolkit toolkit;
    private SignalBucket mockBucket;
    private CachedWav mockWav;
    private GstDropChannel channel;

    @BeforeEach
    void setUp() {
        // 1. Initialize the Mock Toolkit
        toolkitDelegate = new MockGstToolkit();
        Caps wavCaps = toolkitDelegate.capsFromString("audio/x-raw");
        toolkit = spy(toolkitDelegate);

        // 2. Mock the configuration dependencies
        mockBucket = mock(SignalBucket.class);
        mockWav = mock(CachedWav.class);

        // SoundDefinition.drop() must be non-empty: GstDropChannel uses drop().get().gain().
        // @WithDefault on DropConfig does not apply to Mockito mocks — stub gain() or NPE on unboxing.
        var mockDef = mock(Reson8Config.SoundDefinition.class);
        var mockDropDef = mock(Reson8Config.DropConfig.class);

        when(mockBucket.getName()).thenReturn("TestChannel");

        when(mockBucket.getSoundDefinition()).thenReturn(mockDef);
        when(mockDef.drop()).thenReturn(Optional.of(mockDropDef));
        when(mockDropDef.filename()).thenReturn("test.wav");
        when(mockDropDef.gain()).thenReturn(1.0);

        // Mock basic Wav info (mock Caps from toolkit — avoids native Caps.fromString / Gst.init ordering)
        when(mockWav.caps()).thenReturn(wavCaps);
        // 1. Setup mock data (1 second of audio)
        byte[] fakeData = new byte[44100 * 4];
        when(mockWav.pcmData()).thenReturn(fakeData);
        when(mockWav.bytesPerFrame()).thenReturn(4);
        when(mockWav.sampleRate()).thenReturn(44100.0);

        // 3. Create the channel with the mock toolkit
        channel = new GstDropChannel(mockBucket, mockWav, toolkit);
    }

    @Test
    void testConstructorPlumbing() {
        // Verify that the constructor used the toolkit to create the base bin and mixer
        assertNotNull(channel.getSrcElement());
        assertTrue(toolkit.createdElements.keySet().stream().anyMatch(el -> el.contains("TestChannel_sum")));
        assertEquals(1.0, channel.getGain(), 1e-9, "initial gain comes from DropConfig.gain()");
    }

    @Test
    void testConstructorThrowsWhenDropSectionAbsent() {
        SignalBucket bucket = mock(SignalBucket.class);
        Reson8Config.SoundDefinition def = mock(Reson8Config.SoundDefinition.class);
        when(bucket.getName()).thenReturn("NoDrop");
        when(bucket.getSoundDefinition()).thenReturn(def);
        when(def.drop()).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> new GstDropChannel(bucket, mockWav, toolkitDelegate));
    }

    @Test
    void testTriggerCreatesInstance() {
        // Execute
        channel.trigger(80.0);

        // Verify: Use timeout because of the runAsync
        verify(toolkit, timeout(1000)).makeAppSrc(contains("TestChannel_inst_"));

        // Check if the pad was requested from the mixer
        assertFalse(toolkit.requestedPads.isEmpty(), "A mixer pad should have been requested");
    }

    @Test
    void testNeedDataSlicing() {

        channel.trigger(100.0);

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
        channel.trigger(100.0);

        // Verify the AppSrc was made (wait for async if needed)
        verify(toolkit, timeout(1000)).makeAppSrc(anyString());

        // Verify we have a pad to release later
        assertEquals(1, toolkit.requestedPads.size());
    }

    @Test
    void testAudioDataFlow() {
        channel.trigger(100.0);

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
        channel.trigger(100.0);

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
        channel.trigger(100.0);

        // lastCreatedAppSrc is set at [3] makeAppSrc — too early; addMany is at [10].
        // capturedNeedData is set at [14] connectNeedData (end of constructor) — correct barrier.
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedNeedData != null);

        // NOW you verify on the toolkit SPY
        verify(toolkit).addMany(any(Bin.class), any(Element.class), any(), any(), any());
    }

    @Test
    void testCleanupRemovesInstanceFromBin() {
        channel.trigger(100.0);

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
        channel.trigger(100.0);
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedCleanupTask != null);

        // The channelMixer is shared across all triggers — cleanup must not touch its state.
        Element channelMixer = toolkit.createdElements.get("TestChannel_sum");
        assertNotNull(channelMixer, "channelMixer must be registered in createdElements");

        toolkit.capturedCleanupTask.run();

        verify(toolkit, never()).setElementState(eq(channelMixer), any(State.class));
    }

    @Test
    void testInstanceLinksToMixer() {
        channel.trigger(100.0);

        // linkPads is at [17]; capturedCleanupTask is set at [19] addEosProbe, after linkPads — correct barrier
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedCleanupTask != null);

        // Both args are now exact: the mixer pad from requestedPads and the instanceBin src pad
        // (toolkit.getStaticPad caches the same mock pad for a given element+name, so we can retrieve it)
        Pad mixerPad = toolkit.requestedPads.iterator().next();
        verify(toolkit).linkPads(any(Pad.class), eq(mixerPad));
    }

    @Test
    void testBasicGettersAndSetters() {
        assertEquals(1.0, channel.getGain(), 1e-9);
        channel.setGain(75.5);
        assertEquals(75.5, channel.getGain());
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

        channel.trigger(100.0);
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedNeedData != null);

        // Simulate request: Should trigger EOS immediately
        toolkit.capturedNeedData.needData(toolkit.lastCreatedAppSrc, 4096);

        verify(toolkit.lastCreatedAppSrc).endOfStream();
        verify(toolkit, never()).createBuffer(anyInt());
    }

    @Test
    void testSpamTriggers() {
        channel.trigger(100.0);
        channel.trigger(90.0);

        // Verify two different AppSrcs were created
        verify(toolkit, timeout(1000).times(2)).makeAppSrc(anyString());

        // Verify two different pads were requested from the mixer
        assertEquals(2, toolkit.requestedPads.size());
    }
}