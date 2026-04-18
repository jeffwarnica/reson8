package com.coherentnetworksolutions.reson8.audio.input;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.sound.SoundRegistry;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundType;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/*
* Channel Factory is responsible for creating the actual InputChannels
*/
@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "reson8dev.audiopath", stringValue = "gst")
public class GstChannelFactory implements InputChannelFactory {
    @Inject WavCache wavCache;
    @Inject SoundRegistry soundRegistry;
    @Inject Reson8Config config;
    @Inject Mixer mixer;
    
    @Override
    public InputChannel buildChannel(SignalBucket signalEndpoint) {
        Log.debugf("buildChannel([%s])", signalEndpoint.getName());
               
        SoundType type = signalEndpoint.getSoundType();

        return switch (type) {
            case LOOP -> {
                // var config = soundDef.loop().orElseThrow();
                yield new LoopingGaugeChannel(signalEndpoint, config);
            }
            case PROCEDURAL -> {
                // var proc = soundDef.procedural().orElseThrow();
                yield createProcedural(signalEndpoint); //channelName, proc.className(), proc.params());
            }
            case DROP -> {
                // var drop = soundDef.drop().orElseThrow();
                // yield dropFactory.createDropDefinition(signalEndpoint);
                String filename = signalEndpoint.getSoundDefinition()
                        .drop()
                        .orElseThrow()
                        .filename();
                try {
                    WavCache.CachedWav soundData = wavCache.getOrLoad(filename);
                    yield new GstDropChannel(signalEndpoint, soundData);
                } catch (RuntimeException e) {
                    Log.warnf("Channel [%s] borked because audio file was invalid", signalEndpoint.getName());
                    //todo: implement NoOpInputChannel
                    throw new RuntimeException(e);
                }
            }
            default -> throw new IllegalArgumentException("Type " + type + " is unknown");
        };
    }

    private GaugeChannel createProcedural(SignalBucket signalEndpoint) { 
        ProceduralConfig procedureConfig = signalEndpoint.getProcedureConf();
                
        return switch (procedureConfig.className()) {
            case "WindGaugeChannel" -> new WindGaugeChannel(signalEndpoint);
            // case "FireGaugeChannel" -> ...
            default -> throw new UnsupportedOperationException("Unknown procedural: " + procedureConfig.className());
        };
    }

}