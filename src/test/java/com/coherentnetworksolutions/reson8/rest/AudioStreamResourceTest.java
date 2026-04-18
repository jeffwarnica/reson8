package com.coherentnetworksolutions.reson8.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.output.to.BrowserSessionManager;
import com.coherentnetworksolutions.reson8.audio.utils.WavHeaderUtils;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class AudioStreamResourceTest {

    @Inject
    BrowserSessionManager sessionManager;

    @Test
    public void testAudioStreamFlow() throws Exception {
        Log.infof("My BrowserSessionManager is type [%s]", sessionManager.getClass());
        byte[] expectedHeader = WavHeaderUtils.createPcmHeader(48000, 16, 2);
        Log.infof("Size of expected(wav) header is [%s]", expectedHeader.length);
        // Response response = RestAssured.get("/audio/stream");
        // InputStream stream = response.asInputStream();

        // 1. Use a raw connection to ensure no library buffering
        java.net.URL url = new java.net.URL("http://localhost:8081/audio/stream");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setReadTimeout(5000); // 5 second read timeout

        InputStream stream = conn.getInputStream();

        // 2. Consume the header to "prime" the concatenating Multi
        byte[] headerBuffer = new byte[44];
        
        int bytesRead = 0;
        while (bytesRead < 44) {
            int result = stream.read(headerBuffer, bytesRead, 44 - bytesRead);
            if (result == -1) break;
            bytesRead += result;
        }
        
        assertEquals(44, bytesRead);
        assertArrayEquals(expectedHeader, headerBuffer);

        // 3. STIMULUS: Act like GStreamer
        byte[] testBytes = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        sessionManager.broadcast(testBytes); // <--- This is the missing heartbeat!

        // 4. Verify the output
        byte[] result = new byte[4];
        int read = stream.read(result);
        
        assertArrayEquals(testBytes, result);
    }
}