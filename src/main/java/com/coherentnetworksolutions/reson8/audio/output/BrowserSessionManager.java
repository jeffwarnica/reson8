package com.coherentnetworksolutions.reson8.audio.output;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class BrowserSessionManager implements OutputChannelManager {

    // Map each stream to its "alive" flag
    private final Map<OutputStream, AtomicBoolean> sessions = new ConcurrentHashMap<>();

    public void subscribe(OutputStream os) {
        AtomicBoolean isAlive = new AtomicBoolean(true);
        sessions.put(os, isAlive);
        Log.infof("New browser session registered. Active sessions: %d", sessions.size());

        try {
            while (isAlive.get()) {
                Thread.sleep(100); 
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sessions.remove(os);
            Log.info("Browser session closed and removed.");
        }
    }

    public void broadcast(byte[] data) {
        sessions.forEach((os, isAlive) -> {
            try {
                os.write(data);
                os.flush();
            } catch (IOException e) {
                Log.debugf("Broadcast failed for a session (broken pipe). Signaling exit.");
                isAlive.set(false); 
            }
        });
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public Map<OutputStream, AtomicBoolean> getSessions() {
        return sessions;
    }    

    public void clearAllSessions() {
        Log.info("Force clearing all browser sessions for test reset.");
        // 1. Signal all while loops to exit
        sessions.values().forEach(isAlive -> isAlive.set(false));
        // 2. Clear the map
        sessions.clear();
    }

}