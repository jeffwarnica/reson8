package com.coherentnetworksolutions.reson8.manager.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time audio-path selector.
 * <p>
 * The {@code reson8dev.audiopath} value is read at build time by
 * {@code @IfBuildProperty} to activate either the native GStreamer stack
 * ({@code gs}) or the silent no-op stub ({@code silent}).  Declaring it here
 * as a {@code @ConfigMapping} gives IDE tooling a formal binding so it does
 * not report the property as unreferenced.
 */
@ConfigMapping(prefix = "reson8dev")
public interface Reson8DevConfig {

    /** Selects the audio backend: {@code gs} (native GStreamer) or {@code silent}. */
    @WithDefault("silent")
    String audiopath();
}
