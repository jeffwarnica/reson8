package com.coherentnetworksolutions.reson8.audio.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock; 
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.providers.GstToolkit;
import com.coherentnetworksolutions.reson8.audio.providers.MockGstToolkit;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

public class SilentInputChannelTest {
    GstToolkit toolkit = new MockGstToolkit();

    @Test
    void testSilentChannelState() {
        SignalBucket mockBucket = mock(SignalBucket.class);
        when(mockBucket.getName()).thenReturn("SilentTest");

        SilentInputChannel silent = new SilentInputChannel(mockBucket, null);

        silent.setGain(50.0);
        silent.setTargetIntensity(75.0);

        assertEquals(50.0, silent.getGain());
        assertEquals(75.0, silent.getTargetIntensity());
        assertEquals("SilentTest", silent.getChannelName());
        assertNull(silent.getSrcElement());
    }

    @Test
    void capsAreCaps() {
        SignalBucket mockBucket = mock(SignalBucket.class);
        when(mockBucket.getName()).thenReturn("SilentTest");

        SilentInputChannel silent = new SilentInputChannel(mockBucket, toolkit);
        assertEquals("audio/x-raw", silent.getCaps().toString());
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
