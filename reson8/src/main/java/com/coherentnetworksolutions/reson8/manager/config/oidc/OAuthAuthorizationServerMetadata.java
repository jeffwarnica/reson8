package com.coherentnetworksolutions.reson8.manager.config.oidc;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Parses RFC 8414-style OAuth authorization server metadata (subset: {@code issuer} only). */
public final class OAuthAuthorizationServerMetadata {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OAuthAuthorizationServerMetadata() {}

    public static String requireIssuer(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode issuer = root.get("issuer");
        if (issuer == null || issuer.isNull()) {
            throw new IOException("metadata JSON missing \"issuer\" field");
        }
        String url = issuer.asText().trim();
        if (url.isEmpty()) {
            throw new IOException("metadata JSON has empty \"issuer\"");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
