package com.coherentnetworksolutions.reson8.audio.engine;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    void resetStuff() {
        // Ensure the mixer is actually playing before we try to attach listeners
        mixer.getPipeline().setState(org.freedesktop.gstreamer.State.PLAYING);
        manager.clearAllSessions();
        // Small delay to ensure background threads from previous tests 
        // have actually exited their while loops
        try { Thread.sleep(500); } catch (InterruptedException e) {}
    }
    
    @Test
    @DisplayName("Verify that multiple listeners receive identical audio data simultaneously")
    void testMultiBrowserBroadcast() throws Exception {
        
        //Pause so to not be streaming our noise 
        mixer.getPipeline().setState(org.freedesktop.gstreamer.State.PAUSED);
        BrowserOutputChannel outputChannel = (BrowserOutputChannel) mixer.getMasterOutput();
        
        // 1. Create two 'Mock Browsers' (OutputStreams)
        ByteArrayOutputStream browserA = new ByteArrayOutputStream();
        ByteArrayOutputStream browserB = new ByteArrayOutputStream();

        // 2. Register them. 
        // Since writeToStream blocks, we run them in virtual threads or futures.
        CompletableFuture.runAsync(() -> {
            try {
                outputChannel.subscribe(browserA);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
        CompletableFuture.runAsync(() -> {
            try {
                outputChannel.subscribe(browserB);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });

        // Wait a beat to ensure the registration logic inside writeToStream has completed
        await().atMost(1, TimeUnit.SECONDS).until(() -> outputChannel.getListenerCount() == 2);

        // 3. Generate dummy audio data (e.g., 100 bytes of 'noise')
        byte[] testPayload = new byte[100];
        for (int i = 0; i < 100; i++) testPayload[i] = (byte) i;

        browserA.reset(); 
        browserB.reset();

        outputChannel.broadcast(testPayload);

        
        assertEquals(100, browserA.size(), "Browser A did not receive exactly 100 bytes");
        assertArrayEquals(testPayload, browserA.toByteArray(), "Whatever Browser A received, it wasn't what we sent");

        assertEquals(100, browserB.size(), "Browser B did not receive exactly 100 bytes");
        assertArrayEquals(testPayload, browserB.toByteArray(), "Whatever Browser B received, it wasn't what we sent");

        assertArrayEquals(browserA.toByteArray(), browserB.toByteArray(), "Browser A and B received different overall data");

        
        // 6. Test 'Stealing' - Push another buffer
        byte[] secondPayload = new byte[] { 10, 20, 30 };
        outputChannel.broadcast(secondPayload);
        
        assertEquals(103, browserA.size());
        assertEquals(103, browserB.size());

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
        // TODO: JW: We shold be checking that we've got 1 session before we crash it, but... weirdness
        // await().atMost(10, TimeUnit.SECONDS).until(() -> manager.getSessionCount() == 1);

        // 4. Trigger the failure via broadcast
        manager.broadcast(new byte[]{0, 1, 2});

        // 5. Assert the session is purged and the thread exited
        await().atMost(10, TimeUnit.SECONDS).until(() -> manager.getSessionCount() == 0);
        await().atMost(2, TimeUnit.SECONDS).until(sub::isDone);
    }
}