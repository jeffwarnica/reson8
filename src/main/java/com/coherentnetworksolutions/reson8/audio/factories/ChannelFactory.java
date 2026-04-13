package com.coherentnetworksolutions.reson8.audio.factories;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.input.LoopingGaugeChannel;
import com.coherentnetworksolutions.reson8.audio.input.GaugeChannel;
import com.coherentnetworksolutions.reson8.audio.input.WindGaugeChannel;
import com.coherentnetworksolutions.reson8.audio.sound.SoundManager;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundType;
import com.coherentnetworksolutions.reson8.signal.SignalEndpoint;

import io.quarkus.logging.Log;

/*
* Channel Factory is responsible for creating the actual InputChannels
*/
@ApplicationScoped
public class ChannelFactory {
    @Inject DropFactory dropFactory;
    @Inject SoundManager soundRegistry;
    @Inject Reson8Config config;
    
    public InputChannel buildChannel(SignalEndpoint signalEndpoint) {
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
                yield dropFactory.createDropDefinition(signalEndpoint);
            }
            default -> throw new IllegalArgumentException("Type " + type + " is unknown");
        };
    }

    private GaugeChannel createProcedural(SignalEndpoint signalEndpoint) { 
        ProceduralConfig procedureConfig = signalEndpoint.getProcedureConf();
                
        return switch (procedureConfig.className()) {
            case "WindGaugeChannel" -> new WindGaugeChannel(signalEndpoint);
            // case "FireGaugeChannel" -> ...
            default -> throw new UnsupportedOperationException("Unknown procedural: " + procedureConfig.className());
        };
    }

}