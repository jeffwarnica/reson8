package com.coherentnetworksolutions.reson8.signal;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.input.DropChannel;
import com.coherentnetworksolutions.reson8.audio.sound.SoundRegistry;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundDefinition;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundType;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;

public class SignalEndpoint {
    private final String name;
    private final SourceType sourceType;
    private final String query;
    private final InputChannel inputChannel;
    // We can even store the resolved SoundDefinition here later
    private Reson8Config.SoundDefinition soundDefinition;
    private SoundType soundType;

    // @Inject
    InputChannelFactory channelFactory;

    // @Inject
    SoundRegistry soundRegistry;

    public SignalEndpoint(Reson8Config.InputMapping mapping, SoundDefinition soundDefinition, 
            InputChannelFactory channelFactory, SoundRegistry soundRegistry) {
        this.name = mapping.name();
        this.sourceType = mapping.type().orElse(SourceType.PROMETHEUS);
        this.query = mapping.query().orElse("");
        this.soundDefinition = soundDefinition;
        this.channelFactory = channelFactory;
        this.soundRegistry = soundRegistry;

        this.soundType = soundDefinition.type();

        inputChannel = channelFactory.buildChannel(this);
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

    /**
     * 
     * @return "gain", the output volume of the channel
     */
    public double getVolume() {
        return inputChannel.getGain();
    }

    public void setVolume(double vol){
        Log.debugf("[%s].setVolume([%s]), pass to inputChanel", name, vol);
        inputChannel.setGain(vol);
    }


    public double getIntensity() {
        return inputChannel.getIntensity();
    }

    public void setIntensity(double intensity) {
        inputChannel.setIntensity(intensity);
    }
}
