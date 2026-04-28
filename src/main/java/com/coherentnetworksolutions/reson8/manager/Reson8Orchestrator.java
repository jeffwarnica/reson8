package com.coherentnetworksolutions.reson8.manager;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.output.from.ClientChannelFactory;
import com.coherentnetworksolutions.reson8.audio.output.from.MixerOutputToClientManagerChannel;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache;
import com.coherentnetworksolutions.reson8.k8s.client.K8Client;
import com.coherentnetworksolutions.reson8.audio.sound.SoundDefinitionRegistry;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalManager;
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
    @Inject SoundDefinitionRegistry soundRegistry;
    @Inject SignalManager signalManager;
    @Inject K8Client k8sclient;

    void onStart(@Observes StartupEvent ev) {
        Log.info("Reson8 Engine Starting...");
        
        MixerOutputToClientManagerChannel browserOutput = outputChannelFactory.create("browser", "browser-out",1.0);
        mixer.addOutputChannel(browserOutput);


        mixer.start();
        
        Log.info("Orchestrator: Audio Pipeline is now LIVE.");

        Log.info("Reson8 Soundscape is LIVE");
    }

    

}

