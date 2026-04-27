package com.coherentnetworksolutions.reson8.audio.input;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.providers.MockGstToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.DropConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.LoopConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundDefinition;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundType;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

public class GstChannelFactoryTests {
    private GstChannelFactory factory;
    // private MockGstToolkit toolkit;
    private MockGstToolkit toolkit;

    @BeforeAll
    static void ensureGstInitialized() {
        if (!Gst.isInitialized()) {
            Gst.init(GstChannelFactoryTests.class.getSimpleName());
        }
    }

    @BeforeEach
    void setUp() {
        factory = new GstChannelFactory();
        MockGstToolkit realToolkit = new MockGstToolkit();
        toolkit = spy(realToolkit);
        factory.gstToolkit = toolkit; // Inject the spy toolkit

        factory.wavCache = mock(WavCache.class);
        factory.config = mock(Reson8Config.class);
        when(factory.config.audioPath()).thenReturn("/tmp");
    }

    @Test
    void testBuildLoopChannel() {
        SignalBucket signalBucket = mock(SignalBucket.class);
        SoundDefinition mockDef = mock(Reson8Config.SoundDefinition.class);
        LoopConfig loopConfig = mock(Reson8Config.LoopConfig.class);
        Reson8Config config = mock(Reson8Config.class);

        when(config.audioPath()).thenReturn("/fake/audio/path");

        when(signalBucket.getSoundDefinition()).thenReturn(mockDef);
        when(signalBucket.getName()).thenReturn("TestLoop");
        when(signalBucket.getSoundType()).thenReturn(SoundType.LOOP);

        when(mockDef.loop()).thenReturn(Optional.of(loopConfig));

        when(loopConfig.filename()).thenReturn("trickling_water.wav");
        when(loopConfig.gain()).thenReturn(100.0);

        when(mockDef.loop()).thenReturn(Optional.of(loopConfig));
        when(loopConfig.filename()).thenReturn("trickling_water.wav");
        when(loopConfig.gain()).thenReturn(100.0);

        // Add other necessary mocks for construction...

        InputChannel channel = factory.buildChannel(signalBucket);
        assertTrue(channel instanceof LoopingGaugeChannel);
    }

    @Test
    void testBuildWindChannel() {
        SignalBucket signalBucket = mock(SignalBucket.class);
        ProceduralConfig proc = mock(ProceduralConfig.class);
        SoundDefinition mockDef = mock(Reson8Config.SoundDefinition.class);

        when(proc.intensity()).thenReturn(50.0);
        when(proc.smoothingrate()).thenReturn(0.02);
        when(proc.gain()).thenReturn(100.0);
        
        when(signalBucket.getSoundDefinition()).thenReturn(mockDef);
        when(signalBucket.getProcedureConf()).thenReturn(proc);
        when(signalBucket.getSoundType()).thenReturn(SoundType.PROCEDURAL);

        when(mockDef.procedural()).thenReturn(Optional.of(proc));
        when(mockDef.name()).thenReturn("wind");
        when(signalBucket.getName()).thenReturn("wind");
        when(proc.className()).thenReturn("WindGaugeChannel");
        when(proc.gain()).thenReturn(100.0);

        InputChannel channel = factory.buildChannel(signalBucket);
        assertTrue(channel instanceof WindGaugeChannel);
    }

    @Test
    void testBuildDropChannel() {
        SignalBucket signalBucket = mock(SignalBucket.class);
        SoundDefinition mockDef = mock(Reson8Config.SoundDefinition.class);
        DropConfig dropConfig = mock(Reson8Config.DropConfig.class);
        WavCache.CachedWav cachedWav = mock(WavCache.CachedWav.class);

        when(signalBucket.getSoundDefinition()).thenReturn(mockDef);
        when(signalBucket.getSoundType()).thenReturn(SoundType.DROP);

        when(mockDef.drop()).thenReturn(Optional.of(dropConfig));
        when(dropConfig.filename()).thenReturn("drop_sound.wav");
        when(dropConfig.gain()).thenReturn(1.0);

        when(factory.wavCache.getOrLoad("drop_sound.wav")).thenReturn(cachedWav);

        InputChannel channel = factory.buildChannel(signalBucket);
        assertTrue(channel instanceof GstDropChannel);
    }

    @Test
    void testBuildDropChannelFallbackOnWavCacheFailure() {
        SignalBucket signalBucket = mock(SignalBucket.class);
        SoundDefinition mockDef = mock(Reson8Config.SoundDefinition.class);
        DropConfig dropConfig = mock(Reson8Config.DropConfig.class);

        when(signalBucket.getSoundDefinition()).thenReturn(mockDef);
        when(signalBucket.getSoundType()).thenReturn(SoundType.DROP);

        when(mockDef.drop()).thenReturn(Optional.of(dropConfig));
        when(dropConfig.filename()).thenReturn("drop_sound.wav");

        when(factory.wavCache.getOrLoad("drop_sound.wav")).thenThrow(new RuntimeException("Wav cache failure"));

        InputChannel channel = factory.buildChannel(signalBucket);
        assertTrue(channel instanceof SilentInputChannel);
    }

    @Test
    void testUnknownProceduralThrows() {
        SignalBucket signalBucket = mock(SignalBucket.class);
        ProceduralConfig proc = mock(ProceduralConfig.class);
        SoundDefinition mockDef = mock(Reson8Config.SoundDefinition.class);

        when(signalBucket.getSoundDefinition()).thenReturn(mockDef);
        when(signalBucket.getProcedureConf()).thenReturn(proc);
        when(signalBucket.getSoundType()).thenReturn(SoundType.PROCEDURAL);

        when(mockDef.procedural()).thenReturn(Optional.of(proc));
        when(proc.className()).thenReturn("GhostNoiseChannel");

        assertThrows(UnsupportedOperationException.class, () -> factory.buildChannel(signalBucket));
    }
}
