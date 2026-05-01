package com.coherentnetworksolutions.reson8;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * Automated enforcement of the architectural policies documented in .cursor/rules/.
 *
 * Each rule below corresponds to a specific clause in either
 * gstreamer-access.mdc or volume-intensity-ranges.mdc. If one of these tests
 * fails it means production code has drifted from the policy; the fix is to
 * correct the production code, not to weaken the rule.
 *
 * Scope: production classes only (tests excluded via DoNotIncludeTests).
 */
@AnalyzeClasses(
    packages = "com.coherentnetworksolutions.reson8",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    private static final String PROVIDERS = "com.coherentnetworksolutions.reson8.audio.providers";
    private static final String MIXER     = "com.coherentnetworksolutions.reson8.audio.mixer";

    // -----------------------------------------------------------------------
    // gstreamer-access.mdc — Rule 1
    // "Direct use of the native GStreamer API in production code (outside the
    //  Mixer) is a bug."
    //
    // ElementFactory.make() is the primary bypass vector: any class that calls
    // it directly is constructing GStreamer elements without going through
    // GsToolkit. Only NativeGsToolkit and GstMixer are permitted.
    // -----------------------------------------------------------------------
    @ArchTest
    static final ArchRule gst_element_factory_only_in_providers_and_mixer =
        noClasses()
            .that().resideOutsideOfPackages(PROVIDERS, MIXER)
            .should().accessClassesThat()
                .haveFullyQualifiedName("org.freedesktop.gstreamer.ElementFactory")
            .as("ElementFactory.make() must only be called from NativeGsToolkit "
                + "(audio.providers) or GstMixer (audio.mixer)");

    // -----------------------------------------------------------------------
    // gstreamer-access.mdc — Rule 2
    // "Gst.init() is called exclusively inside GstMixer.@PostConstruct"
    //
    // The Gst class controls the native GStreamer lifecycle. Calling Gst.init()
    // from any class other than GstMixer changes startup order and breaks
    // plugin loading.
    // -----------------------------------------------------------------------
    @ArchTest
    static final ArchRule gst_lifecycle_only_in_mixer =
        noClasses()
            .that().doNotHaveFullyQualifiedName(
                "com.coherentnetworksolutions.reson8.audio.mixer.GstMixer")
            .should().accessClassesThat()
                .haveFullyQualifiedName("org.freedesktop.gstreamer.Gst")
            .as("org.freedesktop.gstreamer.Gst must only be accessed from GstMixer — "
                + "Gst.init() must run in @PostConstruct before Reson8Orchestrator.onStart()");

    // -----------------------------------------------------------------------
    // gstreamer-access.mdc — Rule 3
    // "GstMixer must remain @Singleton. Changing to @ApplicationScoped defers
    //  this init and breaks GStreamer plugin loading."
    // -----------------------------------------------------------------------
    @ArchTest
    static final ArchRule gst_mixer_must_be_singleton =
        classes()
            .that().haveFullyQualifiedName(
                "com.coherentnetworksolutions.reson8.audio.mixer.GstMixer")
            .should().beAnnotatedWith("jakarta.inject.Singleton")
            .as("GstMixer must be @Singleton — its @PostConstruct initialises GStreamer "
                + "eagerly; @ApplicationScoped would defer it and break plugin loading");

    // -----------------------------------------------------------------------
    // gstreamer-access.mdc — Rule 4
    // "WavCache.toolkit must be an instance field (not static).
    //  CDI does not inject static fields."
    // -----------------------------------------------------------------------
    @ArchTest
    static final ArchRule wav_cache_toolkit_must_be_instance_field =
        fields()
            .that().areDeclaredInClassesThat()
                .haveFullyQualifiedName(
                    "com.coherentnetworksolutions.reson8.audio.sound.WavCache")
            .and().haveName("toolkit")
            .should().notBeStatic()
            .as("WavCache.toolkit must be an instance field — CDI does not inject static fields");

    // -----------------------------------------------------------------------
    // Layering — REST must not depend on the GstMixer implementation
    //
    // REST resources must inject the Mixer interface, not GstMixer directly.
    // Bypassing the interface couples the REST layer to the GStreamer
    // implementation, preventing testing with SilentMixer.
    // -----------------------------------------------------------------------
    @ArchTest
    static final ArchRule rest_must_not_depend_on_gst_mixer =
        noClasses()
            .that().resideInAPackage("com.coherentnetworksolutions.reson8.rest..")
            .should().accessClassesThat()
                .haveFullyQualifiedName(
                    "com.coherentnetworksolutions.reson8.audio.mixer.GstMixer")
            .as("REST layer must depend on the Mixer interface, not the GstMixer "
                + "implementation — GstMixer is an audio-layer concern");

    // -----------------------------------------------------------------------
    // Layering — signal package must not depend on rest package
    //
    // Dependency must flow only downward: REST → signal → audio.
    // If signal imported rest, it would create a circular dependency and
    // couple business logic to the HTTP transport layer.
    // -----------------------------------------------------------------------
    @ArchTest
    static final ArchRule signal_must_not_depend_on_rest =
        noClasses()
            .that().resideInAPackage("com.coherentnetworksolutions.reson8.signal..")
            .should().accessClassesThat()
                .resideInAPackage("com.coherentnetworksolutions.reson8.rest..")
            .as("Signal layer must not depend on REST — dependency must flow "
                + "downward: REST → signal → audio");

    // -----------------------------------------------------------------------
    // volume-intensity-ranges.mdc — VolumeScaler is the only crossing point
    //
    // Classes outside audio.utils.map must not call VolumeScaler directly on
    // the wrong domain. Enforcing that only audio.* and signal.* may even
    // import VolumeScaler keeps the crossing-point narrow and auditable.
    // -----------------------------------------------------------------------
    @ArchTest
    static final ArchRule volume_scaler_only_used_in_audio_and_signal =
        noClasses()
            .that().resideOutsideOfPackages(
                "com.coherentnetworksolutions.reson8.audio..",
                "com.coherentnetworksolutions.reson8.signal..")
            .should().accessClassesThat()
                .haveFullyQualifiedName(
                    "com.coherentnetworksolutions.reson8.audio.utils.map.VolumeScaler")
            .as("VolumeScaler (human↔GStreamer scale crossing) must only be used "
                + "within the audio or signal layers");
}
