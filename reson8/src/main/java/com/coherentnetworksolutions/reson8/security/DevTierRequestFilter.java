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
 * {@value DevTierRequestFilter#X_RESON8_DEV_TIER} or query {@value DevTierRequestFilter#QUERY_RESON8_DEV_TIER}
 * (same values) and stores the selection in {@link DevTierContext}. The query fallback exists because
 * {@code GET /audio/stream} from an HTML {@code audio} element cannot send custom headers.
 * No-op in production when the flag is false.
 */
@Provider
@ApplicationScoped
@Unremovable
@jakarta.annotation.Priority(Priorities.AUTHENTICATION - 200)
public class DevTierRequestFilter implements ContainerRequestFilter {

    /** Same semantics as {@link #X_RESON8_DEV_TIER}; honored only when dev-tier simulation is enabled. */
    public static final String QUERY_RESON8_DEV_TIER = "reson8-dev-tier";

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
        String raw = requestContext.getUriInfo().getQueryParameters().getFirst(QUERY_RESON8_DEV_TIER);
        DevTierSelection.parse(raw).ifPresent(devTierContext::setSelection);
    }
}
