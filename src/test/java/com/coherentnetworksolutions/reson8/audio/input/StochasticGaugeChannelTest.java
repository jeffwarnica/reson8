package com.coherentnetworksolutions.reson8.audio.input;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import com.coherentnetworksolutions.reson8.audio.providers.MockGsToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.StochasticConfig;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

class StochasticGaugeChannelTest {

    @TempDir
    Path tempDir;

    private MockGsToolkit toolkit;
    private StochasticGaugeChannel channel;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak: a production class is calling native GStreamer directly.");
    }

    @BeforeEach
    void setUp() throws IOException {
        toolkit = spy(new MockGsToolkit());

        // Create a real dummy wav file so SoundHitBundle can find it
        Path wavFile = Files.createFile(tempDir.resolve("test_chirp.wav"));
        Files.write(wavFile, new byte[100]);

        Caps mockCaps = mock(Caps.class);
        when(mockCaps.toString()).thenReturn("audio/x-raw,format=S16LE,rate=44100,channels=1");

        CachedWav wav = mock(CachedWav.class);
        when(wav.caps()).thenReturn(mockCaps);
        when(wav.sampleRate()).thenReturn(44100.0);
        when(wav.bytesPerFrame()).thenReturn(2);
        when(wav.pcmData()).thenReturn(new byte[4410 * 2]);

        WavCache mockWavCache = mock(WavCache.class);
        when(mockWavCache.getOrLoad(anyString())).thenReturn(wav);
        when(mockWavCache.getDataFor(anyString())).thenReturn(wav);

        com.coherentnetworksolutions.reson8.manager.config.Reson8Config mockReson8Config = mock(com.coherentnetworksolutions.reson8.manager.config.Reson8Config.class);
        when(mockReson8Config.audioPath()).thenReturn(tempDir.toAbsolutePath().toString());

        StochasticConfig stochasticConfig = mock(StochasticConfig.class);
        when(stochasticConfig.smoothingrate()).thenReturn(0.9);
        when(stochasticConfig.maxSimultaneous()).thenReturn(2);
        when(stochasticConfig.pitchRandomization()).thenReturn(0.0);
        when(stochasticConfig.ceiling()).thenReturn(80.0);
        when(stochasticConfig.directory()).thenReturn(""); // relative to audioPath — use temp root

        var mockDef = mock(Reson8Config.SoundDefinition.class);
        when(mockDef.stochastic()).thenReturn(Optional.of(stochasticConfig));

        SignalBucket mockBucket = mock(SignalBucket.class);
        when(mockBucket.getName()).thenReturn("cricket");
        when(mockBucket.getSoundDefinition()).thenReturn(mockDef);

        channel = new StochasticGaugeChannel(mockBucket, mockReson8Config, mockWavCache, toolkit);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) channel.dispose();
    }

    @Test
    @DisplayName("getSrcElement() returns a non-null Bin")
    void getSrcElement_returnsNonNull() {
        assertNotNull(channel.getSrcElement());
    }

    @Test
    @DisplayName("getCapsString() returns the caps string from the first WAV in the bundle")
    void getCapsString_returnsNonEmpty() {
        assertFalse(channel.getCapsString().isBlank());
    }

    @Test
    @DisplayName("getChannelName() equals the bucket name")
    void getChannelName_equalsBucketName() {
        assertEquals("cricket", channel.getChannelName());
    }

    @Test
    @DisplayName("ceiling is initialised from stochastic config")
    void getCeiling_isInitialisedFromConfig() {
        assertEquals(80.0, channel.getCeiling(), 0.001);
    }

    @Test
    @DisplayName("target intensity is applied and becomes current intensity over time")
    void intensitySmoothing_targetEventuallyReached() {
        channel.setTargetIntensity(50.0);
        await().atMost(3, TimeUnit.SECONDS)
                .until(() -> channel.getCurrentIntensity() > 1.0);
    }

    @Test
    @DisplayName("dispose() sets the channel bin to NULL state")
    void dispose_setsStateToNull() {
        channel.dispose();
        verify(toolkit, atLeastOnce())
                .setElementState(any(), eq(org.freedesktop.gstreamer.State.NULL));
    }
}
