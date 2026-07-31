package com.coherentnetworksolutions.reson8.manager.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecurityConfigValidatorTest {

    @Test
    @DisplayName("empty tier lists and default sentinel produce no errors")
    void emptyLists_valid() {
        List<String> errors = new ArrayList<>();
        SecurityConfigValidator.collectErrors(sec(List.of(), List.of(), List.of(), "__anonymous__"), errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("null security mapping")
    void nullSecurity_reportsMissing() {
        List<String> errors = new ArrayList<>();
        SecurityConfigValidator.collectErrors(null, errors);
        assertEquals(List.of("reson8.security is missing"), errors);
    }

    @Test
    @DisplayName("blank anonymous sentinel is rejected")
    void blankSentinel_invalid() {
        List<String> errors = new ArrayList<>();
        SecurityConfigValidator.collectErrors(sec(List.of(), List.of(), List.of(), "   "), errors);
        assertTrue(errors.contains("reson8.security.anonymous-stream-sentinel must be non-blank"));
    }

    @Test
    @DisplayName("same group in admin and viewer fails fast")
    void overlapAdminViewer() {
        List<String> errors = new ArrayList<>();
        SecurityConfigValidator.collectErrors(sec(List.of("ops"), List.of("ops"), List.of(), "__anonymous__"), errors);
        assertTrue(errors.stream().anyMatch(s -> s.contains("'ops'") && s.contains("admin-groups") && s.contains("viewer-groups")));
    }

    @Test
    @DisplayName("same group in viewer and stream fails fast")
    void overlapViewerStream() {
        List<String> errors = new ArrayList<>();
        SecurityConfigValidator.collectErrors(sec(List.of(), List.of("x"), List.of("x"), "__anonymous__"), errors);
        assertTrue(errors.stream().anyMatch(s -> s.contains("'x'") && s.contains("viewer-groups") && s.contains("stream-groups")));
    }

    @Test
    @DisplayName("same group in admin and stream fails fast")
    void overlapAdminStream() {
        List<String> errors = new ArrayList<>();
        SecurityConfigValidator.collectErrors(sec(List.of("a"), List.of(), List.of("a"), "__anonymous__"), errors);
        assertTrue(errors.stream().anyMatch(s -> s.contains("'a'") && s.contains("admin-groups") && s.contains("stream-groups")));
    }

    @Test
    @DisplayName("duplicates within a single list are deduplicated (no error)")
    void duplicatesWithinSingleList_allowed() {
        List<String> errors = new ArrayList<>();
        SecurityConfigValidator.collectErrors(sec(List.of("g", "g"), List.of(), List.of(), "__anonymous__"), errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("blank and whitespace-only group entries are ignored")
    void blanksIgnored() {
        List<String> errors = new ArrayList<>();
        SecurityConfigValidator.collectErrors(sec(List.of("", "  "), List.of(), List.of(), "__anonymous__"), errors);
        assertTrue(errors.isEmpty());
    }

    private static Reson8Config.SecurityConfig sec(
            List<String> admin, List<String> viewer, List<String> stream, String sentinel) {
        return new Reson8Config.SecurityConfig() {
            @Override
            public Optional<List<String>> adminGroups() {
                return Optional.of(admin);
            }

            @Override
            public Optional<List<String>> viewerGroups() {
                return Optional.of(viewer);
            }

            @Override
            public Optional<List<String>> streamGroups() {
                return Optional.of(stream);
            }

            @Override
            public String anonymousStreamSentinel() {
                return sentinel;
            }

            @Override
            public boolean loginAvailable() {
                return false;
            }

            @Override
            public boolean devTierCookieEnabled() {
                return false;
            }

            @Override
            public boolean endpointAuthorizationEnabled() {
                return false;
            }
        };
    }
}
