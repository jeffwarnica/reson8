package com.coherentnetworksolutions.reson8.rest;

import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Browser entry point for OIDC: anonymous {@code GET /login} is intercepted by Quarkus OIDC (hybrid mode) and redirected
 * to the IdP; after return the user lands here authenticated, then we send them back to the SPA shell.
 */
@ApplicationScoped
@Path("/login")
public class OidcLoginGatewayResource {

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public String redirectToSpaAfterAuth() {
        Log.debug("Redirecting to SPA after authentication");
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta http-equiv="refresh" content="0;url=/">
                <title>Returning to Reson8</title>
                </head>
                <body>
                <p>Sign-in complete. <a href="/">Continue to Reson8</a>.</p>
                <script>window.location.replace('/');</script>
                </body>
                </html>
                """;
    }
}
