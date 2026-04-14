package com.coherentnetworksolutions.reson8.manager;
import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.factories.ChannelFactory;
import com.coherentnetworksolutions.reson8.audio.factories.DropFactory;
import com.coherentnetworksolutions.reson8.audio.input.OutputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.output.OutputChannel;
import com.coherentnetworksolutions.reson8.audio.sound.SoundManager;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.DummySignalProvider;
import com.coherentnetworksolutions.reson8.signal.MappingManager;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class Reson8Orchestrator {

    @Inject
    Reson8Config config;
    @Inject
    ChannelFactory channelFactory;
    @Inject
    DropFactory dropFactory;
    @Inject
    Mixer mixer;
    @Inject
    OutputChannelFactory outputChannelFactory;
    @Inject SoundManager soundRegistry;
    @Inject MappingManager mappingManager;
    @Inject DummySignalProvider dummySignalProvider;


    void onStart(@Observes StartupEvent ev) {
        Log.info("Reson8 Engine Starting...");

        mixer.initGStreamer();
        mixer.setupDebugStuff();
        
        OutputChannel browserOutput = outputChannelFactory.create("browser", "browser-out",1.0);
        mixer.addOutputChannel(browserOutput);        

        // processSoundscapes();
        soundRegistry.onStart();
        mappingManager.onStart();

        Log.debug("Mapping registry: " + mappingManager);
        Log.debug("SoundRegistry: " + soundRegistry.toString());
        
        // initializeChannels();

        
        mixer.start();
        mixer.setupDebugStuff();
        
        mixer.dumpMixerState();

        dummySignalProvider.driveDummies();

        mixer.dumpMixerState();

        Log.info("Orchestrator: Audio Pipeline is now LIVE.");

        Log.info("Reson8 Soundscape is LIVE");
    }

    

}

