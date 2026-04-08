package com.coherentnetworksolutions.reson8.audio.engine;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;


import com.coherentnetworksolutions.reson8.audio.input.OutputChannelFactory;

import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.coherentnetworksolutions.reson8.audio.output.OutputChannel;
import com.coherentnetworksolutions.reson8.audio.providers.ChannelProvider;
import com.coherentnetworksolutions.reson8.controllers.DropController;

@Singleton
public class Reson8Orchestrator {

    @Inject Mixer mixer;
    @Inject OutputChannelFactory outputFactory;
    @Inject DropController dropController;

    @Inject Instance<ChannelProvider> channelProviders; // Injects all enabled providers
    
    // @Inject
    // NoiseGeneratorFactory noiseGeneratorFactory;

    // @Inject
    // OutputChannelFactory outputChannelFactory;

    @ConfigProperty(name = "reson8.source.mode", defaultValue = "canned")
    String sourceMode;

    public void onStart(@Observes StartupEvent ev) {
        mixer.initGStreamer();
        mixer.setupDebugStuff();
        
        
        // This ensures the pipeline has a sink and can reach the PLAYING state
        OutputChannel browserOutput = outputFactory.create("browser", "browser-out", 1.0);
        mixer.addOutputChannel(browserOutput);


        CompletableFuture.runAsync(() -> {
            try {
                // Initialize the Drop system (File scans, etc.)
                dropController.init(); 
                
                // Populate channels from whichever provider is active (Canned vs K8s)
                for (ChannelProvider provider : channelProviders) {
                    Log.infof("Executing Provider: %s", provider.getClass().getSimpleName());
                    provider.populate(mixer);
                }
                
                // Finally, move the pipeline to PLAYING
                mixer.start();
                mixer.setupDebugStuff();
                Log.info("Orchestrator: Audio Pipeline is now LIVE.");
                
            } catch (Exception e) {
                Log.error("Orchestrator failed during async startup", e);
            }
        });

        // // Input
        // GenericNoiseChanel cpuNoise = (GenericNoiseChanel) noiseGeneratorFactory.create(NoiseType.SQUARE, "cpu-noise", .33);
        // cpuNoise.setFreq(1000f); //Default, but gets rid of unused warning

        // GenericNoiseChanel memNoise = (GenericNoiseChanel) noiseGeneratorFactory.create(NoiseType.SINE, "mem-noise", .33);
        // memNoise.setFreq(1200f);
        
        
    }
    
}