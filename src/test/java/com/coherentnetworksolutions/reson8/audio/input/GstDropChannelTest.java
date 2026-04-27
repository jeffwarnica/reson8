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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.providers.MockGstToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

class GstDropChannelTest {

    private MockGstToolkit toolkit;
    private SignalBucket mockBucket;
    private CachedWav mockWav;
    private GstDropChannel channel;

    @BeforeEach
    void setUp() {
        // 1. Initialize the Mock Toolkit
        MockGstToolkit realToolkit = new MockGstToolkit();
        Caps wavCaps = realToolkit.capsFromString("audio/x-raw");
        toolkit = spy(realToolkit);

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
                () -> new GstDropChannel(bucket, mockWav, toolkit));
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

        // Wait for async wiring
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

        // 1. Simulate the sound finishing (EOS Probe fires)
        toolkit.capturedCleanupTask.run();

        // 2. Verify the pad from requestedPads was released
        Pad thePad = toolkit.requestedPads.iterator().next();
        verify(toolkit).releaseRequestPad(any(), eq(thePad));
    }

    @Test
    void testTriggerAddsElementsToBin() {
        channel.trigger(100.0);

        // WAIT for the async thread to reach this point
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.lastCreatedAppSrc != null);

        // NOW you verify on the toolkit SPY
        verify(toolkit).addMany(any(Bin.class), any(Element.class), any(), any(), any());
    }

    @Test
    void testCleanupRemovesInstanceFromBin() {
        channel.trigger(100.0);

        // 1. Wait for the async thread to finish construction
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.capturedCleanupTask != null);

        // 2. Capture which bin was created so we can check it
        Bin instanceBin = (Bin) toolkit.createdElements.keySet().stream()
                .filter(name -> name.contains("_inst_"))    // drop instance elements
                .filter(name -> name.contains("_bin"))      // in particular, the Bin
                .map(toolkit.createdElements::get)
                .findFirst()
                .orElseThrow();

        // 3. Fire the cleanup task (Simulating the EOS probe)
        toolkit.capturedCleanupTask.run();

        // 4. Verify the toolkit was told to remove it
        verify(toolkit).removeElementFromBin(any(Bin.class), eq(instanceBin));

        // 5. Verify the pad was released
        verify(toolkit).releaseRequestPad(any(), any(Pad.class));
    }

    @Test
    void testInstanceLinksToMixer() {
        channel.trigger(100.0);

        // 1. Wait for async construction
        await().atMost(1, TimeUnit.SECONDS).until(() -> toolkit.lastCreatedAppSrc != null);

        // 2. We need to verify that the Bin's GhostPad (src)
        // was linked to the Mixer's RequestPad (sink)

        // We can use ArgumentCaptors to be precise, or just verify the toolkit call
        verify(toolkit).linkPads(any(Pad.class), eq(toolkit.requestedPads.iterator().next()));
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