package com.coherentnetworksolutions.reson8.rest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.qute.Template;

/**
 * Serves the SPA shell from Qute so dev-only UI (tier selector) is omitted at
 * render time when disabled.
 */
@ApplicationScoped
@Path("/")
public class HomeResource {

    @Inject
    Template index;

    @Inject
    Reson8Config config;

    @Inject
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String applicationVersion;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String home() {
        String spaAssetQuery = "?v=" + URLEncoder.encode(applicationVersion, StandardCharsets.UTF_8);
        return index.data("showDevToolbar", config.security().devTierCookieEnabled())
                .data("spaAssetQuery", spaAssetQuery)
                .render();
    }
}
