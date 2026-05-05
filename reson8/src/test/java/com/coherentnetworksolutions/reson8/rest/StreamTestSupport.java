package com.coherentnetworksolutions.reson8.rest;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import com.coherentnetworksolutions.reson8.audio.output.to.BrowserSessionManager;

/** Matches {@link AudioStreamResourceTest}: WAV header chunk then a broadcast pulse so the connection does not stall. */
final class StreamTestSupport {

    private StreamTestSupport() {}

    static void consumeOpeningChunk(URL streamUrl, Map<String, String> headers, BrowserSessionManager sessions)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) streamUrl.openConnection();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            conn.setRequestProperty(e.getKey(), e.getValue());
        }
        conn.setReadTimeout(8000);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new AssertionError("Expected HTTP 200 from stream, got " + code);
        }
        try (InputStream stream = conn.getInputStream()) {
            byte[] headerBuffer = new byte[44];
            int bytesRead = 0;
            while (bytesRead < 44) {
                int result = stream.read(headerBuffer, bytesRead, 44 - bytesRead);
                if (result == -1) {
                    break;
                }
                bytesRead += result;
            }
            if (bytesRead != 44) {
                throw new AssertionError("Expected full WAV header (44 bytes)");
            }
            sessions.broadcast(new byte[] {0x01});
            stream.read(new byte[1]);
        }
    }
}
