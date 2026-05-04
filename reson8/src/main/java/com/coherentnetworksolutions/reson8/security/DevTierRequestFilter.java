package com.coherentnetworksolutions.reson8.security;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.arc.Unremovable;

/**
 * When {@link Reson8Config.SecurityConfig#devTierHeaderEnabled()} is true (dev / test), parses
 * {@value DevTierRequestFilter#X_RESON8_DEV_TIER} and stores the selection in {@link DevTierContext}.
 * No-op in production when the flag is false.
 */
@Provider
@ApplicationScoped
@Unremovable
@jakarta.annotation.Priority(Priorities.AUTHENTICATION - 200)
public class DevTierRequestFilter implements ContainerRequestFilter {

    public static final String X_RESON8_DEV_TIER = "X-Reson8-Dev-Tier";

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
        DevTierSelection.parse(raw).ifPresent(devTierContext::setSelection);
    }
}
