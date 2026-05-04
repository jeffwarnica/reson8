package com.coherentnetworksolutions.reson8.manager.config.startup;

import java.util.List;

/**
 * One piece of startup-time configuration validation. Register additional checks by adding an
 * {@link jakarta.enterprise.context.ApplicationScoped @ApplicationScoped} implementation; CDI exposes every bean of
 * this type to {@link StartupConfigValidation} via {@link jakarta.enterprise.inject.Instance} — no need to edit that
 * class or use classpath-scanning reflection.
 */
public interface StartupConfigContributor {

    /** Lower runs first (security before signal-map, etc.). */
    default int order() {
        return 100;
    }

    /** Short label for aggregated logs. */
    default String contributorName() {
        return getClass().getSimpleName();
    }

    /** Append zero or more validation messages to {@code errors}; do not throw for individual issues. */
    void collectErrors(List<String> errors);
}
