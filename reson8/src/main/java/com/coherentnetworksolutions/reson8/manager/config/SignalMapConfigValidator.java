package com.coherentnetworksolutions.reson8.manager.config;

import java.util.List;

/**
 * Validates {@link Reson8Config#signalMap()} before signal buckets are constructed.
 */
public final class SignalMapConfigValidator {

    private SignalMapConfigValidator() {}

    public static void collectErrors(Reson8Config config, List<String> errors) {
        if (config.signalMap() == null) {
            errors.add("reson8.signal-map is missing");
            return;
        }
        if (config.signalMap().inputs() == null || config.signalMap().inputs().isEmpty()) {
            errors.add("reson8.signal-map.inputs must contain at least one entry");
        } else {
            config.signalMap().inputs().forEach(mapping -> {
                if (mapping.name() == null || mapping.name().isBlank()) {
                    errors.add("An input mapping is missing a 'name'");
                }
                if (mapping.sound() == null || mapping.sound().isBlank()) {
                    errors.add("Input mapping '" + mapping.name() + "' is missing a 'sound'");
                }
            });
        }
        if (config.signalMap().defaultSoundscape() == null || config.signalMap().defaultSoundscape().isBlank()) {
            errors.add("reson8.signal-map.default-soundscape is missing");
        }
    }
}
