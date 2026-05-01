package com.coherentnetworksolutions.reson8;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class GsTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("reson8dev.audiopath", "gs");
    }
    
}
