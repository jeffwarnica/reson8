package com.coherentnetworksolutions.reson8.audio.input;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.freedesktop.gstreamer.Caps;
import org.junit.jupiter.api.Test;

import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(com.coherentnetworksolutions.reson8.GstTestProfile.class)
class WindGaugeChannelIT {

    @Inject
    GsToolkit toolkit; // NativeGsToolkit in this profile

    @Test
    void getCapsReturnsNonNullFixedCaps() {
        WindGaugeChannel ch = new WindGaugeChannel(buildBucket(), toolkit);
        Caps caps = ch.getCaps();
        assertNotNull(caps);
        assertTrue(caps.toString().contains("audio/x-raw"));
        ch.dispose();
    }

    // -----------------------------------------------------------------------

    private SignalBucket buildBucket() {
        var proc = mock(Reson8Config.ProceduralConfig.class);
        when(proc.intensity()).thenReturn(50.0);
        when(proc.smoothingrate()).thenReturn(0.8);
        when(proc.outputScale()).thenReturn(1.0);

        var def = mock(Reson8Config.SoundDefinition.class);
        when(def.procedural()).thenReturn(Optional.of(proc));

        var bucket = mock(SignalBucket.class);
        when(bucket.getName()).thenReturn("IntegrationWind");
        when(bucket.getProcedureConf()).thenReturn(proc);
        when(bucket.getSoundDefinition()).thenReturn(def);

        return bucket;
    }
}
