package com.coherentnetworksolutions.reson8.audio.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.ConfigProvider;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.BusSyncReply;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.message.MessageType;

import com.coherentnetworksolutions.reson8.audio.input.InputChannel;
import com.coherentnetworksolutions.reson8.audio.output.OutputChannel;
import com.coherentnetworksolutions.reson8.audio.utils.VolumeScaler;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class Mixer {
    public final static String CAPS="audio/x-raw,format=S16LE,layout=interleaved,channels=2,rate=48000,channel-mask=(bitmask)0x3";

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

    private volatile double lastMasterVu;


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

        // mixerElement.set("start-time-selection", 1); // 1 = "Running Time"

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
        
        // Add a fakeSink after the level probe to "drain" the data
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
        if (inputChannels.containsKey(inputChannel.getChannelName()))
            return;

        inputChannels.put(inputChannel.getChannelName(), inputChannel);
        Element srcBin = inputChannel.getSrcElement();

        String prefix = inputChannel.getChannelName() + "::" + System.nanoTime() + "::";
        Element capsFilter = ElementFactory.make("capsfilter", prefix + "caps");
        capsFilter.setCaps(Caps.fromString(CAPS));
        Element convert = ElementFactory.make("audioconvert", prefix + "conv");
        Element resample = ElementFactory.make("audioresample", prefix + "res");
        Element inputQueue = ElementFactory.make("queue", prefix + "queue");

        pipeline.addMany(srcBin, capsFilter, convert, resample, inputQueue);

        // Link the chain
        srcBin.link(convert);
        convert.link(resample);
        resample.link(capsFilter);
        capsFilter.link(inputQueue);

        // Request the pad from the mixer and link the end of our glue chain
        Pad mixerSinkPad = mixerElement.getRequestPad("sink_%u");
        inputQueue.getStaticPad("src").link(mixerSinkPad);

        // Set the Fader (on the Mixer Pad) to 1.0
        mixerSinkPad.set("volume", 1.0);
        channelPads.put(inputChannel.getChannelName(), mixerSinkPad);

        // SYNC EVERYTHING TO THE RUNNING PIPELINE
        Stream.of(srcBin, capsFilter, convert, resample, inputQueue)
                .forEach(Element::syncStateWithParent);

        // Start the generator (Loop/Drop/Proc)
        inputChannel.start();

        Log.debugf("Channel [%s] is now live and synced.", inputChannel.getChannelName());
    }

    // public synchronized void addInputChannel(InputChannel inputChannel) {
    //     if (inputChannels.containsKey(inputChannel.getChannelName())) return;
    //     Log.debugf("addInputChannel([%s])", inputChannel.getChannelName());
    //     inputChannels.put(inputChannel.getChannelName(), inputChannel);
    //     Element src = inputChannel.getSrcElement();
        
    //     // Ensure unique names for internal translators to avoid the "Name not unique" warning
    //     String prefix = inputChannel.getChannelName() + "::" + System.nanoTime() + "::";

    //     Element gainElement = ElementFactory.make("volume", prefix + "_gain");
    //     gainElements.put(inputChannel.getChannelName(), gainElement);
                
    //     // Create the 'Glue' elements
    //     Element capsFilter = ElementFactory.make("capsfilter", prefix + "caps");
    //     capsFilter.setCaps(Caps.fromString(CAPS)); 
        
    //     Element convert = ElementFactory.make("audioconvert", prefix + "conv");
    //     Element resample = ElementFactory.make("audioresample", prefix + "res");

    //     Element inputQueue = ElementFactory.make("queue", prefix + "queue");
    //     inputQueue.set("max-size-time", 200000000L); // Limit the queue size to keep latency low (200ms)

    //     pipeline.addMany(src, gainElement, capsFilter, convert, resample, inputQueue);

    //     Pad mixerSinkPad = mixerElement.getRequestPad("sink_%u");
    //     // mixerSinkPad.set("async", 0);
    //     //channelPads.put(inputChannel.getChannelName(), mixerSinkPad);

    //     boolean linked = false;
    //     if (inputChannel.supportsGain()) {
    //         if (src.link(gainElement)) {
    //              linked = gainElement.link(convert); 
    //         } 
    //     } else { 
    //         linked = src.link(convert); 
    //     }

    //     if (linked) {
    //     linked = convert.link(resample) && 
    //              resample.link(capsFilter) && 
    //              capsFilter.link(inputQueue);
    //     }

    //     if (linked) {
    //         try {
    //             inputQueue.getStaticPad("src").link(mixerSinkPad);
    //             // SUCCESS PATH
    //             double unscaledVol = inputChannel.getGain();
    //             double scaledVol = volumeScaler.uiToGstVolume(unscaledVol);
    //             mixerSinkPad.set("volume", scaledVol);
    //             gainElement.set("volume", scaledVol);

    //             channelPads.put(inputChannel.getChannelName(), mixerSinkPad);
                
    //             Stream.of(src, gainElement, capsFilter, convert, resample, inputQueue)
    //                 .forEach(Element::syncStateWithParent);
    //             inputChannel.start();

    //             // src.publishClock() ; // Force the bin to look for the pipeline clock
    //             src.setBaseTime(pipeline.getBaseTime()); // Align the '0' point

    //             Log.debugf("Connected input channel: [%s]", inputChannel.getChannelName());
    //         } catch (PadLinkException e) {
    //             Log.errorf("Link failed for [%s]: %s. Cleaning up.", inputChannel.getChannelName(), e.getMessage());
    //             pipeline.removeMany(src, gainElement, capsFilter, convert, resample, inputQueue);
    //             // Important: Release the request pad if the link failed, otherwise the mixer keeps it reserved
    //             mixerElement.releaseRequestPad(mixerSinkPad);
    //             Stream.of(gainElement, capsFilter, convert, resample, inputQueue).forEach(Element::dispose);
    //         }
    //     }
    // }

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
    public double getInputChannelVolume(String channelName) {
        Log.debugf("Trying to get channelPad [%s], current channelPads: [%s]", channelName, channelPads.keySet());
        Pad mixerSinkPad = channelPads.get(channelName);
        if (mixerSinkPad == null) {
            Log.warnf("Attempted to get volume on non-registered channel: [%s]", channelName);
            return 0;
        }
        double gstVol = (double) mixerSinkPad.get("volume");
        double uiVol = volumeScaler.gstToUiVolume(gstVol);
        
        Log.infof("returning ui vol [%s] from gstVol [%s]", uiVol, gstVol);
        return uiVol;
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

    // public double getInputChannelGain(String channelName) {
    //     InputChannel channel = inputChannels.get(channelName);
    //     if (!channel.supportsGain()) {
    //         Log.warnf("Source [%s] has no gain support...", channelName);
    //         return 0.0;
    //     }
    //     Element gainElement = gainElements.get(channelName);
    //     Double gstGain = (double) gainElement.get("volume");
    //     return gstGain;

    // }

    // public void setInputChannelGain(String channelName, double uiVolume) {
    //     InputChannel channel = inputChannels.get(channelName);
    //     if (channel == null) return; // Guard for null

    //     double scaledVol = volumeScaler.uiToGstVolume(uiVolume);
    //     if (!channel.supportsGain()) {
    //         Log.warnf("Source [%s] has no gain support...", channelName);
    //         return;
    //     }

    //     // Direct lookup is 100x faster and timestamp-proof
    //     Element gainElement = gainElements.get(channelName);
        
    //     if (gainElement != null) {
    //         gainElement.set("volume", scaledVol);
    //         Log.debugf("Gain for source [%s] set to %f", channelName, scaledVol);
    //     } else {
    //         Log.warnf("Source [%s] claims to support gain, yet has no 'volume' element", channelName);
    //     }
    // }


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
            channel.dispose();
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

    // private void setupLevelLogging() {
    //     Bus bus = pipeline.getBus();

    //     // Use connect instead of setSyncHandler for standard logging
    //     bus.connect((Bus.MESSAGE) (bus1, message) -> {
    //         if (message.getType() == MessageType.ELEMENT) {
    //             if ("master_level_probe".equals(message.getSource().getName())) {
    //                 lastMasterVu = structToRPS(message.getStructure());
    //                 Log.infof("Master VU: [%.2f dB]", lastMasterVu);    
    //             }
    //         }
    //     });
    // }

    private void setupLevelLogging() {
        Bus bus = pipeline.getBus();

        // Use a SyncHandler to intercept messages immediately on the GStreamer thread
        bus.setSyncHandler(message -> {
            if (message.getType() == MessageType.ELEMENT) {
                String srcName = message.getSource().getName();
                if ("master_level_probe".equals(srcName)) {
                    Structure struct = message.getStructure();
                    if (struct != null && struct.hasField("rms")) {
                        double[] rmsValues = struct.getDoubles("rms");
                        if (rmsValues.length > 0) {
                            // Store the highest value if stereo
                            this.lastMasterVu = rmsValues[0]; 
                            // Optional: Log here to verify it's firing
                            Log.debugf("VU: %.2f", lastMasterVu);
                        }
                    }
                }
            }
            return BusSyncReply.PASS;
        });
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

    public List<Element> dumpAllElements() {
        List<Element> elements = pipeline.getElementsRecursive();
        return elements;
    }

    public synchronized void dispose() {
        Log.info("Shutting down Mixer Core...");

        if (pipeline != null) {
            // 1. Synchronously stop the flow
            pipeline.setState(State.NULL);

            // Block until GStreamer confirms the pipeline is fully deconstructed
            // 1 second is plenty for local audio, but essential for thread safety
            pipeline.getState(1, TimeUnit.SECONDS);

            Log.debug("Pipeline reached NULL state.");
        }

        // 2. Dispose every channel explicitly (Kill timers, release file handles)
        inputChannels.values().forEach(ch -> {
            try {
                ch.dispose();
            } catch (Exception e) {
                Log.errorf("Error disposing channel %s: %s", ch.getChannelName(), e.getMessage());
            }
        });
        inputChannels.clear();

        // 3. Teardown the native graph
        if (pipeline != null) {
            // Explicitly remove children to trigger individual c-level free
            for (Element e : pipeline.getElements()) {
                pipeline.remove(e);
            }

            // Finally, release the native pipeline object
            pipeline.dispose();
            // Null it out to prevent accidental reuse
            // (and to satisfy Efficiency Tests looking for leaked pointers)
            pipeline = null;
        }

        Log.info("Mixer Core shutdown complete.");
    }

    public String dumpMixerState() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== RESON8 CONSOLE DUMP [%s] ===\n", pipeline.getState()));

        // --- MASTER SECTION ---
        double masterVol = (double) masterVolumeElement.get("volume");
        sb.append(String.format("MASTER BUS: [%s] | Volume: %.2f\n",
                masterVolumeElement.getState(), masterVol));

        // Extract VU from the level probe (GStreamer 'level' element stores last
        // message results)
        // Note: If you aren't capturing the bus messages, this might show 'idle'
        sb.append(String.format("MASTER VU:  %s\n", getVisualMeter(lastMasterVu)));
        sb.append("----------------------------------------------------------\n");

        // --- INPUT CHANNELS ---
        inputChannels.forEach((name, channel) -> {
            Element gainEl = gainElements.get(name);
            Pad sinkPad = channelPads.get(name);

            double channelGain = (gainEl != null) ? (double) gainEl.get("volume") : 1.0;
            double faderPos = (sinkPad != null) ? (double) sinkPad.get("volume") : 0.0;
            State srcState = channel.getSrcElement().getState();

            sb.append(String.format("CH: %-15s | SRC: %-7s | GAIN: %.2f | FADER: %.2f\n",
                    name.toUpperCase(), srcState, channelGain, faderPos));

            // Check for common link failures
            if (sinkPad == null || !sinkPad.isLinked()) {
                sb.append("   [!] DISCONNECTED: Sink pad missing or unlinked.\n");
            } else {
                sb.append(String.format("   -> Path: %s -> %s -> Mixer:%s\n",
                        channel.getSrcElement().getName(),
                        (gainEl != null ? gainEl.getName() : "DIRECT"),
                        sinkPad.getName()));
            }
        });

        sb.append("==========================================================\n");
        String output = sb.toString();
        Log.info(output);
        return output;
    }

    // Helper to create a text-based VU meter [######____]
    private String getVisualMeter(double db) {
        if (db <= -70)
            return "[----------] -inf dB";
        int bars = (int) Math.max(0, (db + 70) / 7); // Map -70..0 to 0..10
        return String.format("[%s%s] %.1f dB",
                "#".repeat(bars), "-".repeat(10 - bars), db);
    }


    
}