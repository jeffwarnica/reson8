package com.coherentnetworksolutions.reson8.signal;

import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.input.InputChannel;

import java.util.List;

import com.coherentnetworksolutions.reson8.audio.input.DropChannel;
import com.coherentnetworksolutions.reson8.audio.sound.SoundDefinitionRegistry;
import com.coherentnetworksolutions.reson8.audio.utils.map.CurveMapFactory;
import com.coherentnetworksolutions.reson8.audio.utils.map.SignalCurveMap;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.CurveConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundDefinition;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundType;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;

import com.fasterxml.jackson.databind.JsonNode;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;

import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.logging.Log;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;



public class SignalBucket {
    private final String name;
    private final SourceType sourceType;
    private final String query;
    private final InputChannel inputChannel;
    private JsonPath jsonPath;
    
    // We can even store the resolved SoundDefinition here later
    private Reson8Config.SoundDefinition soundDefinition;
    private SoundType soundType;
    private boolean isDrop;

    // @Inject
    InputChannelFactory channelFactory;

    // @Inject
    SoundDefinitionRegistry soundRegistry;

    SignalManager signalManager;
    private CurveConfig curveConfig;
    
    private SignalCurveMap curve;

    public SignalBucket(Reson8Config.InputMapping mapping, SoundDefinition soundDefinition, 
                        InputChannelFactory channelFactory, SoundDefinitionRegistry soundRegistry, 
                        SignalManager signalManager, CurveMapFactory curveMapFactory) {
            this.name = mapping.name();
            this.sourceType = mapping.type().orElse(SourceType.PROMETHEUS);
            this.query = mapping.query().orElse("");
            this.soundDefinition = soundDefinition;
            this.channelFactory = channelFactory;
            this.soundRegistry = soundRegistry;
            this.isDrop = (soundDefinition.type() == SoundType.DROP);
            this.soundType = soundDefinition.type();
            this.signalManager = signalManager;
            this.inputChannel = channelFactory.buildChannel(this);
            this.curveConfig = mapping.curve().orElse(null);
            this.curve = curveMapFactory.createCurve(curveConfig);

            if (this.sourceType == SourceType.KUBERNETES_EVENT) {
                try {
                    this.jsonPath = JsonPath.compile(query);
                    Log.infof("Compiled jsonPath for Bucket [%s]: %s", this.name, this.jsonPath);
                } catch (Exception e) {
                    Log.errorf("Failed to compile JsonPath query [%s] for SignalBucket [%s]: %s", query, name, e.getMessage(), e);
                    throw new RuntimeException("Invalid JsonPath query: " + query, e);
                }
            } else if (this.sourceType == SourceType.PROMETHEUS) {
                // query = query
            } else {
                // noop
            }
            
    }

    public Reson8Config.SoundDefinition getSoundDefinition(){
        return soundDefinition;
    }
    public SourceType getSourceType(){ 
        return sourceType;
    }
    public SoundType getSoundType() {
        return soundType;
    }
    public InputChannel getInputChannel() {
        return inputChannel;
    }
    public String getName() {
        return name;
    }

    public ProceduralConfig getProcedureConf() {
        return soundDefinition.procedural().orElseThrow();
    }

    public void trigger() {
        Log.debugf("SignalEndpoint[%s] is type[%s]", name, soundType);
        if (soundType == SoundType.DROP) {
            ((DropChannel) inputChannel).trigger(inputChannel.getCeiling());
        }
    }
    
    public void setIntensity(@Min(0) @Max(100) double intensity){
        if (signalManager.isK8sSyncEnabled()) {
            Log.debugf("about to setIntensity([%s]) from [%s]", intensity, name);
            inputChannel.setTargetIntensity(intensity);
        }
    }

    /**
     * @return the output ceiling of the channel (0–100)
     */
    public double getVolume() {
        return inputChannel.getCeiling();
    }

    @Deprecated
    public void setVolume(@Min(0) @Max(100) double vol){
        Log.debugf("[%s].setVolume([%s]), pass to inputChanel", name, vol);
        inputChannel.setCeiling(vol);
    }


    public double getIntensity() {
        return inputChannel.getTargetIntensity();
    }

    public double getCurrentIntensity() {
        double intensity = inputChannel.getCurrentIntensity();
        Log.tracef("sb: [%s], currentIntensity: [%s]", name , intensity);
        return intensity; // inputChannel.getCurrentIntensity();
    }


    public boolean isDrop() {
        return isDrop;
    }

    public JsonPath JsonPath() {
        return jsonPath;
    }

    public String getQuery() {
        return query;
    }

    public SignalCurveMap getCurve() {
        return curve;
    }


}
