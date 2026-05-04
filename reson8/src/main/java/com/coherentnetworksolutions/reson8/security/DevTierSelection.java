package com.coherentnetworksolutions.reson8.security;

import java.util.Locale;
import java.util.Optional;

/**
 * Values for the {@code X-Reson8-Dev-Tier} header (development / test only).
 */
public enum DevTierSelection {
    ADMIN,
    VIEWER,
    STREAM,
    /** Simulate an unauthenticated caller for tier resolution. */
    ANONYMOUS;

    public static Optional<DevTierSelection> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "admin" -> Optional.of(ADMIN);
            case "viewer" -> Optional.of(VIEWER);
            case "stream" -> Optional.of(STREAM);
            case "anonymous" -> Optional.of(ANONYMOUS);
            default -> Optional.empty();
        };
    }
}
