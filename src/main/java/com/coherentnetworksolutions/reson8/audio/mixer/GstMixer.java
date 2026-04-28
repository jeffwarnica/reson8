package com.coherentnetworksolutions.reson8.audio.mixer;

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
import com.coherentnetworksolutions.reson8.audio.output.from.MixerOutputToClientManagerChannel;
import com.coherentnetworksolutions.reson8.audio.utils.map.VolumeScaler;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import io.vertx.mutiny.core.eventbus.EventBus;

@Singleton
@io.quarkus.arc.properties.IfBuildProperty(name="reson8dev.audiopath", stringValue = "gst")
public class GstMixer implements Mixer {

    private volatile boolean ready = false;

    private Element mixerElement; // The GStreamer mixer element (e.g., 'audiomixer')
    private Pipeline pipeline; // The GStreamer pipeline containing the entire audio flow
    private final Map<String, InputChannel> inputChannels = new HashMap<>(); 
    private final Map<String, MixerOutputToClientManagerChannel> outputChannels = new HashMap<>();     
    private final Map<String, Pad> volPadsOfInputs = new HashMap<>();
    // private final Map<String, Element> gainElements = new HashMap<>();

    private MixerOutputToClientManagerChannel masterOutput;
    private Element masterVolumeElement;
    private Element masterTee;
    private Element levelProbe;

    @Inject EventBus eventBus;
    // @Inject VolumeScaler volumeScaler;

    private volatile double lastMasterVu;


    @Override
    @PostConstruct
    public void initGStreamer() {
        if (!Gst.isInitialized()) {
            Gst.init("Reson8Engine");
            
        } else {
            Log.info("Gst was already init'd?????");
        }
        this.pipeline = new Pipeline("audioPipeline-" + System.currentTimeMillis());
        this.mixerElement = ElementFactory.make("audiomixer", "main_mixer");
        Element mixerCaps = ElementFactory.make("capsfilter", "mixer_output_caps");
        mixerCaps.setCaps(Caps.fromString(CAPS));
        
        mixerElement.set("start-time-selection", 1); // 0 = 'first' 
        mixerElement.set("min-upstream-latency", 0);
        mixerElement.set("latency", 200000000L); // 200ms

        // mixerElement.set("start-time-selection", 1); // 1 = "Running Time"

        // Safety converters for the master output
        Element outConv = ElementFactory.make("audioconvert", "master_conv");
        
        Element outRes = ElementFactory.make("audioresample", "master_resample");
        Element outVol = ElementFactory.make("volume", "master_volume");
        this.masterVolumeElement = outVol;
        outVol.set("volume", 1.0f);
        
        // setupDropCleanup();

        // The "Splitter" - every output channel will connect to this
        this.masterTee = ElementFactory.make("tee", "master_tee");
        
        // The Level Probe (The Master VU Meter)
        this.levelProbe = ElementFactory.make("level", "master_level_probe");
        levelProbe.set("post-messages", true);
        levelProbe.set("message", true);
        levelProbe.set("interval", 1000000000L);  //1 second?

        pipeline.addMany(mixerElement, outConv, outRes, mixerCaps, masterTee, levelProbe, outVol);

        // Link: Mixer -> Convert -> Resample -> Tee
        mixerElement.link(outConv);
        outConv.link(outRes);
        outRes.link(mixerCaps);
        mixerCaps.link(outVol);
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

        pipeline.getBus().connect((Bus.ERROR) (source, code, message) -> {
            Log.errorf("GStreamer Error [%d]: %s", code, message);
        });

        setupDebugStuff();
        this.ready = true;
        eventBus.publish("mixer-ready", null);
        Log.info("Mixer Core is INITIALIZED and READY.");
    }

    // Start the entire pipeline (play audio)
    @Override
    public void start() {
        Log.info("Starting pipeline...");
        
        // Set everything to PLAYING
        pipeline.setState(State.PLAYING);

    }

    // Stop the pipeline
    @Override
    public void stop() {
        pipeline.stop();
    }    

    @Override
    public boolean isReady() {
       return ready;
    }
    
