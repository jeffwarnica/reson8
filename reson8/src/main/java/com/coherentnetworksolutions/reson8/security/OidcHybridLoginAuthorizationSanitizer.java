package com.coherentnetworksolutions.reson8.security;

import java.util.Set;
import java.util.logging.Logger;

import io.quarkus.vertx.web.Route;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * OIDC {@code hybrid}: {@link io.quarkus.oidc.runtime.OidcAuthenticationMechanism} chooses authorization-code vs Bearer
 * solely from {@code Authorization == null}. Any header value (including blank strings injected by proxies) forces the
 * Bearer path; browsers hitting {@code GET /login} then get {@code 403 Forbidden}. Strip before authentication runs.
 * <p>
 * Implemented as a reactive route so it attaches to Quarkus's HTTP {@link io.vertx.ext.web.Router} (unlike ad hoc
 * {@code Observes StartupEvent} handlers, which may target the wrong router instance in some setups).
 */
@ApplicationScoped
public class OidcHybridLoginAuthorizationSanitizer {

    private static final Logger LOG = Logger.getLogger(OidcHybridLoginAuthorizationSanitizer.class.getName());

    /** Run ahead of security so OIDC sees no {@code Authorization} header on {@code GET /login}. */
    private static final int ORDER_BEFORE_SECURITY_CHAIN = Integer.MIN_VALUE;

    private static final Set<String> DIAGNOSTIC_HEADERS = Set.of(
            "authorization", "cookie", "x-forwarded-proto", "x-forwarded-host",
            "x-forwarded-for", "forwarded", "origin", "referer");

    @Route(path = "/login", methods = Route.HttpMethod.GET, order = ORDER_BEFORE_SECURITY_CHAIN)
    void stripAuthorizationForLogin(RoutingContext rc) {
        String remoteAddr = rc.request().remoteAddress() != null
                ? rc.request().remoteAddress().toString() : "unknown";

        StringBuilder diag = new StringBuilder();
        diag.append("PRE-SECURITY GET /login from=").append(remoteAddr)
                .append(" scheme=").append(rc.request().scheme())
                .append(" host=").append(rc.request().getHeader("Host"))
                .append(" uri=").append(rc.request().uri());

        for (String name : DIAGNOSTIC_HEADERS) {
            String value = rc.request().getHeader(name);
            if (value != null) {
                String safe = name.equalsIgnoreCase("authorization")
                        ? value.replaceAll("(?i)(bearer\\s+)\\S+", "$1[redacted]")
                        : value;
                diag.append(" | ").append(name).append("=").append(safe);
            }
        }
        LOG.fine(diag.toString());

        boolean hadAuth = rc.request().getHeader("Authorization") != null;
        rc.request().headers().remove("Authorization");
        if (hadAuth) {
            LOG.info("PRE-SECURITY /login: removed Authorization header — OIDC will use code-flow path");
        }

        rc.next();
    }
}
