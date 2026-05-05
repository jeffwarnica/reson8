package com.coherentnetworksolutions.reson8.manager.config.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuthAuthorizationServerMetadataTest {

    @Test
    @DisplayName("parses issuer and trims trailing slash")
    void parsesIssuer() throws Exception {
        String json = "{\"issuer\":\"https://oauth.example.com/realms/demo/\",\"token_endpoint\":\"x\"}";
        assertEquals("https://oauth.example.com/realms/demo", OAuthAuthorizationServerMetadata.requireIssuer(json));
    }

    @Test
    @DisplayName("missing issuer fails")
    void missingIssuer() {
        assertThrows(Exception.class, () -> OAuthAuthorizationServerMetadata.requireIssuer("{}"));
    }
}
