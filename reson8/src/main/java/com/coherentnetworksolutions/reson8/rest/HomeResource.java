package com.coherentnetworksolutions.reson8.rest;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.qute.Template;

/**
 * Serves the SPA shell from Qute so dev-only UI (tier selector) is omitted at render time when disabled.
 */
@ApplicationScoped
@Path("/")
public class HomeResource {

    @Inject
    Template index;

    @Inject
    Reson8Config config;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String home() {
        return index.data("showDevToolbar", config.security().devTierHeaderEnabled()).render();
    }
}
