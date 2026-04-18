package com.coherentnetworksolutions.reson8.audio.input;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeReturn;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.AppSrc;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * A persistent channel for 'Drop' type sounds. 
 * It stays connected to the Mixer but only pushes data when triggered.
 */
public class GstDropChannel implements InputChannel, DropChannel {
    private final String channelName;
    private final CachedWav cachedWav;
    
    private double volume;
    
    private Bin channelBin;
    private Element channelMixer;

    
    public GstDropChannel(SignalBucket signalEndpoint, CachedWav cachedWav) {
        this.channelName = signalEndpoint.getName();

        //Get (a reference to) PCM data locally
        this.cachedWav = cachedWav;
        // dropFactory.getDataFor(signalEndpoint.getSoundDefinition().drop().map(d -> d.filename()).orElseThrow());

        // //Calculate our Caps once
        // // String channelMask = (cachedPcmData.channels == 1) ? "0x0" : "0x3";
        // caps = Caps.fromString(String.format("audio/x-raw,format=%s,channels=%d,rate=%d,layout=interleaved,channel-mask=(bitmask)%s",
        //         cachedPcmData.gstFormat(), cachedPcmData.channels(), (int)cachedPcmData.sampleRate(), cachedPcmData.channelMask()));
        // Log.debugf("Setting caps for [%s] to [%s]", channelName, caps.toString());
        this.volume = signalEndpoint.getSoundDefinition().drop().map(d -> d.gain()).orElse(1.0);

        //gstreamer bucket
        this.channelBin = new Bin(channelName + "_bin");
        this.channelMixer = ElementFactory.make("audiomixer", channelName + "_sum");

        channelMixer.set("start-time-selection", 1); // 1 = Running Time
        // channelMixer.set("force-live", true);

        // Low latency for drops
        channelMixer.set("latency", 10000000L); // 10ms

        // deal with a bunch of bins being created, attached, quickly
        channelMixer.set("ignore-inactive-pads", true);

        // Add this to prevent the mixer from "stuttering" while waiting for new
        // spam-clicks
        channelMixer.set("min-upstream-latency", 10000000L); // 10ms

        channelBin.add(channelMixer);

        // Ghost the output of our internal mixer to the main Mixer
        channelBin.addPad(new GhostPad("src", channelMixer.getStaticPad("src")));
        

        Log.debugf("DropChannel [%s] registered and ready.", channelName);
    }

