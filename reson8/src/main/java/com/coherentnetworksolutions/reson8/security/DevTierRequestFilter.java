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
 * When {@link Reson8Config.SecurityConfig#devTierCookieEnabled()} is true (dev / test), parses
 * cookie {@value DevTierRequestFilter#DEV_TIER_COOKIE} for tier simulation.
 * <p>
 * Cookie-only behavior keeps API and media requests consistent for the same browser session.
 * No-op in production when the flag is false.
 */
@Provider
@ApplicationScoped
@Unremovable
@jakarta.annotation.Priority(Priorities.AUTHENTICATION - 200)
public class DevTierRequestFilter implements ContainerRequestFilter {

    /** Cookie used by browser requests in dev/test. */
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
        if (!config.security().devTierCookieEnabled()) {
            return;
        }
        Cookie cookie = requestContext.getCookies().get(DEV_TIER_COOKIE);
        String raw = cookie != null ? cookie.getValue() : null;
        DevTierSelection.parse(raw).ifPresent(devTierContext::setSelection);
    }
}
