package com.coherentnetworksolutions.reson8.manager;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.output.from.ClientChannelFactory;
import com.coherentnetworksolutions.reson8.audio.output.from.MixerOutputToClientManagerChannel;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache;
import com.coherentnetworksolutions.reson8.k8s.client.k8sclient;
import com.coherentnetworksolutions.reson8.audio.sound.SoundRegistry;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalManager;
import com.coherentnetworksolutions.reson8.signal.SignalMapRegistry;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class Reson8Orchestrator {

    @Inject Reson8Config config;
    @Inject InputChannelFactory channelFactory;
    @Inject WavCache dropFactory;
    @Inject Mixer mixer;
    @Inject ClientChannelFactory outputChannelFactory;
    @Inject SoundRegistry soundRegistry;
    @Inject SignalMapRegistry mappingManager;
    @Inject SignalManager signalManager;
    @Inject k8sclient k8sclient;

    void onStart(@Observes StartupEvent ev) {
        Log.info("Reson8 Engine Starting...");
        // if (LaunchMode.current() == LaunchMode.TEST) {
        //     Log.info("Quarkus makes it necessary to modify code to do useful tests. Not to encourage better code, but explicitly doing different codepaths during testing.");
        //     return;
        // }

        // mixer.initGStreamer();
        // mixer.setupDebugStuff();
        
        MixerOutputToClientManagerChannel browserOutput = outputChannelFactory.create("browser", "browser-out",1.0);
        mixer.addOutputChannel(browserOutput);

        // soundRegistry.onStart();
        // mappingManager.onStart();

        // Log.debug("Mapping registry: " + mappingManager);
        // Log.debug("SoundRegistry: " + soundRegistry.toString());
        
        // initializeChannels();

        mixer.start();
        
        // signalProvider.startChannels();

        Log.info("Orchestrator: Audio Pipeline is now LIVE.");

        Log.info("Reson8 Soundscape is LIVE");
    }

    

}

