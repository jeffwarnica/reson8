package com.coherentnetworksolutions.reson8.security;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Shared HTTP responses for tier authorization failures. */
public final class TierAuthorizationResponses {

    private TierAuthorizationResponses() {}

    public static Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"forbidden\"}")
                .build();
    }
}
