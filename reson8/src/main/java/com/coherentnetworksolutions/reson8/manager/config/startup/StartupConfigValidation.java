package com.coherentnetworksolutions.reson8.manager.config.startup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

/**
 * Runs every {@link StartupConfigContributor} before the rest of the application wires consumers of
 * {@link com.coherentnetworksolutions.reson8.manager.config.Reson8Config}. Contributors are discovered via CDI
 * {@link Instance} — add a new {@link ApplicationScoped} {@link StartupConfigContributor} rather than changing this
 * class.
 */
@Startup
@ApplicationScoped
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class StartupConfigValidation {

    private final Instance<StartupConfigContributor> contributors;

    @Inject
    StartupConfigValidation(Instance<StartupConfigContributor> contributors) {
        this.contributors = contributors;
    }

    @PostConstruct
    void runAllContributors() {
        List<StartupConfigContributor> sorted = contributors.stream()
                .sorted(Comparator.comparingInt(StartupConfigContributor::order)
                        .thenComparing(c -> c.contributorName()))
                .toList();

        Log.infof("Startup configuration validation starting (%d contributor(s))", sorted.size());

        List<String> errors = new ArrayList<>();
        for (StartupConfigContributor contributor : sorted) {
            Log.debugf("Startup configuration validation: contributor [%s] (order=%d)",
                    contributor.contributorName(), contributor.order());
            int before = errors.size();
            contributor.collectErrors(errors);
            int added = errors.size() - before;
            if (added > 0) {
                Log.warnf("Startup configuration validation: contributor [%s] reported %d issue(s)",
                        contributor.contributorName(), added);
            }
        }

        if (!errors.isEmpty()) {
            String message = "Startup configuration validation failed:\n  - " + String.join("\n  - ", errors);
            Log.error(message);
            throw new IllegalStateException(message);
        }

        Log.info("Startup configuration validation passed.");
    }
}
