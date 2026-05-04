package com.coherentnetworksolutions.reson8.security;

/**
 * JSON payload for {@code GET /api/capabilities}.
 */
public record CapabilitiesResponse(
        AccessTier tier,
        boolean canStream,
        boolean canViewControlState,
        boolean canMutate,
        boolean canUseDebug,
        boolean showDevRoleSelector,
        boolean devUi,
        boolean loginAvailable) {
}
