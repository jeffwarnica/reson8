package com.coherentnetworksolutions.reson8.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

class AccessTierResolverTest {

    @Test
    @DisplayName("anonymous + sentinel in stream-groups -> STREAM")
    void anonymousSentinel_stream() {
        Reson8Config.SecurityConfig sec = sec(List.of(), List.of(), List.of("__anonymous__"), "__anonymous__");
        assertEquals(AccessTier.STREAM, AccessTierResolver.resolveTier(Set.of(), true, Optional.empty(), sec));
    }

    @Test
    @DisplayName("empty stream-groups + anonymous -> NONE")
    void emptyStreamAnonymous_none() {
        Reson8Config.SecurityConfig sec = sec(List.of(), List.of(), List.of(), "__anonymous__");
        assertEquals(AccessTier.NONE, AccessTierResolver.resolveTier(Set.of(), true, Optional.empty(), sec));
    }

    @Test
    @DisplayName("authenticated admin group wins over viewer")
    void precedenceAdminOverViewer() {
        Reson8Config.SecurityConfig sec =
                sec(List.of("ops"), List.of("ops"), List.of("__anonymous__"), "__anonymous__");
        assertEquals(AccessTier.ADMIN, AccessTierResolver.resolveTier(Set.of("ops"), false, Optional.empty(), sec));
    }

    @Test
    @DisplayName("authenticated viewer when not in admin")
    void viewerTier() {
        Reson8Config.SecurityConfig sec = sec(List.of("adm"), List.of("read"), List.of(), "__anonymous__");
        assertEquals(AccessTier.VIEWER, AccessTierResolver.resolveTier(Set.of("read"), false, Optional.empty(), sec));
    }

    @Test
    @DisplayName("authenticated stream group (non-sentinel)")
    void streamGroupTier() {
        Reson8Config.SecurityConfig sec = sec(List.of(), List.of(), List.of("radio", "__anonymous__"), "__anonymous__");
        assertEquals(AccessTier.STREAM, AccessTierResolver.resolveTier(Set.of("radio"), false, Optional.empty(), sec));
    }

    @Test
    @DisplayName("sentinel string does not grant authenticated stream tier by role name")
    void sentinelNotRoleForAuth() {
        Reson8Config.SecurityConfig sec = sec(List.of(), List.of(), List.of("__anonymous__"), "__anonymous__");
        assertEquals(AccessTier.NONE, AccessTierResolver.resolveTier(Set.of("__anonymous__"), false, Optional.empty(), sec));
    }

    @Test
    @DisplayName("no matching groups -> NONE")
    void noMatch_none() {
        Reson8Config.SecurityConfig sec = sec(List.of("a"), List.of("b"), List.of("c"), "__anonymous__");
        assertEquals(AccessTier.NONE, AccessTierResolver.resolveTier(Set.of("x"), false, Optional.empty(), sec));
    }

    @ParameterizedTest
    @CsvSource({
        "ADMIN, ADMIN",
        "VIEWER, VIEWER",
        "STREAM, STREAM"
    })
    @DisplayName("dev selection forces tier ignoring JWT groups")
    void devSelectionForcesTier(String selectionName, String expectedTierName) {
        DevTierSelection sel = DevTierSelection.valueOf(selectionName);
        AccessTier expected = AccessTier.valueOf(expectedTierName);
        Reson8Config.SecurityConfig sec = sec(List.of("adm"), List.of(), List.of(), "__anonymous__");
        assertEquals(
                expected,
                AccessTierResolver.resolveTier(Set.of("not-used"), false, Optional.of(sel), sec));
    }

    @Test
    @DisplayName("dev ANONYMOUS uses sentinel like unauthenticated resolution")
    void devAnonymous_respectsSentinel() {
        Reson8Config.SecurityConfig withSentinel = sec(List.of(), List.of(), List.of("__anonymous__"), "__anonymous__");
        assertEquals(
                AccessTier.STREAM,
                AccessTierResolver.resolveTier(Set.of(), false, Optional.of(DevTierSelection.ANONYMOUS), withSentinel));

        Reson8Config.SecurityConfig noSentinel = sec(List.of(), List.of(), List.of(), "__anonymous__");
        assertEquals(
                AccessTier.NONE,
                AccessTierResolver.resolveTier(Set.of(), false, Optional.of(DevTierSelection.ANONYMOUS), noSentinel));
    }

    private static Reson8Config.SecurityConfig sec(
            List<String> admin, List<String> viewer, List<String> stream, String sentinel) {
        return new Reson8Config.SecurityConfig() {
            @Override
            public java.util.Optional<List<String>> adminGroups() {
                return java.util.Optional.of(admin);
            }

            @Override
            public java.util.Optional<List<String>> viewerGroups() {
                return java.util.Optional.of(viewer);
            }

            @Override
            public java.util.Optional<List<String>> streamGroups() {
                return java.util.Optional.of(stream);
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
