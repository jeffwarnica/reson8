package com.coherentnetworksolutions.reson8.manager.config;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates {@link Reson8Config.SecurityConfig} at startup.
 */
public final class SecurityConfigValidator {

    private SecurityConfigValidator() {}

    /**
     * Appends validation problems to {@code errors}. Blank entries in group lists are ignored; duplicates within a
     * single list are deduplicated (no error).
     */
    public static void collectErrors(Reson8Config.SecurityConfig security, List<String> errors) {
        if (security == null) {
            errors.add("reson8.security is missing");
            return;
        }

        String sentinel = security.anonymousStreamSentinel();
        if (sentinel == null || sentinel.isBlank()) {
            errors.add("reson8.security.anonymous-stream-sentinel must be non-blank");
        }

        Set<String> admin = normalizedGroups(security.adminGroups().orElse(List.of()));
        Set<String> viewer = normalizedGroups(security.viewerGroups().orElse(List.of()));
        Set<String> stream = normalizedGroups(security.streamGroups().orElse(List.of()));

        collectPairwiseOverlaps(admin, viewer, "admin-groups", "viewer-groups", errors);
        collectPairwiseOverlaps(admin, stream, "admin-groups", "stream-groups", errors);
        collectPairwiseOverlaps(viewer, stream, "viewer-groups", "stream-groups", errors);
    }

    private static void collectPairwiseOverlaps(
            Set<String> a,
            Set<String> b,
            String nameA,
            String nameB,
            List<String> errors) {
        Set<String> overlap = new HashSet<>(a);
        overlap.retainAll(b);
        for (String g : overlap) {
            errors.add(
                    "Group '"
                            + g
                            + "' appears in both reson8.security."
                            + nameA
                            + " and reson8.security."
                            + nameB
                            + ". Each group must appear in at most one tier list (precedence when resolving a subject is admin > viewer > stream).");
        }
    }

    static Set<String> normalizedGroups(List<String> raw) {
        Set<String> seen = new LinkedHashSet<>();
        if (raw == null) {
            return seen;
        }
        for (String g : raw) {
            if (g == null || g.isBlank()) {
                continue;
            }
            seen.add(g.trim());
        }
        return seen;
    }
}
