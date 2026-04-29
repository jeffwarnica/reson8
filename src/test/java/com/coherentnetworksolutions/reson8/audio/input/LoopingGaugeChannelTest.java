package com.coherentnetworksolutions.reson8.audio.input;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.awaitility.Awaitility.await;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.elements.PlayBin;

import com.coherentnetworksolutions.reson8.audio.providers.MockGstToolkit;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundDefinition;

import io.quarkus.logging.Log;

public class LoopingGaugeChannelTest {
    private MockGstToolkit toolkit;
    private LoopingGaugeChannel channel;

    @BeforeEach
    void setUp() {
        toolkit = spy(new MockGstToolkit());
        SignalBucket signalBucket = mock(SignalBucket.class);

        when(signalBucket.getName()).thenReturn("TestLoop");
        when(signalBucket.getSoundDefinition()).thenReturn(mock(SoundDefinition.class));
        when(signalBucket.getSoundDefinition().loop()).thenReturn(Optional.of(mock(Reson8Config.LoopConfig.class)));
        when(signalBucket.getSoundDefinition().loop().orElseThrow().filename()).thenReturn("test_loop.wav");
        when(signalBucket.getSoundDefinition().loop().orElseThrow().outputScale()).thenReturn(1.0);
        when(signalBucket.getSoundDefinition().loop().orElseThrow().smoothingrate()).thenReturn(0.02);
        Reson8Config config = mock(Reson8Config.class);

        when(config.audioPath()).thenReturn("/fake/audio/path");

        channel = new LoopingGaugeChannel(signalBucket, config, toolkit); // Passing null for SignalBucket since we
                                                                          // won't use it here
    }

    @AfterEach
    void tearDown() {
        channel.dispose();
    }

    @Test
    void testLoopingCallbackSetsUri() {
        await().atMost(2, TimeUnit.SECONDS).until(() -> toolkit.capturedAboutToFinish != null);

        // 1. Trigger the 'About to Finish' event manually
        assertNotNull(toolkit.capturedAboutToFinish, "Listener should be registered");

        // Create a mock playbin to pass to the callback
        PlayBin mockPlayBin = toolkit.createPlayBin("mockPlayBin");
        when(mockPlayBin.getName()).thenReturn("mockPlayBin");

        // 2. Execute the lambda
        toolkit.capturedAboutToFinish.aboutToFinish(mockPlayBin);

        // 3. Verify the logic: Did it set the URI again?
        verify(toolkit).setElementProperty(eq(mockPlayBin), eq("uri"), anyString());

        Log.debug("Verified: ABOUT_TO_FINISH successfully looped the URI");
    }

    @Test
    void testSetIntensityUpdatesVolume() {
        // 1. Set intensity
        channel.setTargetIntensity(80.0);

        // 2. If you use a scheduler (like WindGauge), use await()
        // If it's direct, just verify.
        // We assume playbin's internal volume or an attached volume element is touched.
        // Element playBin = toolkit.createdElements.get("playbin");
        Log.debugf("created Elements keys are: [%s]", toolkit.createdElements.keySet());
        
        Element volume = toolkit.createdElements.keySet().stream()
            .filter(name -> name.endsWith("vol"))
                .map(toolkit.createdElements::get).findFirst().orElseThrow();

        Log.debugf("Found playBin element: [%s]", volume);
        verify(toolkit, atLeastOnce()).setElementProperty(eq(volume), eq("volume"), 
                argThat(val -> (Double) val > 0.0));
    }

    @Test
    void testDisposeCleanlyShutsDown() {
        // 1. Call dispose
        channel.dispose();

        // 2. Verify GStreamer State Change
        // playbin (or the bin) should be moved to NULL state to release file handles
        // Element playBin = toolkit.createdElements.get("playbin");
        Element playBin = toolkit.createdElements.keySet().stream().filter(name -> name.contains("playbin"))
                .map(toolkit.createdElements::get).findFirst().orElseThrow();
        // atLeastOnce because @AfterEach also calls dispose(), producing a second setState(NULL).
        verify(toolkit, atLeastOnce()).setElementState(eq(playBin), eq(org.freedesktop.gstreamer.State.NULL));

        // 3. Verify Scheduler (if used)
        // If you have a private scheduler, you can't easily check .isShutdown()
        // without reflection, but you can verify that no MORE modulation calls happen.
        // verifyNoMoreInteractions(toolkit);

        // This would be a good idea to check if gst was restarted ever test case.
        // it (should) work when run as a standalone test.
    }
}
