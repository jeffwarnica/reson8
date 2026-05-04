package com.coherentnetworksolutions.reson8.security;

/**
 * Resolved SPA access tier from JWT groups and/or anonymous sentinel configuration.
 */
public enum AccessTier {
    ADMIN,
    VIEWER,
    STREAM,
    NONE
}
