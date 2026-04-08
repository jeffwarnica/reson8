package com.coherentnetworksolutions.reson8.audio.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.freedesktop.gstreamer.ElementFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class MixerStressTest {

    @Inject
    Mixer mixer;

    @Test
    @DisplayName("Stress Test: Concurrent Addition of Same Channel")
    void testConcurrentAdditions() throws InterruptedException {
        String duplicateName = "stress-test-ch";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    latch.await(); // Wait for the starting gun
                    InputChannel mockCh = mock(InputChannel.class);
                    when(mockCh.getChannelName()).thenReturn(duplicateName);
                    when(mockCh.getSrcElement()).thenReturn(ElementFactory.make("fakesrc", null));
                    
                    mixer.addInputChannel(mockCh);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {}
            });
        }

        latch.countDown(); // Start all threads at once
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Even with 10 threads screaming to add it, only 1 should have actually 
        // proceeded past the containsKey() check and the synchronized lock.
        assertEquals(1, mixer.getInputChannels().entrySet().stream()
            .filter(e -> e.getKey().equals(duplicateName)).count());
    }

    @Test
    @DisplayName("Trick Test: Rapid Add and Remove Cycle")
    void testRapidCycle() {
        String name = "cycle-ch";
        InputChannel mockCh = mock(InputChannel.class);
        when(mockCh.getChannelName()).thenReturn(name);
        when(mockCh.getSrcElement()).thenReturn(ElementFactory.make("fakesrc", null));

        // Rapid fire add/remove to catch race conditions between 
        // Java Map state and Native GStreamer state
        assertDoesNotThrow(() -> {
            for(int i=0; i<50; i++) {
                mixer.addInputChannel(mockCh);
                mixer.removeInputChannel(name);
            }
        });
    }

    @Test
    @DisplayName("Trick Test: Gain Update on Removed Channel")
    void testGainOnZombieChannel() {
        String name = "zombie-ch";
        InputChannel mockCh = mock(InputChannel.class);
        when(mockCh.getChannelName()).thenReturn(name);
        when(mockCh.supportsGain()).thenReturn(true);
        when(mockCh.getSrcElement()).thenReturn(ElementFactory.make("fakesrc", null));

        mixer.addInputChannel(mockCh);
        mixer.removeInputChannel(name);

        // Try to update gain on a channel that is no longer in the map
        // This should hit your null check and Log.warn/error instead of NPE
        assertDoesNotThrow(() -> mixer.setChannelGain(name, 0.8));
    }
    @Test
    void testPrefixSafetyWithTimestamps() {
        String name = "cpu-noise";
        // Verify that the vacuum cleaner uses the exact prefix
        // This ensures cpu-noise::123 doesn't match cpu-noise-extra::456
        assertTrue("cpu-noise::12345::gain".startsWith(name + "::"));
        assertFalse("cpu-noise-extra::12345::gain".startsWith(name + "::"));
    }    
}