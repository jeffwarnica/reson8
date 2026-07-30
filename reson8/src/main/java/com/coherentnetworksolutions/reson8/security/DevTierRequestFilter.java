package com.coherentnetworksolutions.reson8.security;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.arc.Unremovable;

/**
 * When {@link Reson8Config.SecurityConfig#devTierHeaderEnabled()} is true (dev / test), parses
 * {@value DevTierRequestFilter#X_RESON8_DEV_TIER} first, then falls back to cookie
 * {@value DevTierRequestFilter#DEV_TIER_COOKIE}.
 * <p>
 * Header takes precedence so API requests can override an existing browser cookie in dev/test.
 * Cookie fallback exists because {@code GET /audio/stream} from an HTML {@code audio} element
 * cannot send custom headers.
 * No-op in production when the flag is false.
 */
@Provider
@ApplicationScoped
@Unremovable
@jakarta.annotation.Priority(Priorities.AUTHENTICATION - 200)
public class DevTierRequestFilter implements ContainerRequestFilter {

    /** Header used by SPA/API fetch requests in dev/test. */
    public static final String X_RESON8_DEV_TIER = "X-Reson8-Dev-Tier";
    /** Cookie used by browser media requests (e.g. {@code /audio/stream}) in dev/test. */
    public static final String DEV_TIER_COOKIE = "reson8-dev-tier";

    private final Reson8Config config;
    private final DevTierContext devTierContext;

    @Inject
    public DevTierRequestFilter(Reson8Config config, DevTierContext devTierContext) {
        this.config = config;
        this.devTierContext = devTierContext;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!config.security().devTierHeaderEnabled()) {
            return;
        }
        String raw = requestContext.getHeaderString(X_RESON8_DEV_TIER);
        if (raw == null || raw.isBlank()) {
            Cookie cookie = requestContext.getCookies().get(DEV_TIER_COOKIE);
            raw = cookie != null ? cookie.getValue() : null;
        }
        DevTierSelection.parse(raw).ifPresent(devTierContext::setSelection);
    }
}
