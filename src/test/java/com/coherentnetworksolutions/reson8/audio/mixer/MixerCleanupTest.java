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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@QuarkusTest
@Timeout(10)
@TestProfile(com.coherentnetworksolutions.reson8.GstTestProfile.class)
class MixerCleanupTest {

    @Inject
    Mixer mixer;

    @BeforeEach
    void setup() {
        Log.infof("Right now this test case has a mixer of type: [%s]", mixer.getClass());
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

    
    @Test
    @DisplayName("Verify Full Cleanup and Identify Zombie Elements")
    void testCleanupEfficiency() {
        // 1. Snapshot the names before adding anything
        Set<String> beforeNames = mixer.getPipeline().getElements().stream()
                .map(Element::getName)
                .collect(Collectors.toSet());
        int baseCount = beforeNames.size();
        Log.infof("Before names: [%s]", beforeNames);
        // 2. Add a channel
        String chName = "diag-ch";
        String expectedPrefix = chName + "::";

        InputChannel mockCh = mock(InputChannel.class);
        when(mockCh.getChannelName()).thenReturn(chName);
        // when(mockCh.supportsGain()).thenReturn(true);
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

        Log.infof("After names: [%s]", beforeNames);
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

    // TODO: This usually never ends. some c level problem?
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