package com.coherentnetworksolutions.reson8.rest;

import com.coherentnetworksolutions.reson8.security.AccessTierResolver;
import com.coherentnetworksolutions.reson8.security.CapabilitiesResponse;
import com.coherentnetworksolutions.reson8.security.DevTierContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.security.identity.SecurityIdentity;

/**
 * Exposes effective SPA capabilities derived from security tier configuration and the current caller.
 */
@ApplicationScoped
@Path("/api/capabilities")
public class CapabilitiesResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    DevTierContext devTierContext;

    @Inject
    AccessTierResolver accessTierResolver;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CapabilitiesResponse get() {
        return accessTierResolver.resolve(identity, devTierContext);
    }
}
