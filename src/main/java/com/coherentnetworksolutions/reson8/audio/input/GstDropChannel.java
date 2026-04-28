package com.coherentnetworksolutions.reson8.audio.input;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.AppSrc;

import com.coherentnetworksolutions.reson8.audio.providers.GstToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * A persistent channel for 'Drop' type sounds.
 * It stays connected to the Mixer but only pushes data when triggered.
 * <p>
 * Target/current intensity (0–100) are stored for UI / control-plane parity; they do not
 * drive drop playback (see {@link #trigger(double)}).
 */
public class GstDropChannel extends BaseInputChannel implements DropChannel {

    private final CachedWav cachedWav;

    private double volume;

    private Bin channelBin;
    private Element channelMixer;
    private GstToolkit toolkit;

    private final Object binLock = new Object();

    /**
     * Executor for the trigger method.
     * This is used to ensure that the trigger method is executed asynchronously
     * and does not block the main thread.
     * 
     * Named, so to be isolated from other executors.
     */
    private static final ExecutorService TRIGGER_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "drop-trigger");
        t.setDaemon(true);
        return t;
    });

    public GstDropChannel(SignalBucket signalEndpoint, CachedWav cachedWav, GstToolkit toolkit) {
        super(signalEndpoint.getName(), 0.0, 0.0);
        this.cachedWav = cachedWav;
        this.toolkit = toolkit;

        this.volume = signalEndpoint.getSoundDefinition().drop().get().gain();

        this.channelBin = toolkit.createBin(getChannelName() + "_bin");
        this.channelMixer = toolkit.makeElement("audiomixer", getChannelName() + "_sum");

        channelMixer.set("start-time-selection", 1); // 1 = Running Time

        channelMixer.set("latency", 10000000L); // 10ms

        channelMixer.set("ignore-inactive-pads", true);

        channelMixer.set("min-upstream-latency", 10000000L); // 10ms

        toolkit.addElementToBin(channelBin, channelMixer);

        toolkit.addPad(channelBin, toolkit.createGhostPad("src", toolkit.getStaticPad(channelMixer, "src")));

        Log.debugf("DropChannel [%s] registered and ready.", getChannelName());
    }

    @Override
    public void trigger(@Min(0) @Max(100) double volume) {
        Log.debugf("Channel [%s] triggered with volume [%s]", getChannelName(), volume);
        Log.debugf("Channel [%s] channelMixer.state is [%s]", getChannelName(), channelMixer.getState());
        Log.debugf("Channel [%s] channelBin.state is [%s]", getChannelName(), channelBin.getState());

        CompletableFuture.runAsync(() -> {
            try {
                DropInstance dropInstance = new DropInstance(cachedWav, volume);

                synchronized (binLock) {
                    toolkit.setElementState(channelBin, State.PLAYING);
                }

                dropInstance.linkAndStart();

                Log.debugf("[%s] Instance successfully started asynchronously", getChannelName());
            } catch (Exception e) {
                Log.errorf(e, "Failed to trigger drop instance for channel [%s]", getChannelName());
            }
        }, TRIGGER_EXECUTOR);

        Log.debugf("[%s] Triggered instance with volume %.2f", getChannelName(), volume);
    }

    @Override
    public void start() {
        Log.debugf("DropChannel [%s] started (waiting for triggers).", getChannelName());
    }

    @Override
    public Caps getCaps() {
        return cachedWav.caps();
    }

    @Override
    public void setGain(@Min(0) @Max(100) double volume) {
        this.volume = volume;
    }

    @Override
    public double getGain() {
        return volume;
    }

    @Override
    public Element getSrcElement() {
        return channelBin;
    }

    @Override
    public void dispose() {
    }

    private class DropInstance {
        private final AppSrc instanceSrc;
        private final Bin instanceBin;
        private Pad myReservedMixerPad;
        private int bytesPushed = 0;
        private String instanceId;

        public DropInstance(CachedWav cachedWav, double triggerVolume) {
            
            instanceId = getChannelName() + "_inst_" + System.nanoTime();

            Log.debugf("channelMixer has pads: [%s]", channelMixer.getPads());
            myReservedMixerPad = toolkit.getRequestPad(channelMixer, "sink_%u");

            Log.debugf("Got reserved pad from channelMixer: [%s] for instance [%s]", myReservedMixerPad.getName(), instanceId);

            instanceBin = toolkit.createBin(instanceId + "_bin");

            instanceSrc = toolkit.makeAppSrc(instanceId + "_src");
            instanceSrc.set("format", Format.TIME);
            instanceSrc.set("is-live", true);
            instanceSrc.set("emit-signals", true);
            instanceSrc.set("do-timestamp", true);
            instanceSrc.setCaps(cachedWav.caps());
            Log.debugf("instanceSrc at creation is in state: [%s]", instanceSrc.getState());

            Element vol = toolkit.makeElement("volume", instanceId + "_vol");
            vol.set("volume", triggerVolume);

            Element conv = toolkit.makeElement("audioconvert", instanceId + "_conv");
            Element resample = toolkit.makeElement("audioresample", instanceId + "_res");

            Log.debugf("Created elements for instance [%s]: src=[%s], vol=[%s], conv=[%s], resample=[%s]",
                    instanceId, instanceSrc.getName(), vol.getName(), conv.getName(), resample.getName());
            toolkit.addMany(instanceBin, instanceSrc, vol, conv, resample);

            toolkit.linkMany(instanceSrc, vol, conv, resample, instanceBin);

            toolkit.addPad(instanceBin, toolkit.createGhostPad("src", toolkit.getStaticPad(resample, "src")));

            toolkit.syncStateWithParent(instanceBin);

            toolkit.connectNeedData(instanceSrc, (elem, size) -> {
                Log.debugf("NEED_DATA callback triggered for instance [%s], requested size: [%s]", instanceId, size);
                byte[] data = cachedWav.pcmData();
                int bytesPerFrame = cachedWav.bytesPerFrame();

                int remaining = data.length - bytesPushed;
                if (remaining <= 0) {
                    elem.endOfStream();
                    return;
                }

                int requestSize = (size > 0) ? size : 4096;
                int bufferSize = Math.min(remaining, requestSize);

                bufferSize -= (bufferSize % bytesPerFrame);

                if (bufferSize <= 0) {
                    elem.endOfStream();
                    return;
                }

                Buffer buffer = toolkit.createBuffer(bufferSize);
                toolkit.fillBuffer(buffer, data, bytesPushed, bufferSize);

                long currentTimestampNano = (long) ((bytesPushed / bytesPerFrame) * 1_000_000_000L / cachedWav.sampleRate());

                long framesInThisBuffer = bufferSize / bytesPerFrame;
                long durationNano = (long) ((framesInThisBuffer * 1_000_000_000L) / cachedWav.sampleRate());
                toolkit.setDuration(buffer, durationNano);

                Log.debugf("Had set PresentationTimestamp to [%s], and Duration to [%s]", currentTimestampNano, durationNano);
                toolkit.pushBuffer(instanceSrc, buffer);

                bytesPushed += bufferSize;
            });
        }

        public void linkAndStart() {
            synchronized (binLock) {
                toolkit.addElementToBin(channelBin, instanceBin);
            }
            Log.debugf("instanceBin pads: [%s]", instanceBin.getPads());

            // Route through toolkit so the spy can observe and mock stays consistent
            // (toolkit.getStaticPad returns the same cached pad on every call for a given element+name)
            Pad binSrcPad = toolkit.getStaticPad(instanceBin, "src");
            toolkit.linkPads(binSrcPad, myReservedMixerPad);

            Pad srcPad = toolkit.getStaticPad(instanceSrc, "src");
            toolkit.addEosProbe(srcPad, () -> cleanup(myReservedMixerPad));

            toolkit.setElementState(instanceBin, State.PLAYING);
        }

        /**
         * Tears down this drop instance after EOS.
         * <p>
         * {@code channelBin} and {@code channelMixer} are referenced directly as outer-class
         * fields — NOT passed as parameters — to make it unambiguous which objects are the
         * long-lived channel components versus the short-lived per-drop components.
         */
        private void cleanup(Pad reservedMixerPad) {
            Log.debugf("Cleanup of instance [%s], releasing mixer pad [%s]", instanceId, reservedMixerPad.getName());

            // 1. Stop the instance bin first so all its native C elements are flushed and
            //    their GStreamer refcounts drop before we touch the graph structure.
            toolkit.setElementState(instanceBin, State.NULL);

            // 2. Detach from the mixer — same toolkit.getStaticPad call as linkAndStart() so
            //    the mock returns the identical cached pad, making link/unlink symmetrical.
            toolkit.unlinkPads(toolkit.getStaticPad(instanceBin, "src"), reservedMixerPad);

            // 3. Remove the bin; channelBin drops its reference, allowing GC to unref natively.
            synchronized (binLock) {
                toolkit.removeElementFromBin(channelBin, instanceBin);
            }

            // 4. Return the mixer request pad to the pool for future triggers.
            toolkit.releaseRequestPad(channelMixer, reservedMixerPad);

            Log.debugf("Instance [%s] fully removed.", instanceId);
        }
    }
}
