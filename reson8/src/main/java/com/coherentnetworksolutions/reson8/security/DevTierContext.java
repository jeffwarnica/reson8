package com.coherentnetworksolutions.reson8.security;

import java.util.Optional;

import jakarta.enterprise.context.RequestScoped;

/**
 * Optional dev-tier override for the current request, populated when
 * {@code reson8.security.dev-tier-cookie-enabled} is true and cookie
 * {@code reson8-dev-tier} is set.
 */
@RequestScoped
public class DevTierContext {

    private DevTierSelection selection;

    public void setSelection(DevTierSelection selection) {
        this.selection = selection;
    }

    public Optional<DevTierSelection> selection() {
        return Optional.ofNullable(selection);
    }
}
