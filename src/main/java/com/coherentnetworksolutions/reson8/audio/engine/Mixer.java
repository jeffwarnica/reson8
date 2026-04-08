package com.coherentnetworksolutions.reson8.audio.engine;

import org.eclipse.microprofile.config.ConfigProvider;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.lowlevel.GValueAPI.GValueArray;
import org.freedesktop.gstreamer.message.MessageType;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.output.OutputChannel;
import com.coherentnetworksolutions.reson8.audio.utils.VolumeScaler;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class Mixer {
    public final static String CAPS="audio/x-raw,format=S16LE,layout=interleaved,channels=2,rate=48000"; //,channel-mask=0x3";

    private volatile boolean initialized = false;

    private Element mixerElement; // The GStreamer mixer element (e.g., 'audiomixer')
    private Pipeline pipeline; // The GStreamer pipeline containing the entire audio flow
    private final Map<String, InputChannel> inputChannels = new HashMap<>(); 
    private final Map<String, OutputChannel> outputChannels = new HashMap<>();     
    private final Map<String, Pad> channelPads = new HashMap<>();
    private final Map<String, Element> gainElements = new HashMap<>();

    private OutputChannel masterOutput;
    private Element masterVolumeElement;
    private Element masterTee;
    private Element levelProbe;

    @Inject
    VolumeScaler volumeScaler;    

    public void initGStreamer() {
        if (!Gst.isInitialized()) {
            Gst.init("Reson8Engine");
        } else {
            Log.info("Gst was already init'd?????");
        }
        this.pipeline = new Pipeline("audioPipeline");
        this.mixerElement = ElementFactory.make("audiomixer", "main_mixer");
        
        mixerElement.set("start-time-selection", 0); // 0 = 'first' 
        mixerElement.set("min-upstream-latency", 0);
        mixerElement.set("latency", 200000000L); // 200ms

        // Safety converters for the master output
        Element outConv = ElementFactory.make("audioconvert", "master_conv");
        Element outRes = ElementFactory.make("audioresample", "master_resample");
        Element outVol = ElementFactory.make("volume", "master_volume");
        this.masterVolumeElement = outVol;
        outVol.set("volume", 1.0f);
        

        setupDropCleanup();

        // The "Splitter" - every output channel will connect to this
        this.masterTee = ElementFactory.make("tee", "master_tee");
        
        // The Level Probe (The Master VU Meter)
        this.levelProbe = ElementFactory.make("level", "master_level_probe");
        levelProbe.set("post-messages", true);
        levelProbe.set("message", true);
        levelProbe.set("interval", 1000000000L);  //1 second?

        pipeline.addMany(mixerElement, outConv, outRes, masterTee, levelProbe, outVol);

        // Link: Mixer -> Convert -> Resample -> Tee
        mixerElement.link(outConv);
        outConv.link(outRes);
        outRes.link(outVol);
        outVol.link(masterTee);
        
        // Attach the level probe to one branch of the Tee permanently
        // This ensures there is ALWAYS a consumer for the mixer data
        Pad teeLevelPad = masterTee.getRequestPad("src_%u");
        teeLevelPad.link(levelProbe.getStaticPad("sink"));
        
        // Add a fakesink after the level probe to "drain" the data
        Element fakeSink = ElementFactory.make("fakesink", "level_drain");
        fakeSink.set("sync", false);
        fakeSink.set("async", false);
        pipeline.add(fakeSink);
        levelProbe.link(fakeSink);

        // This tells the mixer: "Don't wait for all pads to have data before starting"
        mixerElement.set("start-time-selection", 0);
        pipeline.setState(State.PLAYING);

        this.initialized = true;
        Log.info("Mixer Core is INITIALIZED and READY.");        
    }

    // Start the entire pipeline (play audio)
    public void start() {
        Log.info("Starting pipeline...");
        
        // Set everything to PLAYING
        pipeline.setState(State.PLAYING);

    }

    // Stop the pipeline
    public void stop() {
        pipeline.stop();
    }    

    public boolean isReady() {
       return initialized;
    }
    
    public synchronized void addInputChannel(InputChannel inputChannel) {
        if (inputChannels.containsKey(inputChannel.getChannelName())) return;
        inputChannels.put(inputChannel.getChannelName(), inputChannel);
        Element src = inputChannel.getSrcElement();
        
        // Ensure unique names for internal translators to avoid the "Name not unique" warning
        String prefix = inputChannel.getChannelName() + "::" + System.nanoTime() + "::";

        Element gainElement = ElementFactory.make("volume", prefix + "_gain");
        gainElements.put(inputChannel.getChannelName(), gainElement);
                
        // Create the 'Glue' elements
        Element capsFilter = ElementFactory.make("capsfilter", prefix + "caps");
        capsFilter.setCaps(Caps.fromString(CAPS)); 
        
        Element convert = ElementFactory.make("audioconvert", prefix + "conv");
        Element resample = ElementFactory.make("audioresample", prefix + "res");

        Element inputQueue = ElementFactory.make("queue", prefix + "queue");
        inputQueue.set("max-size-time", 200000000L); // Limit the queue size to keep latency low (200ms)

        pipeline.addMany(src, gainElement, capsFilter, convert, resample, inputQueue);
        
        Pad mixerSinkPad = mixerElement.getRequestPad("sink_%u");
        mixerSinkPad.set("volume", 1.0f);
        channelPads.put(inputChannel.getChannelName(), mixerSinkPad);

        if (inputChannel.supportsGain()) {
            gainElement.set("volume", 1.0f);
            src.link(gainElement);
            gainElement.link(convert);
        } else {
            // Bypass the gain element entirely
            src.link(convert);
        }
        convert.link(resample);
        resample.link(capsFilter);
        capsFilter.link(inputQueue);
        inputQueue.getStaticPad("src").link(mixerSinkPad);
        
        double unscaledVol = inputChannel.getGain();
        double scaledVol = volumeScaler.uiToGstVolume(unscaledVol);
        mixerSinkPad.set("volume", scaledVol);
        gainElement.set("volume", scaledVol);

        mixerElement.set("latency", 200000000L); // 200ms

        src.syncStateWithParent();
        gainElement.syncStateWithParent();
        capsFilter.syncStateWithParent();
        convert.syncStateWithParent();
        resample.syncStateWithParent();
        inputQueue.syncStateWithParent();

        Log.debugf("Connected input channel: [%s]", inputChannel.getChannelName());
    }

    public void addOutputChannel(OutputChannel outputChannel) {
        outputChannels.put(outputChannel.getChannelName(), outputChannel);
        if (masterOutput == null) masterOutput = outputChannel;

        Element sinkElement = outputChannel.getElement(); 
        sinkElement.setState(State.PAUSED);

        String prefix = outputChannel.getChannelName() + "::" + System.nanoTime() + "::";

        Element outConv = ElementFactory.make("audioconvert", prefix + "_outconv");
        Element outRes = ElementFactory.make("audioresample", prefix + "_outres");
        Element outVol = ElementFactory.make("volume", prefix + "_outvol");

        pipeline.addMany(outConv, outRes, outVol, sinkElement);

        Pad teeSrcPad = masterTee.getRequestPad("src_%u");
        teeSrcPad.link(outConv.getStaticPad("sink"));
        
        outConv.link(outRes);
        outRes.link(outVol);
        outVol.link(sinkElement);
        outVol.set("volume", 1.0f);

        // Push it live (Order matters: Sink first, then work backwards to the Tee)
        sinkElement.syncStateWithParent();
        outVol.syncStateWithParent();
        outRes.syncStateWithParent();
        outConv.syncStateWithParent();

        sinkElement.setState(State.PLAYING);
        Log.infof("Output channel [%s] is now live and connected to the master mixer, with gs state [%s",
             outputChannel.getChannelName(), sinkElement.getState());
    }
   

    // Set the master volume for the mixer
    public void setMasterVolume(double uiVolume) {
        double scaledVol = volumeScaler.uiToGstVolume(uiVolume);
        masterVolumeElement.set("volume", scaledVol);
    }

    public void setInputChannelVolume(String channelName, double uiVolume) {
        // 1. Get the pad from our registry, NOT the channel itself
        Pad mixerSinkPad = channelPads.get(channelName);
        
        if (mixerSinkPad != null) {
            double scaledVol = volumeScaler.uiToGstVolume(uiVolume);
            Log.debugf("Setting mixer sink pad volume for [%s] to [%s]", channelName, scaledVol);
            
            // 2. Set the property on the PAD, not the element
            mixerSinkPad.set("volume", scaledVol);
        } else {
            Log.errorf("Could not find mixer sink pad for channel: %s", channelName);
        }
    }

    // Set the volume for a specific output channel
    public void setOutputChannelVolume(String channelName, double uiVolume) {
        OutputChannel channel = outputChannels.get(channelName);
        if (channel != null) {
            Element element = channel.getElement();
            double scaledVol = volumeScaler.uiToGstVolume(uiVolume);
            element.set("volume", scaledVol);
        } else {
            Log.errorf("Error: Output channel %s[] not found", channelName);
        }
    }

    // Method to retrieve an input channel by name
    public InputChannel getInputChannel(String channelName) {
        return inputChannels.get(channelName);
    }

    // Method to retrieve an output channel by name
    public OutputChannel getOutputChannel(String channelName) {
        return outputChannels.get(channelName);
    }

    // Method to retrieve an input channel by name
    public Map<String, InputChannel> getInputChannels() {
        return inputChannels;
    }

    // Method to retrieve an output channel by name
    public Map<String, OutputChannel> getOutputChannels() {
        return outputChannels;
    }

    public void setChannelGain(String channelName, double uiVolume) {
        InputChannel channel = inputChannels.get(channelName);
        if (channel == null) return; // Guard for null

        double scaledVol = volumeScaler.uiToGstVolume(uiVolume);
        if (!channel.supportsGain()) {
            Log.warnf("Source [%s] has no gain support...", channelName);
            return;
        }

        // Direct lookup is 100x faster and timestamp-proof
        Element gainElement = gainElements.get(channelName);
        
        if (gainElement != null) {
            gainElement.set("volume", scaledVol);
            Log.debugf("Gain for source [%s] set to %f", channelName, scaledVol);
        } else {
            Log.warnf("Source [%s] claims to support gain, yet has no 'volume' element", channelName);
        }
    }


    public Element getMixerElement() {
        return mixerElement;
    }
    
    public Pipeline getPipeline() {
        return pipeline;
    }

    public synchronized void removeInputChannel(String channelName) {
        InputChannel channel = inputChannels.remove(channelName);
        Element gainElement = gainElements.remove(channelName);
        Pad mixerSinkPad = channelPads.remove(channelName);

        if (channel != null) {
            channel.getSrcElement().setState(State.NULL);
            if (gainElement != null) {
                gainElement.setState(State.NULL);
            }

            // Find and remove all elements belonging to this channel
            // This handles the conv, res, and queue elements
            pipeline.getElements().stream()
                .filter(e -> e.getName().startsWith(channelName))
                .forEach(e -> {
                    e.setState(State.NULL);
                    pipeline.remove(e);
                    e.dispose();
                });
    
            if (mixerSinkPad != null) {
                mixerSinkPad.setActive(false);
                mixerElement.releaseRequestPad(mixerSinkPad);
            }
            Log.infof("Channel [%s] removed and pads released.", channelName);
        }
            
    }

    public synchronized void removeOutputChannel(String channelName) {
        OutputChannel channel = outputChannels.remove(channelName);
        if (channel == null) return;

        Element sinkElement = channel.getElement();
        if (sinkElement != null) {
            sinkElement.setState(State.NULL);
            
            // Find the ghost pad or tee pad that was feeding this output
            // Usually, we look for the pad on the master_tee linked to this sink
            Element masterTee = pipeline.getElementByName("master_tee");
            if (masterTee != null) {
                Pad srcPad = sinkElement.getStaticPad("sink").getPeer();
                if (srcPad != null) {
                    masterTee.releaseRequestPad(srcPad);
                }
            }

            // The Cleanup Vacuum (using your new :: convention)
            pipeline.getElements().stream()
                .filter(e -> e.getName().startsWith(channelName + "::"))
                .forEach(e -> {
                    e.setState(State.NULL);
                    pipeline.remove(e);
                    e.dispose();
                });
                
            Log.infof("Output channel [%s] decommissioned.", channelName);
        }
    }

    public OutputChannel getMasterOutput() {
        return masterOutput;
    }

    /**
     * Add a bus watcher that looks for our one-shot drops ending, and then removes that channel
     */
    public void setupDropCleanup() {
        Bus bus = pipeline.getBus();
        bus.connect((Bus.MESSAGE) (bus1, message) -> {
            if (message.getType() == MessageType.EOS) {
                String name = message.getSource().getName();
                if (name != null && name.startsWith("drop-")) {
                    Log.infof("EOS detected for %s. Cleaning up...", name);
                    // Run in async to avoid deadlocking the Bus thread
                    CompletableFuture.runAsync(() -> removeInputChannel(name));
                }
            }
        });
    }

    private void setupLevelLogging() {
        Bus bus = pipeline.getBus();

        // Use connect instead of setSyncHandler for standard logging
        bus.connect((Bus.MESSAGE) (bus1, message) -> {
            if (message.getType() == MessageType.ELEMENT) {
                if ("master_level_probe".equals(message.getSource().getName())) {
                    double vu = structToRPS(message.getStructure());
                    Log.infof("Master VU: [%.2f dB]", vu);    
                }
            }
        });
    }


    private double structToRPS(Structure struct){
         // Note: Depending on your version, it might be a double array or GValueArray
        Object rmsObj = struct.getValue("rms");
        if (rmsObj instanceof double[] rms) {
            return rms[0];
        } else if (rmsObj instanceof GValueArray array) {
            return (double) array.getValue(0);
        }  else {
            return 0f;
        }
    }

    private void startPipelineStateWatcher() {
        new Thread(() -> {
            // Element probe = pipeline.getElementByName("master_level_probe");
            while (true) {
                try {
                    Thread.sleep(5000);
                    // Manually query the 'last-sample' or properties if supported, 
                    // but better yet, let's fix the sync handler.
                    Log.infof("Pipeline State: %s", pipeline.getState(0));
                } catch (Exception e) {}
            }
        }).start();
    }

    public void setupDebugStuff() {
        if (isDebugEnabled()) {
            startPipelineStateWatcher();
            setupLevelLogging();
        }
        
    }

    private boolean isDebugEnabled() {
        // Look up the specific config key for your package
        Optional<String> level = ConfigProvider.getConfig()
            .getOptionalValue("quarkus.log.category.\"com.coherentnetworksolutions\".level", String.class);
        
        // If not set specifically, you might want to check the default root level
        if (level.isEmpty()) {
            level = ConfigProvider.getConfig().getOptionalValue("quarkus.log.level", String.class);
        }
        Log.debugf("isDebugEnabled thinks [%s] is our debug level", level);

        return level.map(s -> s.equalsIgnoreCase("DEBUG") || s.equalsIgnoreCase("TRACE"))
                    .orElse(false);
    }


    
}