package com.coherentnetworksolutions.reson8.audio.providers;

import com.coherentnetworksolutions.reson8.audio.input.GenericNoiseChanel.NoiseType;
import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.NoiseGeneratorFactory;

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@LookupIfProperty(name = "reson8.source.mode", stringValue = "canned")
public class CannedNoiseProvider implements ChannelProvider {
    @Inject 
    NoiseGeneratorFactory factory;

    @Override
    public void populate(Mixer mixer) {
        mixer.addInputChannel(factory.create(NoiseType.SQUARE, "cpu-noise", 0.33));
        mixer.addInputChannel(factory.create(NoiseType.SINE, "mem-noise", 0.33));
    }
}