    @Override
    public synchronized void addInputChannel(InputChannel inputChannel) {
        if (inputChannels.containsKey(inputChannel.getChannelName()))
            return;
        inputChannels.put(inputChannel.getChannelName(), inputChannel);

        Element srcBin = inputChannel.getSrcElement();
        String prefix = inputChannel.getChannelName() + CHANNEL_DELINEATOR + System.nanoTime() + CHANNEL_DELINEATOR;

        Element convert = ElementFactory.make("audioconvert", prefix + "conv");
        Element resample = ElementFactory.make("audioresample", prefix + "res");
        Element capsFilter = ElementFactory.make("capsfilter", prefix + "caps");
        capsFilter.setCaps(Caps.fromString(CAPS));
        Element inputQueue = ElementFactory.make("queue", prefix + "queue");

        pipeline.addMany(srcBin, convert, resample, capsFilter, inputQueue);

        // 1. Link the GLUE logic first
        srcBin.link(convert);
        convert.link(resample);
        resample.link(capsFilter);
        capsFilter.link(inputQueue);

        Pad mixerSinkPad = mixerElement.getRequestPad("sink_%u");
        inputQueue.getStaticPad("src").link(mixerSinkPad);

        // 2. Sync the GLUE elements to the pipeline state FIRST
        // This paves the road so when the source starts, the road is open
        Stream.of(convert, resample, capsFilter, inputQueue)
                .forEach(Element::syncStateWithParent);

        // 3. Set the fader
        mixerSinkPad.set("volume", 1.0f);
        volPadsOfInputs.put(inputChannel.getChannelName(), mixerSinkPad);

        // 4. NOW start the source.
        // This triggers the internal appsrc/playbin to start pushing.
        srcBin.syncStateWithParent();
        inputChannel.start();

        Log.debugf("Channel [%s] is now live.", inputChannel.getChannelName());
    }

    @Override
    public void addOutputChannel(MixerOutputToClientManagerChannel outputChannel) {
        outputChannels.put(outputChannel.getChannelName(), outputChannel);
        if (masterOutput == null) masterOutput = outputChannel;

        Element sinkElement = outputChannel.getElement(); 
        sinkElement.setState(State.PAUSED);

        String prefix = outputChannel.getChannelName() + CHANNEL_DELINEATOR + System.nanoTime() + CHANNEL_DELINEATOR;

        Element outConv = ElementFactory.make("audioconvert", prefix + "_outconv");
        
        Element outRes = ElementFactory.make("audioresample", prefix + "_outres");
        Element outVol = ElementFactory.make("volume", prefix + "_outvol");

        // Create a caps filter for the output branch
        Element outBranchCaps = ElementFactory.make("capsfilter", prefix + "_outcaps");
        outBranchCaps.setCaps(Caps.fromString(CAPS));

        pipeline.addMany(outConv, outRes, outBranchCaps, outVol, sinkElement);

        Pad teeSrcPad = masterTee.getRequestPad("src_%u");
        teeSrcPad.link(outConv.getStaticPad("sink"));
        
        outConv.link(outRes);
        outRes.link(outBranchCaps);
        outBranchCaps.link(outVol);
        outVol.link(sinkElement);
        outVol.set("volume", 1.0f);

        // Push it live (Order matters: Sink first, then work backwards to the Tee)
        sinkElement.syncStateWithParent();
        outVol.syncStateWithParent();
        outRes.syncStateWithParent();
        outConv.syncStateWithParent();

        sinkElement.setState(State.PLAYING);
        Log.infof("Output channel [%s] is now live and connected to the master mixer, with gs state [%s]",
             outputChannel.getChannelName(), sinkElement.getState());
    }
   

    // Set the master volume for the mixer
    @Override
    public void setMasterVolume(double uiVolume) {
        double scaledVol = VolumeScaler.humanToGstVolume(uiVolume);
        masterVolumeElement.set("volume", scaledVol);
    }

    @Override
    public double getMasterVolume() {
        double gstVol = (double) masterVolumeElement.get("volume");
        double humanVol = VolumeScaler.gstToHumanVolume(gstVol);
        return humanVol;
    }

    @Override
    public void setInputChannelVolume(String channelName, double humanVolume) {
        // 1. Get the pad from our registry, NOT the channel itself
        Pad mixerSinkPad = volPadsOfInputs.get(channelName);
        
        if (mixerSinkPad != null) {
            double scaledVol = VolumeScaler.humanToGstVolume(humanVolume);
            
            Log.debugf("Setting mixer sink pad volume for [%s] from uiVol [%s] to GS vol [%s]", channelName, humanVolume, scaledVol);
            
            // 2. Set the property on the PAD, not the element
            mixerSinkPad.set("volume", scaledVol);
        } else {
            Log.errorf("Could not find mixer sink pad for channel: %s", channelName);
        }
    }
    @Override
    public double getInputChannelVolume(String channelName) {
        Log.tracef("Trying to get channelPad [%s], current channelPads: [%s]", channelName, volPadsOfInputs.keySet());
        Pad mixerSinkPad = volPadsOfInputs.get(channelName);
        if (mixerSinkPad == null) {
            Log.warnf("Attempted to get volume on non-registered channel: [%s]", channelName);
            return 0;
        }
        double gstVol = (double) mixerSinkPad.get("volume");
        double uiVol = VolumeScaler.gstToHumanVolume(gstVol);
        
        Log.tracef("returning ui vol [%s] from gstVol [%s]", uiVol, gstVol);
        return uiVol;
    }


