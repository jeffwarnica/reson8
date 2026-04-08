package com.coherentnetworksolutions.reson8.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.output.BrowserSessionManager;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;

@QuarkusTest
class AudioControlResourceTest {

    @Inject
    BrowserSessionManager browserSessionManager;

    @Test
    @DisplayName("GET /audio/control/channels should return active channel names")
    void testGetChannels() {
        given()
          .when().get("/audio/control/channels")
          .then()
             .statusCode(200)
             .contentType(ContentType.JSON)
             .body("name", hasItems("cpu-noise", "mem-noise"))
             .body("find { it.name == 'cpu-noise' }.supportsGain", is(false));
            // Check for the name property inside the array of objects
            // Optionally verify that noise channels correctly report no gain support
    }

    @Test
    @DisplayName("POST /audio/control/volume should update channel gain")
    void testSetVolume() {
        // Construct the request body matching your ChannelVolumeRequest inner class
        String requestBody = "{\"channel\": \"cpu-noise\", \"volume\": 0.75}";

        given()
          .contentType(ContentType.JSON)
          .body(requestBody)
          .when().post("/audio/control/gain")
          .then()
             .statusCode(200);
             
        // Logic check: In a perfect world, we'd now poll a 'getVolume' endpoint 
        // to verify it stuck, but for now, 200 confirms the Mixer didn't crash.
    } 


    // @Test
    // @DisplayName("GET /audio/drop should list available WAV files")
    // void testListDrops() {
    //     given()
    //     .when().get("/audio/drop")
    //     .then()
    //         .statusCode(200)
    //         .contentType(ContentType.JSON)
    //         // Assuming you have at least one test wav like 'pod_start.wav'
    //         .body("size()", greaterThanOrEqualTo(1));
    // }

    @Test
    @DisplayName("POST /audio/drop should return 400 for null dropName")
    void testTriggerDropNullName() {
        String badJson = "{\"volume\": 1.0}"; // Missing dropName

        given()
        .contentType(ContentType.JSON)
        .body(badJson)
        .when().post("/audio/drop")
        .then()
            .statusCode(400)
            .body(org.hamcrest.CoreMatchers.is("dropName is required"));
    }

    @Test
    @DisplayName("POST /audio/drop should return 400 for empty dropName")
    void testTriggerDropEmptyName() {
        String badJson = "{\"dropName\": \"\", \"volume\": 1.0}";

        given()
        .contentType(ContentType.JSON)
        .body(badJson)
        .when().post("/audio/drop")
        .then()
            .statusCode(400)
            .body(org.hamcrest.CoreMatchers.is("dropName is required"));
    }

    @Test
    @DisplayName("POST /audio/drop should return 200 for valid drop")
    void testTriggerDropSuccess() {
        String validJson = "{\"dropName\": \"pod_start.wav\", \"volume\": 1.0}";

        given()
        .contentType(ContentType.JSON)
        .body(validJson)
        .when().post("/audio/drop")
        .then()
            .statusCode(200);
    }


    @Test
    @DisplayName("GET /audio/stream should provide a valid WAV header and stay open")
    void testStreamConnectivity() throws Exception {
        // 1. Get the base URL from Quarkus (usually http://localhost:8081)
        String url = "http://localhost:8081/audio/stream";
        
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");

        // 2. Connect and check headers
        assertEquals(200, connection.getResponseCode());
        assertEquals("audio/wav", connection.getContentType());
        assertEquals("keep-alive", connection.getHeaderField("Connection"));

        // 3. Read ONLY the first 44 bytes (The WAV Header)
        try (java.io.InputStream is = connection.getInputStream()) {
            byte[] header = new byte[44];
            int totalRead = 0;
            while (totalRead < 44) {
                int read = is.read(header, totalRead, 44 - totalRead);
                if (read == -1) break; // Stream ended prematurely
                totalRead += read;
            }
            
            assertEquals(44, totalRead, "Should have been able to read the full 44-byte WAV header");

            // 4. Verify the RIFF/WAVE signature
            assertEquals('R', (char)header[0]);
            assertEquals('I', (char)header[1]);
            assertEquals('F', (char)header[2]);
            assertEquals('F', (char)header[3]);
            assertEquals('W', (char)header[8]);
            assertEquals('A', (char)header[9]);
            assertEquals('V', (char)header[10]);
            assertEquals('E', (char)header[11]);
        } finally {
            // 5. CRITICAL: Close the connection manually to stop the streaming
            connection.disconnect();
        }
    }

    @Test
    @DisplayName("POST /audio/drop validation checks")
    void testTriggerDropValidation() {
        // 1. Test Null dropName (400)
        given()
        .contentType(ContentType.JSON)
        .body("{\"volume\": 0.5}") // No dropName
        .when().post("/audio/drop")
        .then()
            .statusCode(400)
            .body(is("dropName is required"));

        // 2. Test Empty dropName (400)
        given()
        .contentType(ContentType.JSON)
        .body("{\"dropName\": \"\", \"volume\": 0.5}")
        .when().post("/audio/drop")
        .then()
            .statusCode(400);

        // 3. Test Success Path (200)
        given()
        .contentType(ContentType.JSON)
        .body("{\"dropName\": \"pod_start.wav\", \"volume\": 1.0}")
        .when().post("/audio/drop")
        .then()
            .statusCode(200);
    }

    @Test
    @DisplayName("GET /audio/drop listing")
    void testListDrops() {
        given()
        .when().get("/audio/drop")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", not(eq(0))); // Standard Hamcrest check
    }

}