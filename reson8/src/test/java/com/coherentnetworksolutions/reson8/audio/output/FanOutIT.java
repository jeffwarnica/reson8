package com.coherentnetworksolutions.reson8.audio.output;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.coherentnetworksolutions.reson8.audio.output.to.BrowserSessionManager;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@Timeout(10)
@TestProfile(com.coherentnetworksolutions.reson8.GsTestProfile.class)
class FanOutIT {

    @Inject //Spy
    BrowserSessionManager browserSessionManager;

    // @Inject
    // Mixer mixer;

    // @BeforeEach
    // void setup() {
    //     mixer.initGStreamer();
    //     mixer.getPipeline().setState(State.PLAYING);
    //     manager.clearAllSessions();
    //     // Small delay to ensure background threads from previous tests 
    //     // have actually exited their while loops
    //     try { Thread.sleep(500); } catch (InterruptedException e) {}
    // }

    // @AfterEach
    // void tearDown() {
    //     // Everything is now handled internally by the Mixer
    //     // mixer.dispose();

    //     // Recommendation: Keep a tiny sleep if you are seeing
    //     // "Address already in use" errors with the browser stream
    //     try {
    //         Thread.sleep(50);
    //     } catch (InterruptedException e) {
    //     }
    // }
        
    @Test
    @DisplayName("Verify BrowserSessionManager fan-out isolation")
    void testSessionManagerFanOut() throws IOException {
        Log.infof("Right now this test case has a BrowserSessionManager of type: [%s]", browserSessionManager.getClass());
        // If BrowserSessionManager is a proper @ApplicationScoped bean,
        // you can just inject it, but a fresh instance is even safer for byte-accuracy.
        // GstBrowserSessionManager manager = new GstBrowserSessionManager();

        ByteArrayOutputStream clientA = new ByteArrayOutputStream();
        ByteArrayOutputStream clientB = new ByteArrayOutputStream();

        browserSessionManager.subscribe()
            .subscribe()
            .with(
                bytes -> {
                    try {
                        clientA.write(bytes);
                    } catch (IOException e) {
                        // Handle the write error
                    }
                },
                failure -> Log.error("Subscription failed", failure)
            );
        browserSessionManager.subscribe()
                .subscribe()
                .with(
                        bytes -> {
                            try {
                                clientB.write(bytes);
                            } catch (IOException e) {
                                // Handle the write error
                            }
                        },
                        failure -> Log.error("Subscription failed", failure));


        byte[] fakeGstBuffer = new byte[100];
        new Random().nextBytes(fakeGstBuffer);

        clientA.reset();
        clientB.reset();

        // This is the call the GStreamer NEW_SAMPLE handler usually makes
        browserSessionManager.broadcast(fakeGstBuffer);

        // Now it's 100% deterministic
        // JW: ByteArrayOutputStream is Javas way of allowing buffer overrun problems. 
        //     
        // assertEquals(100, clientA.size());
        // assertEquals(100, clientB.size());
        assertArrayEquals(fakeGstBuffer, Arrays.copyOfRange(clientA.toByteArray(),0,100));
        assertArrayEquals(fakeGstBuffer, Arrays.copyOfRange(clientB.toByteArray(), 0, 100));
    }


}