    // Set the volume for a specific output channel
    @Override
    public void setOutputChannelVolume(String channelName, double uiVolume) {
        MixerOutputToClientManagerChannel channel = outputChannels.get(channelName);
        if (channel != null) {
            Element element = channel.getElement();
            double scaledVol = VolumeScaler.humanToGstVolume(uiVolume);
            element.set("volume", scaledVol);
        } else {
            Log.errorf("Error: Output channel [%s] not found", channelName);
        }
    }

    // Method to retrieve an input channel by name
    @Override
    public InputChannel getInputChannel(String channelName) {
        return inputChannels.get(channelName);
    }

    // Method to retrieve an output channel by name
    @Override
    public MixerOutputToClientManagerChannel getOutputChannel(String channelName) {
        return outputChannels.get(channelName);
    }

    // Method to retrieve an input channel by name
    @Override
    public Map<String, InputChannel> getInputChannels() {
        return inputChannels;
    }

    // Method to retrieve an output channel by name
    @Override
    public Map<String, MixerOutputToClientManagerChannel> getOutputChannels() {
        return outputChannels;
    }

    @Override
    public Element getMixerElement() {
        return mixerElement;
    }
    
    @Override
    public Pipeline getPipeline() {
        return pipeline;
    }

    @Override
    public synchronized void removeInputChannel(String channelName) {
        InputChannel channel = inputChannels.remove(channelName);
        // Element gainElement = gainElements.remove(channelName);
        Pad mixerSinkPad = volPadsOfInputs.remove(channelName);

        if (channel != null) {
            channel.getSrcElement().setState(State.NULL);

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

    @Override
    public synchronized void removeOutputChannel(String channelName) {
        MixerOutputToClientManagerChannel channel = outputChannels.remove(channelName);
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

            // The Cleanup Vacuum
            pipeline.getElements().stream()
                .filter(e -> e.getName().startsWith(channelName + CHANNEL_DELINEATOR))
                .forEach(e -> {
                    e.setState(State.NULL);
                    pipeline.remove(e);
                    e.dispose();
                });
                
            Log.infof("Output channel [%s] decommissioned.", channelName);
        }
    }

    @Override
    public MixerOutputToClientManagerChannel getMasterOutput() {
        return masterOutput;
    }

    /**
     * Add a bus watcher that looks for our one-shot drops ending, and then removes that channel
     */
    @Override
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
                            Log.tracef("VU: %.2f", lastMasterVu);
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
                    Thread.sleep(10000);
                    // Manually query the 'last-sample' or properties if supported, 
                    // but better yet, let's fix the sync handler.
                    Log.tracef("Pipeline State: %s", pipeline.getState(0));
                } catch (Exception e) {}
            }
        }).start();
    }

    @Override
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

    @Override
    public List<Element> dumpAllElements() {
        List<Element> elements = pipeline.getElementsRecursive();
        return elements;
    }

    public synchronized void dispose() {
        if (pipeline == null) return;

        // 1. Force state to NULL
        pipeline.setState(State.NULL);
        
        // 2. BLOCK until it's actually NULL. 
        // If this times out, the native threads are hung.
        pipeline.setState(State.NULL);
        State ret = pipeline.getState(2, TimeUnit.SECONDS);
        
        if (ret == State.NULL) {
            Log.error("Pipeline failed to reach NULL state. Forcing disposal anyway.");
        }

        Log.debugf("Pipeline state change to NULL returned: %s", ret);

        // 3. Clear the Bus
        // Sometimes messages stuck on the bus prevent disposal
        pipeline.getBus().setFlushing(true);

        // 4. Dispose
        try {
            pipeline.dispose();
        } catch (Exception e) {
            Log.error("Native disposal failed", e);
        } finally {
            pipeline = null;
        }

        // Gst.deinit();
    }

    @Override
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
            
            Pad sinkPad = volPadsOfInputs.get(name);

            double channelGain = channel.getGain();
            double faderPos = (sinkPad != null) ? (double) sinkPad.get("volume") : 0.0;
            State srcState = channel.getSrcElement().getState();

            sb.append(String.format("CH: %-15s | SRC: %-7s | GAIN: %.2f | FADER: %.2f\n",
                    name.toUpperCase(), srcState, channelGain, faderPos));

            // Check for common link failures
            if (sinkPad == null || !sinkPad.isLinked()) {
                sb.append("   [!] DISCONNECTED: Sink pad missing or unlinked.\n");
            } else {
                sb.append(String.format("   -> Path: %s ->-> Mixer:%s\n",
                        channel.getSrcElement().getName(),
                        // (gainEl != null ? gainEl.getName() : "DIRECT"),
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