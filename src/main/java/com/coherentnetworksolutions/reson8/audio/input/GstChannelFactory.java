package com.coherentnetworksolutions.reson8.audio.input;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.providers.GstToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.SoundDefinitionRegistry;
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
    @Inject SoundDefinitionRegistry soundRegistry;
    @Inject Reson8Config config;
    @Inject Mixer mixer;
    @Inject GstToolkit gstToolkit;
    
    @Override
    public InputChannel buildChannel(SignalBucket signalBucket) {
        Log.debugf("buildChannel([%s])", signalBucket.getName());
               
        SoundType type = signalBucket.getSoundType();

        return switch (type) {
            case LOOP -> {
                // var config = soundDef.loop().orElseThrow();
                yield new LoopingGaugeChannel(signalBucket, config, gstToolkit);
            }
            case PROCEDURAL -> {
                // var proc = soundDef.procedural().orElseThrow();
                yield createProcedural(signalBucket); //channelName, proc.className(), proc.params());
            }
            case STOCHASTIC -> {
                yield new StochasticGaugeChannel(signalBucket, config, wavCache, gstToolkit);
                // throw new UnsupportedOperationException("Stochastic sound type is not yet implemented");
            }
            case DROP -> {
                // var drop = soundDef.drop().orElseThrow();
                // yield dropFactory.createDropDefinition(signalEndpoint);
                String filename = signalBucket.getSoundDefinition()
                        .drop()
                        .orElseThrow()
                        .filename();
                try {
                    WavCache.CachedWav soundData = wavCache.getOrLoad(filename);
                    yield new GstDropChannel(signalBucket, soundData, gstToolkit);
                } catch (RuntimeException e) {
                    Log.errorf("Channel [%s] failed: %s. Falling back to Silent.", signalBucket.getName(),
                        e.getMessage());
                    yield new SilentInputChannel(signalBucket, gstToolkit);
                }
            }
            default -> throw new IllegalArgumentException("Type " + type + " is unknown");
        };
    }

    private GaugeChannel createProcedural(SignalBucket signalBucket) { 
        ProceduralConfig procedureConfig = signalBucket.getProcedureConf();
                
        return switch (procedureConfig.className()) {
            case "WindGaugeChannel" -> new WindGaugeChannel(signalBucket, gstToolkit);
            // case "FireGaugeChannel" -> ...
            default -> throw new UnsupportedOperationException("Unknown procedural: " + procedureConfig.className());
        };
    }

}