package com.coherentnetworksolutions.reson8.security;

import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Normalizes request paths for stable prefix checks in {@code ContainerRequestFilter}s.
 * <p>
 * Uses {@link jakarta.ws.rs.core.UriInfo#getPath(boolean)} with {@code decode = true} (decoded path segment). On the
 * Quarkus REST stack, querying a non-decoded path is not supported and fails at runtime; this follows the Jakarta REST
 * API contract and product documentation — not bytecode inspection.
 */
final class TierPath {

    private TierPath() {}

    static String normalize(ContainerRequestContext ctx) {
        String p = ctx.getUriInfo().getPath(true);
        if (p == null || p.isEmpty()) {
            return "/";
        }
        return p.startsWith("/") ? p : "/" + p;
    }
}
