package com.coherentnetworksolutions.reson8.signal;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.input.InputChannel;

import java.util.List;

import com.coherentnetworksolutions.reson8.audio.input.DropChannel;
import com.coherentnetworksolutions.reson8.audio.sound.SoundRegistry;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
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
    private final JsonPath jsonPath;
    
    // We can even store the resolved SoundDefinition here later
    private Reson8Config.SoundDefinition soundDefinition;
    private SoundType soundType;
    private boolean isDrop;

    // @Inject
    InputChannelFactory channelFactory;

    // @Inject
    SoundRegistry soundRegistry;

    SignalManager signalManager;

    private static final Configuration JSON_NODE_CONF = Configuration.builder()
        .jsonProvider(new JacksonJsonNodeJsonProvider())
        .options(Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS)
        .build();

    public SignalBucket(Reson8Config.InputMapping mapping, SoundDefinition soundDefinition, 
            InputChannelFactory channelFactory, SoundRegistry soundRegistry, SignalManager signalManager) {
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

            this.jsonPath = JsonPath.compile(query);
            Log.infof("Compiling jsonPath for Bucket [%s]: %s", this.name, this.jsonPath);
            
    }

    public void processEvent(io.fabric8.kubernetes.api.model.events.v1.Event event) {
        Log.debugf("Signal Bucket [%s] checking event [%s]", name, event);

        JsonNode eventNode = Serialization.jsonMapper().valueToTree(event);
        Log.debugf("Event as JsonNode: [%s]", eventNode);

        Object matches = jsonPath.read(eventNode, JSON_NODE_CONF);
        Log.debugf("Signal Bucket [%s] got matches [%s] for event [%s] against query [%s]", name, matches, event, query);
        
        if (!matches.toString().equals("[]")) {
            trigger();
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
            ((DropChannel) inputChannel).trigger(inputChannel.getGain());
        }
    }
    
    public void setIntensity(@Min(0) @Max(100) double intensity){
        if (signalManager.isK8sSyncEnabled()) {
            Log.debugf("about to setIntensity([%s]) from [%s]", intensity, name);
            inputChannel.setIntensity(intensity);
        }
    }

    /**
     * 
     * @return "gain", the output volume of the channel
     */
    public double getVolume() {
        return inputChannel.getGain();
    }

    public void setVolume(@Min(0) @Max(100) double vol){
        Log.debugf("[%s].setVolume([%s]), pass to inputChanel", name, vol);
        inputChannel.setGain(vol);
    }


    public double getIntensity() {
        return inputChannel.getIntensity();
    }

    public boolean isDrop() {
        return isDrop;
    }


}
