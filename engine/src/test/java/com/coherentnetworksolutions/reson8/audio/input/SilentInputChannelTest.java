package com.coherentnetworksolutions.reson8.audio.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock; 
import static org.mockito.Mockito.when;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import com.coherentnetworksolutions.reson8.audio.providers.MockGsToolkit;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

public class SilentInputChannelTest {
    GsToolkit toolkit = new MockGsToolkit();

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak: a production class is calling " +
                "Caps.fromString() or ElementFactory.make() directly.");
    }

    @Test
    void testSilentChannelState() {
        SignalBucket mockBucket = mock(SignalBucket.class);
        when(mockBucket.getName()).thenReturn("SilentTest");

        SilentInputChannel silent = new SilentInputChannel(mockBucket, toolkit);

        silent.setCeiling(50.0);
        silent.setTargetIntensity(75.0);

        assertEquals(50.0, silent.getCeiling());
        assertEquals(75.0, silent.getTargetIntensity());
        assertEquals("SilentTest", silent.getChannelName());
        assertNull(silent.getSrcElement());
    }

    @Test
    void capsAreCaps() {
        SignalBucket mockBucket = mock(SignalBucket.class);
        when(mockBucket.getName()).thenReturn("SilentTest");

        SilentInputChannel silent = new SilentInputChannel(mockBucket, toolkit);
        assertEquals("audio/x-raw", silent.getCapsString());
    }

    @Test
    void testEmptyThingsDoNotThrow() {
        SignalBucket mockBucket = mock(SignalBucket.class);
        when(mockBucket.getName()).thenReturn("SilentTest");

        SilentInputChannel silent = new SilentInputChannel(mockBucket, toolkit);

        // Just call all the methods and make sure nothing throws
        silent.start();
        silent.dispose();
    }
}
