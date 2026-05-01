package com.coherentnetworksolutions.reson8.audio.mixer;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.output.from.MixerOutputToClientManagerChannel;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.freedesktop.gstreamer.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@Timeout(10)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestProfile(com.coherentnetworksolutions.reson8.GsTestProfile.class)
class MixerPipelineIT {

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
    @Order(1)
    @DisplayName("Verify Pipeline Initialization")
    void testInit() {
        assertNotNull(mixer.getPipeline());
        assertNotNull(mixer.getMixerElement());
        await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> State.PLAYING == mixer.getPipeline().getState() );

        assertTrue(mixer.isReady(), "Mixer not ready after startup.");
        // assertEquals(State.PLAYING, mixer.getPipeline().getState(0));
    }

    @Test
    @Order(2)
    @DisplayName("Test Input Channel Addition and Volume Scaling")
    void testAddInput() {
        InputChannel mockChannel = mock(InputChannel.class);
        Element fakeSrc = ElementFactory.make("fakesrc", "test_src");
        
        when(mockChannel.getChannelName()).thenReturn("test-ch");
        when(mockChannel.getSrcElement()).thenReturn(fakeSrc);
        when(mockChannel.getCeiling()).thenReturn(80.0);

        mixer.addInputChannel(mockChannel);
        
        assertTrue(mixer.getInputChannels().containsKey("test-ch"));
        
        // Verify volume logic didn't crash
        mixer.setInputChannelVolume("test-ch", 0.5);

        double returnedVol = mixer.getInputChannelVolume("test-ch");

        //verify we get volume back
        assertEquals(0.5, returnedVol);
    }

    @Test
    @Order(5)
    @DisplayName("Verify Master Volume element is linked")
    void testMasterVolume() {
        // This ensures the setMasterVolume call finds the element we added in initGStreamer
        assertDoesNotThrow(() -> mixer.setMasterVolume(0.8));
    }

    @Test
    @Order(6)
    @DisplayName("Test Input Channel Removal and Element Disposal")
    void testInChannelRemoval() {
        // Add then remove
        InputChannel mockChannel = mock(InputChannel.class);
        Element fakeSrc = ElementFactory.make("fakesrc", "remove-test");
        when(mockChannel.getChannelName()).thenReturn("remove-test");
        when(mockChannel.getSrcElement()).thenReturn(fakeSrc);

        mixer.addInputChannel(mockChannel);
        mixer.removeInputChannel("remove-test");

        assertFalse(mixer.getInputChannels().containsKey("remove-test"));
        // Success means the loop with e.dispose() handled the elements correctly
    }

    @Test
    @Order(7)
    @DisplayName("Test Output Channel Removal and Element Disposal")
    void testOutChannelRemoval() {
        // Add then remove
        MixerOutputToClientManagerChannel mockChannel = mock(MixerOutputToClientManagerChannel.class);
        // Element fakeSink = ElementFactory.make("fakesink", "remove-test");
        Element fakeSink = mock(Element.class);
        Pad fakePad = mock(Pad.class);
        when(mockChannel.getChannelName()).thenReturn("remove-test");
        when(mockChannel.getElement()).thenReturn(fakeSink);
        when(fakeSink.getState()).thenReturn(State.PLAYING);
        when(fakeSink.getStaticPad("sink")).thenReturn(fakePad);
        when(fakePad.getPeer()).thenReturn(null);

        mixer.addOutputChannel(mockChannel);
        mixer.removeOutputChannel("remove-test");

        assertFalse(mixer.getInputChannels().containsKey("remove-test"));
        // Success means the loop with e.dispose() handled the elements correctly
    }

    @Test
    @DisplayName("Ensure Mixer handles missing channels gracefully")
    void testRemoveNonExistentChannel() {
        // Hits the 'if (channel == null)' branch in removeInputChannel
        assertDoesNotThrow(() -> mixer.removeInputChannel("does-not-exist"));
    }

    @Test
    @DisplayName("Ensure Mixer handles volume updates for non-existent channels")
    void testVolumeForInvalidChannel() {
        // Hits the 'else' branch in setInputChannelVolume
        assertDoesNotThrow(() -> mixer.setInputChannelVolume("ghost-channel", 0.5));
    }
    
    @Test
    @DisplayName("Test setOutputChannelVolume - Success and Failure branches")
    void testOutputVolumeBranches() {
        // 1. Path: Success (Channel exists)
        MixerOutputToClientManagerChannel mockOutput = mock(MixerOutputToClientManagerChannel.class);
        Element mockVolumeElement = ElementFactory.make("volume", "test_out_vol");
        
        when(mockOutput.getChannelName()).thenReturn("test-out");
        when(mockOutput.getElement()).thenReturn(mockVolumeElement);
        
        mixer.addOutputChannel(mockOutput);
        assertDoesNotThrow(() -> mixer.setOutputChannelVolume("test-out", 0.5));
        
        // 2. Path: Failure (Channel null) - Hits the Log.errorf branch
        assertDoesNotThrow(() -> mixer.setOutputChannelVolume("non-existent-out", 0.8));
    }

}
