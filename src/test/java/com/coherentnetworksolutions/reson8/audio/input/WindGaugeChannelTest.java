package com.coherentnetworksolutions.reson8.audio.input;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.providers.MockGstToolkit;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;

class WindGaugeChannelTest {

    @BeforeAll
    static void ensureGstInitialized() {
        if (!Gst.isInitialized()) {
            Gst.init(WindGaugeChannelTest.class.getSimpleName());
        }
    }

    private MockGstToolkit toolkit;
    private SignalBucket mockBucket;
    private WindGaugeChannel channel;
    private ProceduralConfig mockProc;

    @BeforeEach
    void setUp() {
        toolkit = spy(new MockGstToolkit());
        mockBucket = mock(SignalBucket.class);
        mockProc = mock(ProceduralConfig.class);

        // Setup config mocks
        when(mockBucket.getName()).thenReturn("TestWind");
        when(mockBucket.getProcedureConf()).thenReturn(mockProc);
        when(mockProc.intensity()).thenReturn(50.0);
        when(mockProc.smoothingrate()).thenReturn(0.8); // High smoothing for fast tests

        // Setup SoundDefinition for baseGain
        var mockDef = mock(Reson8Config.SoundDefinition.class);
        var mockProcedural = mock(Reson8Config.ProceduralConfig.class);
        when(mockBucket.getSoundDefinition()).thenReturn(mockDef);
        when(mockDef.procedural()).thenReturn(Optional.of(mockProcedural));
        when(mockProcedural.gain()).thenReturn(100.0);

        channel = new WindGaugeChannel(mockBucket, toolkit);
    }

    @AfterEach
    void tearDown() {
        channel.dispose();
    }

    @Test
    void testIntensitySmoothingRamp() {
        // 1. Set a high target intensity
        channel.setTargetIntensity(100.0);

        // 2. Use Awaitility to wait for the scheduler to move currentIntensity
        // We verify that the toolkit eventually receives a 'cutoff' set call higher
        // than the base 40Hz
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // Verify that the filter element received a 'cutoff' update
            // We search through the toolkit interactions for the filter element
            verify(toolkit, atLeastOnce()).setElementProperty(argThat(el -> el.getName().contains("filter")),
                    eq("cutoff"), argThat(val -> (Double) val > 40.0));
        });
    }

    @Test
    void testGainScaling() {
        // Test that setting gain translates through our VolumeScaler logic
        channel.setGain(50.0);

        // Verify the toolkit was called to set volume on the volume element
        verify(toolkit, atLeastOnce()).setElementProperty(argThat(el -> el.getName().contains("vol")), eq("volume"),
                anyDouble());
    }

    @Test
    void testDisposeShutsDown() {
        channel.dispose();

        // Verify state is set to NULL (atLeastOnce because @AfterEach also calls dispose).
        verify(toolkit, atLeastOnce()).setElementState(any(), eq(org.freedesktop.gstreamer.State.NULL));

        // Note: You could use reflection to check if scheduler.isShutdown() is true
    }

    @Test
    void testQuadraticScalingMath() {
        Element filter = toolkit.createdElements.keySet().stream()
                .filter(name -> name.contains("filter"))
                .map(toolkit.createdElements::get)
                .findFirst()
                .orElseThrow();

        Log.debugf("Got 'filter' element: [%s]", filter);
        assertNotNull(filter, "Filter element should have been created");

        // 1. Force currentIntensity to 100 via setIntensity and a wait
        channel.setTargetIntensity(100.0);

        // We wait for the smoothing to reach 100
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // At 100% intensity, cutoff should be near 2540Hz
            // (40 base + 2500 * (1.0^2))
            verify(toolkit, atLeastOnce()).setElementProperty(eq(filter), eq("cutoff"),
                    argThat(val -> val instanceof Number && ((Number) val).doubleValue() > 2500.0));
        });

        // 2. Set intensity to 50%
        channel.setTargetIntensity(50.0);

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // At 50% intensity, curve = 0.5^2 = 0.25
            // Cutoff = 40 + (2500 * 0.25) = 665Hz
            verify(toolkit, atLeastOnce()).setElementProperty(eq(filter), eq("cutoff"),
                    argThat(val -> (Double) val < 1000.0));
        });
    }

}