    @Override
    public void trigger(@Min(0) @Max(100) double volume) {
        Log.debugf("Channel [%s] triggered with volume [%s]", channelName, volume);
        Log.debugf("Channel [%s] channelMixer.state is [%s]", channelName, channelMixer.getState());
        Log.debugf("Channel [%s] channelBin.state is [%s]", channelName, channelBin.getState());

        // get this off our main path of execution, but not quite a new thread
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Create instance (native allocations happen here)
                DropInstance dropInstance = new DropInstance(cachedWav, volume);

                channelBin.setState(State.PLAYING);
                
                // Add to the internal mixer
                dropInstance.linkAndStart();

                Log.debugf("[%s] Instance successfully started asynchronously", channelName);
            } catch (Exception e) {
                Log.errorf(e, "Failed to trigger drop instance for channel [%s]", channelName);
            }
        });

        // 3. Push data (The Need Data callback will fire once synced)
        Log.debugf("[%s] Triggered instance with volume %.2f", channelName, volume);
    }

    @Override
    public void start() {
        // No-op for Drop; it waits for trigger()
        Log.debugf("DropChannel [%s] started (waiting for triggers).", channelName);
    }

    @Override
    public Caps getCaps() {
        return cachedWav.caps();
    }

    @Override
    public String getChannelName() { return channelName; }

    @Override
    public void setGain(@Min(0) @Max(100) double volume) { 
        this.volume = volume; 
    }

    @Override
    public double getGain() { return volume; }

    @Override
    public Element getSrcElement() { return channelBin; }


    // @Override
    // public boolean supportsGain() { return true; }

    // @Override
    // public boolean supportsIntensity() { return false; }

    @Override 
    public void setIntensity(@Min(0) @Max(100) double intensity) { }

    @Override
    public double getIntensity() { return 0.0; }

    @Override
    public void dispose() {
        // if (bin != null) {
        // bin.endOfStream();
        // }
    }

    private class DropInstance {
        private final AppSrc instanceSrc;
        private final Bin instanceBin; // A small sub-bin for this instance
        private Pad myReservedMixerPad; //this instances pad on the channel mixer
        private int bytesPushed = 0;
        private String instanceId;

        public DropInstance(CachedWav cachedWav, double triggerVolume) {
            instanceId = channelName + "_inst_" + System.nanoTime();

            Log.debugf("channelMixer has pads: [%s]", channelMixer.getPads());
            myReservedMixerPad = channelMixer.getRequestPad("sink_%u");
            Log.debugf("Got reserved pad from channelMixer: [%s] for instance [%s]", myReservedMixerPad.getName(), instanceId);

            instanceBin = new Bin(instanceId + "_bin");

            instanceSrc = (AppSrc) ElementFactory.make("appsrc", instanceId + "_src");
            instanceSrc.set("format", Format.TIME);
            instanceSrc.set("is-live", true); //2100
            instanceSrc.set("emit-signals", true);
            instanceSrc.set("do-timestamp", true); //2100
            instanceSrc.setCaps(cachedWav.caps());
            Log.debugf("instanceSrc at creation is in state: [%s]", instanceSrc.getState());

            Element vol = ElementFactory.make("volume", instanceId + "_vol");
            vol.set("volume", triggerVolume);
            Element conv = ElementFactory.make("audioconvert", instanceId + "_conv");
            Element resample = ElementFactory.make("audioresample", instanceId + "_res");

            instanceBin.addMany(instanceSrc, vol, conv, resample);

            instanceSrc.link(vol);
            vol.link(conv);
            conv.link(resample);
            resample.link(instanceBin);
            
            instanceBin.addPad(new GhostPad("src", resample.getStaticPad("src")));
            instanceBin.syncStateWithParent();            
            
            instanceSrc.connect((AppSrc.NEED_DATA) (elem, size) -> {
                byte[] data = cachedWav.pcmData();
                int bytesPerFrame = cachedWav.bytesPerFrame(); // e.g., 3 for 24-bit mono, 4 for 16-bit stereo

                int remaining = data.length - bytesPushed;
                if (remaining <= 0) {
                    elem.endOfStream();
                    return;
                }

                // GStreamer's suggested 'size' is usually in bytes
                int requestSize = (size > 0) ? size : 4096;
                int bufferSize = Math.min(remaining, requestSize);

                // CRITICAL ALIGNMENT:
                // This ensures we never cut a frame (like a 3-byte 24-bit sample) in half.
                bufferSize -= (bufferSize % bytesPerFrame);

                // If the data remaining is less than one single frame, we're done
                if (bufferSize <= 0) {
                    elem.endOfStream();
                    return;
                }

                Buffer buffer = new Buffer(bufferSize);
                buffer.map(true).put(ByteBuffer.wrap(data, bytesPushed, bufferSize));
                buffer.unmap();

                long currentTimestampNano = (long) ((bytesPushed / bytesPerFrame) * 1_000_000_000L / cachedWav.sampleRate());
                // buffer.setPresentationTimestamp(currentTimestampNano);

                long framesInThisBuffer = bufferSize / bytesPerFrame;
                long durationNano = (long) ((framesInThisBuffer * 1_000_000_000L) / cachedWav.sampleRate());
                buffer.setDuration(durationNano);
                
                Log.debugf("Had set PresentationTimestamp to [%s], and Duration to [%s]", currentTimestampNano, durationNano);
                elem.pushBuffer(buffer);
                
                bytesPushed += bufferSize;
            });
        }


        public void linkAndStart() {
            channelBin.add(instanceBin);
            Log.debugf("instanceBin pads: [%s]", instanceBin.getPads());
            
            Pad binSrcPad = instanceBin.getStaticPad("src");
            binSrcPad.link(myReservedMixerPad);

            Pad srcPad = instanceSrc.getStaticPad("src");

            // Attach a probe to the src (out) pad of what actually is initiating sound
            srcPad.addProbe(PadProbeType.EVENT_DOWNSTREAM, (pad, info) -> {
                if (info.getEvent() instanceof org.freedesktop.gstreamer.event.EOSEvent) {
                    Log.debugf("Dealing with event on pad [%s]: [%s]", pad.getName(), info.getEvent());
                    // full on thread to get outside of gstreamer c thread so not to block it
                    new Thread(() -> cleanup(channelBin, channelMixer, myReservedMixerPad)).start();
                    return PadProbeReturn.DROP;
                }
                return PadProbeReturn.OK;
            });

            this.instanceBin.setState(State.PLAYING);
        }

        private void cleanup(Bin parentBin, Element channelMixer, Pad myReservedMixerPad) {
            Log.debugf("Cleanup of mixer pad: [%s] for instance [%s]", myReservedMixerPad.getName(), instanceId);
            
            /*
             * disconnect our instance bin from the channel bin so the channel bin gets nothing
             * and cant get confused if sound or events sneak through
             */
            this.instanceBin.getStaticPad("src").unlink(myReservedMixerPad);

            // this specific instance to NULL so it stops processing
            this.instanceBin.setState(State.NULL);

            // finally remove, after disconnection and stopping
            parentBin.remove(this.instanceBin);

            // Release the mixer pad so it can be reused by the next "spam" click
            channelMixer.releaseRequestPad(myReservedMixerPad);
        
            Log.debugf("Instance [%s] fully removed.", instanceId);
        }
    }

}
