package com.coherentnetworksolutions.reson8.audio.mixer;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.State;
import org.junit.jupiter.api.Timeout;
import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@QuarkusTest
@Timeout(30)
@TestProfile(com.coherentnetworksolutions.reson8.GsTestProfile.class)
class MixerCleanupIT {

    @Inject
    Mixer mixer;

    /**
     * Snapshot of the pipeline taken after it has fully quiesced in {@link #setup()}.
     * Used by {@link #testCleanupEfficiency()} as the baseline so that channels added
     * asynchronously by SignalManager (in response to the "mixer-ready" event emitted
     * by initGStreamer) are counted as part of the baseline rather than as zombies.
     */
    private Set<String> quiescentNames;

    @BeforeEach
    void setup() {
        Log.infof("Right now this test case has a mixer of type: [%s]", mixer.getClass());
        mixer.initGStreamer();
        mixer.getPipeline().setState(State.PLAYING);

        // Wait for the pipeline to stabilise: the "mixer-ready" event published inside
        // initGStreamer() triggers SignalManager.attemptWiring() on the Vert.x event
        // loop, which asynchronously adds all configured channels.
        //
        // With real channel factories active (GsTestProfile, audiopath=gs), channels
        // can arrive in rapid bursts separated by short pauses. Two consecutive equal
        // counts at 300ms were sufficient when factories were vetoed (no channels),
        // but insufficient now that real WindGaugeChannel/LoopingGaugeChannel etc. are
        // being wired. Three consecutive equal counts at 500ms (1 full second of proven
        // stability) rules out burst gaps before the baseline is captured.
        AtomicInteger lastCount = new AtomicInteger(-1);
        AtomicInteger stableStreak = new AtomicInteger(0);
        await().atMost(10, TimeUnit.SECONDS)
               .pollDelay(0, TimeUnit.MILLISECONDS)
               .pollInterval(500, TimeUnit.MILLISECONDS)
               .until(() -> {
                   int now = mixer.getPipeline().getElements().size();
                   if (now > 0 && now == lastCount.get()) {
                       return stableStreak.incrementAndGet() >= 3;
                   }
                   lastCount.set(now);
                   stableStreak.set(0);
                   return false;
               });

        quiescentNames = mixer.getPipeline().getElements().stream()
                .map(Element::getName)
                .collect(Collectors.toSet());
        Log.infof("Quiescent pipeline (%d elements): %s", quiescentNames.size(), quiescentNames);
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

    
    @Test
    @DisplayName("Verify Full Cleanup and Identify Zombie Elements")
    void testCleanupEfficiency() {
        // Baseline was captured in @BeforeEach after the pipeline fully quiesced.
        int baseCount = quiescentNames.size();

        // 1. Add a diagnostic channel
        String chName = "diag-ch";
        InputChannel mockCh = mock(InputChannel.class);
        when(mockCh.getChannelName()).thenReturn(chName);
        when(mockCh.getSrcElement()).thenReturn(
                ElementFactory.make("fakesrc", chName + "::fakesrc_static_name"));

        mixer.addInputChannel(mockCh);
        assertTrue(mixer.getPipeline().getElements().size() > baseCount,
                "Pipeline should have grown after addInputChannel");

        // 2. Remove the channel
        mixer.removeInputChannel(chName);

        // 3. Snapshot after removal
        Set<String> afterNames = mixer.getPipeline().getElements().stream()
                .map(Element::getName)
                .collect(Collectors.toSet());

        Log.infof("After names: [%s]", afterNames);

        // 4. Identify zombies — elements present after removal that were not in baseline
        if (afterNames.size() > baseCount) {
            Set<String> zombies = new HashSet<>(afterNames);
            zombies.removeAll(quiescentNames);
            Log.errorf("CLEANUP FAILURE! Zombie elements detected: %s", zombies);
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
