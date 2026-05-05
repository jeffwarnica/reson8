package com.coherentnetworksolutions.reson8.security;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.arc.Unremovable;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Single authorization gate for tier-protected REST routes when {@link Reson8Config.SecurityConfig#endpointAuthorizationEnabled()}
 * is {@code true}.
 * <p>
 * Covers {@code /audio/stream} (validated before the streaming handler runs — {@link com.coherentnetworksolutions.reson8.rest.AudioStreamResource}
 * stays {@code @Singleton}, so declarative security interceptors are not relied on there), plus {@code /audio/control},
 * {@code /audio/drop}, and {@code /api/debug}.
 */
@Provider
@ApplicationScoped
@Unremovable
@jakarta.annotation.Priority(Priorities.AUTHORIZATION)
public class TierEndpointAuthorizationFilter implements ContainerRequestFilter {

    private final Reson8Config config;
    private final AccessTierResolver accessTierResolver;
    private final DevTierContext devTierContext;
    private final SecurityIdentity identity;

    @Inject
    public TierEndpointAuthorizationFilter(
            Reson8Config config,
            AccessTierResolver accessTierResolver,
            DevTierContext devTierContext,
            SecurityIdentity identity) {
        this.config = config;
        this.accessTierResolver = accessTierResolver;
        this.devTierContext = devTierContext;
        this.identity = identity;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!config.security().endpointAuthorizationEnabled()) {
            return;
        }
        String method = requestContext.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return;
        }
        String path = TierPath.normalize(requestContext);
        if (!isTierProtectedPath(path)) {
            return;
        }

        CapabilitiesResponse caps = accessTierResolver.resolve(identity, devTierContext);

        if (path.startsWith("/audio/stream")) {
            if (!caps.canStream()) {
                requestContext.abortWith(TierAuthorizationResponses.forbidden());
            }
            return;
        }

        if (path.startsWith("/audio/control")) {
            boolean controlRead =
                    ("GET".equalsIgnoreCase(method)
                                    && (path.endsWith("/channels") || path.endsWith("/state")))
                            || ("HEAD".equalsIgnoreCase(method) && controlHeadAllowed(path));
            if (controlRead) {
                if (!caps.canViewControlState()) {
                    requestContext.abortWith(TierAuthorizationResponses.forbidden());
                }
                return;
            }
            if (!caps.canMutate()) {
                requestContext.abortWith(TierAuthorizationResponses.forbidden());
            }
            return;
        }

        if (path.startsWith("/audio/drop")) {
            if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                if (!caps.canViewControlState()) {
                    requestContext.abortWith(TierAuthorizationResponses.forbidden());
                }
                return;
            }
            if (!caps.canMutate()) {
                requestContext.abortWith(TierAuthorizationResponses.forbidden());
            }
            return;
        }

        if (path.startsWith("/api/debug")) {
            if (!caps.canUseDebug()) {
                requestContext.abortWith(TierAuthorizationResponses.forbidden());
            }
        }
    }

    private static boolean isTierProtectedPath(String path) {
        return path.startsWith("/audio/stream")
                || path.startsWith("/audio/control")
                || path.startsWith("/audio/drop")
                || path.startsWith("/api/debug");
    }

    private static boolean controlHeadAllowed(String path) {
        return path.endsWith("/channels") || path.endsWith("/state");
    }
}
