package com.coherentnetworksolutions.reson8.signal;

import com.coherentnetworksolutions.reson8.audio.engine.Mixer;
import com.coherentnetworksolutions.reson8.audio.factories.ChannelFactory;
import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.input.DropChannel;
import com.coherentnetworksolutions.reson8.audio.sound.SoundManager;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ProceduralConfig;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SoundType;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.SourceType;

import io.quarkus.logging.Log;


public class SignalEndpoint {
    private final String name;
    private final String fullSoundPath; // The "Calculated" path
    private final SourceType sourceType;
    private final String query;
    private final InputChannel inputChannel;
    // We can even store the resolved SoundDefinition here later
    private Reson8Config.SoundDefinition soundDefinition;
    private SoundType soundType;
    private Mixer mixer;

    public SignalEndpoint(Reson8Config.InputMapping mapping, String defaultScape, ChannelFactory channelFactory, SoundManager soundManager, Mixer mixer) {
        this.name = mapping.name();
        this.sourceType = mapping.type().orElse(SourceType.PROMETHEUS);
        this.query = mapping.query().orElse("");
        this.mixer = mixer;
        
        // Logic: Calculate the full path once and store it
        String rawSound = mapping.sound();
        fullSoundPath = rawSound.contains("/") ? rawSound : defaultScape + "/" + rawSound;
        soundDefinition = soundManager.get(fullSoundPath);
        this.soundType = soundDefinition.type();

        inputChannel = channelFactory.buildChannel(this);
    }

    // Getters...
    public String getFullSoundPath() { return fullSoundPath; }

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
