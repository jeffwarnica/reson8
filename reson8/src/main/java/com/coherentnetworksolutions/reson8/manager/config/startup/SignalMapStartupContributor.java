package com.coherentnetworksolutions.reson8.manager.config.startup;

import java.util.List;

import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.SignalMapConfigValidator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SignalMapStartupContributor implements StartupConfigContributor {

    @Inject
    Reson8Config config;

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void collectErrors(List<String> errors) {
        SignalMapConfigValidator.collectErrors(config, errors);
    }
}
