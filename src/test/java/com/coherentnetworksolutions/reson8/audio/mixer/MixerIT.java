package com.coherentnetworksolutions.reson8.audio.mixer;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import com.coherentnetworksolutions.reson8.audio.output.to.GstBrowserSessionManager;

@QuarkusTest
@Tag("integration")
@TestProfile(com.coherentnetworksolutions.reson8.GstTestProfile.class)
public class MixerIT {

    @Inject
    Mixer mixer;

    @Inject
    GstBrowserSessionManager sessionManager;

    // @BeforeEach
    // void ensurePipelineIsPlaying() {
    //     // Ensure the engine is actually running
    //     mixer.getPipeline().setState(org.freedesktop.gstreamer.State.PLAYING);
    //     // Wait for it to settle into playing state
    //     mixer.getPipeline().getState(1, TimeUnit.SECONDS);
    // }

    // @Test
    // @DisplayName("Verify live audio pipeline reaches subscribers")
    // @Timeout(value = 10, unit = TimeUnit.SECONDS)
    // void testLivePipelineFlow() {
    //     ByteArrayOutputStream virtualBrowser = new ByteArrayOutputStream();

    //     // 1. Subscribe to the live session manager
    //     sessionManager.subscribe(virtualBrowser);

    //     // 2. Wait for GStreamer to push at least some data through the appsink
    //     // We use Awaitility because the buffer handoff is asynchronous
    //     await()
    //             .atMost(5, TimeUnit.SECONDS)
    //             .until(() -> virtualBrowser.size() > 0);

    //     // 3. Verify that we are receiving valid data (not just empty)
    //     int capturedSize = virtualBrowser.size();
    //     assertTrue(capturedSize > 0, "No audio data was received from the GStreamer pipeline");

    //     // Optional: Log the throughput for visibility in CI
    //     System.out.println("Captured " + capturedSize + " bytes of live audio from pipeline.");

    //     // 4. Cleanup to prevent leaking the stream in the manager
    //     sessionManager.unsubsquirrel(virtualBrowser);
    // }

    // @Test
    // @DisplayName("Verify multiple concurrent live subscribers")
    // @Timeout(value = 10, unit = TimeUnit.SECONDS)
    // void testMultiSubscriberLiveFlow() {
    //     ByteArrayOutputStream browserA = new ByteArrayOutputStream();
    //     ByteArrayOutputStream browserB = new ByteArrayOutputStream();

    //     sessionManager.subscribe(browserA);
    //     sessionManager.subscribe(browserB);

    //     // Wait for both to have data
    //     await().atMost(5, TimeUnit.SECONDS).until(() -> browserA.size() > 1000 && browserB.size() > 1000);

    //     assertTrue(browserA.size() > 0);
    //     assertTrue(browserB.size() > 0);

    //     sessionManager.unsubsquirrel(browserA);
    //     sessionManager.unsubsquirrel(browserB);
    // }
}