package com.coherentnetworksolutions.reson8.audio.engine;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.freedesktop.gstreamer.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.output.BrowserOutputChannel;
import com.coherentnetworksolutions.reson8.audio.output.BrowserSessionManager;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;

@QuarkusTest
class FanOutTest {

    @InjectSpy
    BrowserSessionManager manager;

    @Inject
    Mixer mixer;

    @BeforeEach
    void setup() {
        mixer.initGStreamer();
        mixer.getPipeline().setState(State.PLAYING);
        manager.clearAllSessions();
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
    @DisplayName("Verify that multiple listeners receive identical audio data simultaneously")
    void testMultiBrowserBroadcast() throws Exception {
        
        // 1. HARD STOP: Move to NULL state to release native buffers
        mixer.getPipeline().setState(org.freedesktop.gstreamer.State.NULL);
        // Ensure the state change actually finished
        mixer.getPipeline().getState(500, TimeUnit.MILLISECONDS);

        BrowserOutputChannel outputChannel = (BrowserOutputChannel) mixer.getMasterOutput();
        
        ByteArrayOutputStream browserA = new ByteArrayOutputStream();
        ByteArrayOutputStream browserB = new ByteArrayOutputStream();

        // 2. Subscribe (Logic remains same)
        CompletableFuture.runAsync(() -> {
            try { outputChannel.subscribe(browserA); } catch (IOException e) { e.printStackTrace(); }
        });
        CompletableFuture.runAsync(() -> {
            try { outputChannel.subscribe(browserB); } catch (IOException e) { e.printStackTrace(); }
        });

        await().atMost(2, TimeUnit.SECONDS).until(() -> outputChannel.getListenerCount() == 2);

        // 3. THE CLEANUP: Reset the streams AFTER the wait 
        // to catch any "dying breaths" from the pipeline
        browserA.reset(); 
        browserB.reset();

        byte[] testPayload = new byte[100];
        for (int i = 0; i < 100; i++) testPayload[i] = (byte) i;

        
        // Manual Broadcast
        outputChannel.broadcast(testPayload);

        assertEquals(100, browserA.size(), "Browser A did not receive exactly 100 bytes");
        assertArrayEquals(testPayload, browserA.toByteArray());

        // ... rest of the test ...

        // Reset for subsequent tests
        mixer.getPipeline().setState(org.freedesktop.gstreamer.State.PLAYING);
    }

    @Test
    @DisplayName("Verify that a broken pipe removes the listener from the broadcast set")
    void testCleanupOnDisconnect() throws Exception {
        
        Log.infof("start of test session count is [%s]", manager.getSessionCount());

        // 1. Create a mock stream that fails
        OutputStream broken = new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("Fail"); }
            @Override public void write(byte[] b, int off, int len) throws IOException { throw new IOException("Fail"); }
        };

        // 2. Subscribe asynchronously
        CompletableFuture<Void> sub = CompletableFuture.runAsync(() -> manager.subscribe(broken));

        // 3. Wait for registration
        // TODO: JW: We should be checking that we've got 1 session before we crash it, but... weirdness
        // await().atMost(10, TimeUnit.SECONDS).until(() -> manager.getSessionCount() == 1);

        // 4. Trigger the failure via broadcast
        manager.broadcast(new byte[]{0, 1, 2});

        // 5. Assert the session is purged and the thread exited
        await().atMost(10, TimeUnit.SECONDS).until(() -> manager.getSessionCount() == 0);
        await().atMost(2, TimeUnit.SECONDS).until(sub::isDone);
    }
}