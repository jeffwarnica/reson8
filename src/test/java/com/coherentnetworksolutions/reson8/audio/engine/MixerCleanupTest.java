package com.coherentnetworksolutions.reson8.audio.engine;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.State;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.factories.DropFactory;
import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.input.OneShotChannel;
import com.coherentnetworksolutions.reson8.audio.sound.SoundManager;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundDefinition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class MixerCleanupTest {

    @Inject
    Mixer mixer;

    @Inject
    DropFactory dropFactory;

    @Inject
    SoundManager soundRegistry;

    @BeforeEach
    void setup() {
        mixer.initGStreamer();
        mixer.getPipeline().setState(State.PLAYING);
        // Small delay to ensure background threads from previous tests 
        // have actually exited their while loops
        try { Thread.sleep(500); } catch (InterruptedException e) {}
    }

    @AfterEach
    void tearDown() {
        // Everything is now handled internally by the Mixer
        mixer.dispose();

        // Recommendation: Keep a tiny sleep if you are seeing
        // "Address already in use" errors with the browser stream
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
        }
    }

    // TODO: rework this test
    // @Test
    // @DisplayName("Verify that dynamic drop elements are fully purged after playback")
    // void testDropCleanupLeak() throws InterruptedException {
    //     // 1. Capture the baseline (Noise generators + Master chain)
    //     int baselineCount = mixer.getPipeline().getElements().size();
        
    //     SoundDefinition soundDefinition = soundRegistry.get("forest/chirp_long1");

    //     // 2. Trigger a drop (use a small file you have cached)
    //     channel.trigger();

    //     // 3. Verify elements were added
    //     // We use a small wait because pipeline.add happens on an executor thread
    //     await().atMost(1, TimeUnit.SECONDS).until(() -> 
    //         mixer.getPipeline().getElements().size() > baselineCount
    //     );

    //     int midDropCount = mixer.getPipeline().getElements().size();
    //     assertTrue(midDropCount >= baselineCount + 4, 
    //         "Expected at least 4 new elements (appsrc, conv, res, queue)");

    //     // 4. Wait for the cleanup logic to fire
    //     // (Assuming your delayedExecutor is set to 2 seconds, we wait 5 for safety)
    //     await()
    //         .atMost(5, TimeUnit.SECONDS)
    //         .pollInterval(500, TimeUnit.MILLISECONDS)
    //         .until(() -> mixer.getPipeline().getElements().size() == baselineCount);

    //     // 5. Final Audit
    //     List<Element> finalElements = mixer.getPipeline().getElements();
    //     assertEquals(baselineCount, finalElements.size(), 
    //         "Pipeline element count did not return to baseline. Leak detected!");

    //     // 6. Inspect the names of remaining elements for any "drop-" prefix
    //     for (Element e : finalElements) {
    //         assertTrue(!e.getName().contains("drop-"), 
    //             "Found lingering dynamic element: " + e.getName());
    //     }
    // }

    @Test
    @DisplayName("Verify Full Cleanup and Identify Zombie Elements")
    void testCleanupEfficiency() {
        // 1. Snapshot the names before adding anything
        Set<String> beforeNames = mixer.getPipeline().getElements().stream()
                .map(Element::getName)
                .collect(Collectors.toSet());
        int baseCount = beforeNames.size();
        Log.infof("Before names: [%f]", beforeNames);
        // 2. Add a channel
        String chName = "diag-ch";
        String expectedPrefix = chName + "::";

        InputChannel mockCh = mock(InputChannel.class);
        when(mockCh.getChannelName()).thenReturn(chName);
        when(mockCh.supportsGain()).thenReturn(true);
        // Note: If this source name is static, it's a prime suspect!
        when(mockCh.getSrcElement()).thenReturn(ElementFactory.make("fakesrc", expectedPrefix + "fakesrc_static_name"));

        mixer.addInputChannel(mockCh);
        int midCount = mixer.getPipeline().getElements().size();
        assertTrue(midCount > baseCount, "Pipeline should have grown");

        // 3. Remove the channel
        mixer.removeInputChannel(chName);

        // 4. Snapshot after removal
        Set<String> afterNames = mixer.getPipeline().getElements().stream()
                .map(Element::getName)
                .collect(Collectors.toSet());

        Log.infof("After names: [%f]", beforeNames);
        // 5. Identify Zombies
        if (afterNames.size() > baseCount) {
            Set<String> zombies = new HashSet<>(afterNames);
            zombies.removeAll(beforeNames);
            Log.errorf("CLEANUP FAILURE! Zombie elements detected: %s", zombies);
            
            // This will print exactly which element stayed behind
            fail("Pipeline contains zombie elements: " + zombies);
        }

        assertEquals(baseCount, afterNames.size(), "Pipeline element count mismatch!");
    }

    @Test
    @DisplayName("Trick Test: Substring Name Collision")
    void testPrefixSafety() {
        // We have two channels where one name is a substring of the other
        String shortName = "cpu";
        String longName = "cpu-noise";

        // Add both
        mixer.addInputChannel(createMockChannel(shortName)); // Will be cpu::123::...
        mixer.addInputChannel(createMockChannel(longName));  // Will be cpu-noise::456::...

        int countWithBoth = mixer.getPipeline().getElements().size();

        // Remove the short one
        mixer.removeInputChannel(shortName);

        // TRICK CHECK:
        // If we used .startsWith("cpu"), it might have deleted "cpu-noise" too.
        // Because we use .startsWith("cpu::"), "cpu-noise::" is safe!
        
        assertTrue(mixer.getInputChannels().containsKey(longName), 
            "The longer channel name was accidentally deleted by the shorter prefix!");
        
        assertTrue(mixer.getPipeline().getElements().size() < countWithBoth);
    }

    private InputChannel createMockChannel(String name) {
        InputChannel mockCh = mock(InputChannel.class);
        when(mockCh.getChannelName()).thenReturn(name);
        when(mockCh.getSrcElement()).thenReturn(ElementFactory.make("fakesrc", name + "_src"));
        return mockCh;
    }
}