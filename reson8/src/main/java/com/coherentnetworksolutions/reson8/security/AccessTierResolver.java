package com.coherentnetworksolutions.reson8.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.security.identity.SecurityIdentity;

/**
 * Maps {@link SecurityIdentity} plus security configuration to an {@link AccessTier} and UI capabilities.
 * Precedence for authenticated subjects: admin groups &gt; viewer groups &gt; stream groups (excluding the anonymous
 * sentinel, which applies only to anonymous callers).
 */
@ApplicationScoped
public class AccessTierResolver {

    private final Reson8Config config;

    @Inject
    public AccessTierResolver(Reson8Config config) {
        this.config = config;
    }

    /**
     * Builds capabilities for the HTTP request, applying an optional dev-tier selection when present.
     */
    public CapabilitiesResponse resolve(SecurityIdentity identity, DevTierContext devTierContext) {
        Reson8Config.SecurityConfig sec = config.security();
        Optional<DevTierSelection> dev = sec.devTierHeaderEnabled()
                ? devTierContext.selection()
                : Optional.empty();
        Set<String> roles = extractRoles(identity);
        boolean anonymous = identity == null || identity.isAnonymous();
        AccessTier tier = resolveTier(roles, anonymous, dev, sec);
        boolean devHeaderOn = sec.devTierHeaderEnabled();
        return forTier(tier, devHeaderOn, devHeaderOn, sec.loginAvailable());
    }

    /**
     * Core tier resolution; used by {@link #resolve(SecurityIdentity, DevTierContext)} and unit tests.
     */
    static AccessTier resolveTier(
            Set<String> roles,
            boolean anonymous,
            Optional<DevTierSelection> devSelection,
            Reson8Config.SecurityConfig sec) {
        if (devSelection.isPresent()) {
            return switch (devSelection.get()) {
                case ADMIN -> AccessTier.ADMIN;
                case VIEWER -> AccessTier.VIEWER;
                case STREAM -> AccessTier.STREAM;
                case ANONYMOUS -> tierForAnonymous(sec);
            };
        }

        if (anonymous) {
            return tierForAnonymous(sec);
        }

        Set<String> normRoles = normalizeNames(roles);
        List<String> admins = sec.adminGroups().orElse(List.of());
        List<String> viewers = sec.viewerGroups().orElse(List.of());
        List<String> streams = sec.streamGroups().orElse(List.of());

        if (containsAnyMember(normRoles, admins)) {
            return AccessTier.ADMIN;
        }
        if (containsAnyMember(normRoles, viewers)) {
            return AccessTier.VIEWER;
        }

        String sentinelNorm = normalizeName(sec.anonymousStreamSentinel());
        Set<String> streamWithoutSentinel = streams.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !normalizeName(s).equals(sentinelNorm))
                .map(AccessTierResolver::normalizeName)
                .collect(Collectors.toSet());

        if (containsAnyMemberByString(normRoles, streamWithoutSentinel)) {
            return AccessTier.STREAM;
        }

        return AccessTier.NONE;
    }

    static AccessTier tierForAnonymous(Reson8Config.SecurityConfig sec) {
        if (streamListContainsAnonymousSentinel(sec)) {
            return AccessTier.STREAM;
        }
        return AccessTier.NONE;
    }

    private static boolean streamListContainsAnonymousSentinel(Reson8Config.SecurityConfig sec) {
        String sentinelNorm = normalizeName(sec.anonymousStreamSentinel());
        if (sentinelNorm.isEmpty()) {
            return false;
        }
        for (String g : sec.streamGroups().orElse(List.of())) {
            if (g != null && normalizeName(g).equals(sentinelNorm)) {
                return true;
            }
        }
        return false;
    }

    static CapabilitiesResponse forTier(
            AccessTier tier, boolean showDevRoleSelector, boolean devUi, boolean loginAvailable) {
        return switch (tier) {
            case ADMIN -> new CapabilitiesResponse(
                    tier, true, true, true, true, showDevRoleSelector, devUi, loginAvailable);
            case VIEWER -> new CapabilitiesResponse(
                    tier, true, true, false, false, showDevRoleSelector, devUi, loginAvailable);
            case STREAM -> new CapabilitiesResponse(
                    tier, true, false, false, false, showDevRoleSelector, devUi, loginAvailable);
            case NONE -> new CapabilitiesResponse(
                    tier, false, false, false, false, showDevRoleSelector, devUi, loginAvailable);
        };
    }

    private static boolean containsAnyMember(Set<String> normalizedRoles, List<String> groupList) {
        Set<String> normalizedGroups = normalizeGroupList(groupList);
        return containsAnyMemberByString(normalizedRoles, normalizedGroups);
    }

    private static boolean containsAnyMemberByString(Set<String> normalizedRoles, Set<String> normalizedGroups) {
        for (String r : normalizedRoles) {
            if (normalizedGroups.contains(r)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizeGroupList(List<String> groupList) {
        Set<String> out = new HashSet<>();
        if (groupList == null) {
            return out;
        }
        for (String g : groupList) {
            if (g == null || g.isBlank()) {
                continue;
            }
            out.add(normalizeName(g));
        }
        return out;
    }

    private static Set<String> normalizeNames(Set<String> names) {
        Set<String> out = new HashSet<>();
        if (names == null) {
            return out;
        }
        for (String n : names) {
            if (n == null || n.isBlank()) {
                continue;
            }
            out.add(normalizeName(n));
        }
        return out;
    }

    private static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> extractRoles(SecurityIdentity identity) {
        Set<String> roles = new HashSet<>();
        if (identity == null) {
            return roles;
        }
        if (identity.getRoles() != null) {
            roles.addAll(identity.getRoles());
        }
        Object groupsClaim = identity.getAttribute("groups");
        if (groupsClaim instanceof String s) {
            roles.add(s);
        } else if (groupsClaim instanceof Collection<?> c) {
            for (Object o : c) {
                if (o != null) {
                    roles.add(o.toString());
                }
            }
        } else if (groupsClaim instanceof String[] arr) {
            for (String s : arr) {
                if (s != null) {
                    roles.add(s);
                }
            }
        }
        return roles;
    }
}
