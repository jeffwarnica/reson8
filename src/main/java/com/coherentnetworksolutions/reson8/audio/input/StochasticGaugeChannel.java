package com.coherentnetworksolutions.reson8.audio.input;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.AppSrc;

import com.coherentnetworksolutions.reson8.audio.providers.GsToolkit;
import com.coherentnetworksolutions.reson8.audio.sound.SoundHitBundle;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache;
import com.coherentnetworksolutions.reson8.audio.sound.WavCache.CachedWav;
import com.coherentnetworksolutions.reson8.audio.utils.map.VolumeScaler;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.StochasticConfig;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;

import io.quarkus.logging.Log;

/**
 * A gauge channel that generates a stochastic soundscape (e.g. crickets) by
 * maintaining a fixed pool of persistent AppSrc sub-channels, each always
 * streaming — either live PCM from a randomly-chosen sample or silence.
 * <p>
 * Intensity (0–100) drives a Poisson process whose rate λ = maxSimultaneous ×
 * norm². The tick fires every {@value #TICK_MS} ms, samples k ~ Poisson(λ·dt),
 * and assigns idle pool slots a random {@link CachedWav} from the bundle.
 */
public class StochasticGaugeChannel extends BaseInputChannel implements GaugeChannel {

    private static final int TICK_MS = 50;
    private static final double SMOOTHING_EPSILON = 0.1;

    private final GsToolkit toolkit;
    private final SoundHitBundle hitBundle;
    private final Bin channelBin;
    private final Element channelMixer;
    private final Caps bundleCaps;
    /** Pre-computed zeroed buffer for silence: 10ms of PCM at the bundle's format. */
    private final byte[] silenceBuffer;
    private final long silenceDurationNano;
    private final double smoothingRate;
    private final int maxSimultaneous;
    /**
     * Max random pitch deviation in semitones, from {@link StochasticConfig#pitchRandomization()}.
     * Each chirp is shifted by a uniform draw from {@code [-pitchRandomization, +pitchRandomization]}
     * semitones via Java-side PCM resampling — no GStreamer caps change needed.
     * Zero disables resampling entirely.
     */
    private final double pitchRandomization;
    private final List<ChirpSlot> slots;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stochastic-tick");
        t.setDaemon(true);
        return t;
    });

    public StochasticGaugeChannel(SignalBucket signalBucket, Reson8Config config, WavCache wavCache,
            GsToolkit toolkit) {
        super(signalBucket.getName(), 0.0, 0.0);
        this.toolkit = toolkit;

        StochasticConfig stochasticConfig = signalBucket.getSoundDefinition().stochastic().orElseThrow();
        this.smoothingRate = stochasticConfig.smoothingrate();
        this.maxSimultaneous = stochasticConfig.maxSimultaneous();
        this.pitchRandomization = stochasticConfig.pitchRandomization();
        initCeiling(stochasticConfig.ceiling());
        
        this.hitBundle = new SoundHitBundle(stochasticConfig.directory(), config, wavCache);
        if (hitBundle.isEmpty()) {
            throw new IllegalArgumentException(
                    "StochasticGaugeChannel [" + getChannelName() + "] bundle directory is empty");
        }

        CachedWav firstWav = hitBundle.getCachedWavAt(0);
        this.bundleCaps = firstWav.caps();

        int silenceFrames = (int) (firstWav.sampleRate() * 0.010); // 10ms
        this.silenceBuffer = new byte[silenceFrames * firstWav.bytesPerFrame()]; // zero-filled
        this.silenceDurationNano = (long) (silenceFrames * 1_000_000_000L / firstWav.sampleRate());

        this.channelBin = toolkit.createBin(getChannelName() + "_bin");
        this.channelMixer = toolkit.makeElement("audiomixer", getChannelName() + "_sum");
        toolkit.setElementProperty(channelMixer, "start-time-selection", 1); // running time
        toolkit.setElementProperty(channelMixer, "latency", 10_000_000L);    // 10ms
        toolkit.setElementProperty(channelMixer, "ignore-inactive-pads", true);
        toolkit.setElementProperty(channelMixer, "min-upstream-latency", 10_000_000L);
        toolkit.addElementToBin(channelBin, channelMixer);

        this.slots = new ArrayList<>(maxSimultaneous);
        for (int i = 0; i < maxSimultaneous; i++) {
            ChirpSlot slot = new ChirpSlot(i);
            toolkit.addElementToBin(channelBin, slot.slotBin);
            toolkit.linkPads(
                    toolkit.getStaticPad(slot.slotBin, "src"),
                    toolkit.getRequestPad(channelMixer, "sink_%u"));
            slots.add(slot);
        }

        toolkit.addPad(channelBin, toolkit.createGhostPad("src",
                toolkit.getStaticPad(channelMixer, "src")));

        scheduler.scheduleWithFixedDelay(this::intensityTick, 0, TICK_MS, TimeUnit.MILLISECONDS);

        Log.debugf("StochasticGaugeChannel [%s] ready: %d slots, caps: %s",
                getChannelName(), maxSimultaneous, bundleCaps);
    }

    // -------------------------------------------------------------------------
    // Tick logic
    // -------------------------------------------------------------------------

    private void intensityTick() {
        stepSmoothTowardTarget(smoothingRate, SMOOTHING_EPSILON);

        double norm = getCurrentIntensity() / 100.0;
        double lambda = maxSimultaneous * norm * norm;           // chirps/second
        double mu = lambda * (TICK_MS / 1000.0);                // expected events this tick

        int k = poissonSample(mu);
        Log.tracef("[%s] intensity=%.1f norm=%.2f λ=%.2f μ=%.3f k=%d",
                getChannelName(), getCurrentIntensity(), norm, lambda, mu, k);

        for (int i = 0; i < k; i++) {
            ChirpSlot slot = findIdleSlot();
            if (slot == null) {
                Log.tracef("[%s] pool exhausted (%d slots busy), skipping %d remaining events",
                        getChannelName(), maxSimultaneous, k - i);
                break;
            }
            CachedWav wav = hitBundle.getRandomCachedWav();
            if (wav == null) break;
            slot.play(wav, VolumeScaler.humanToGsVolume(getCeiling()));
        }
    }

    private ChirpSlot findIdleSlot() {
        for (ChirpSlot slot : slots) {
            if (slot.isIdle()) return slot;
        }
        return null;
    }

    /**
     * Resamples {@code wav}'s PCM by linear interpolation to achieve a pitch shift.
     * <p>
     * A {@code speedFactor > 1} produces fewer output frames (higher pitch at the
     * original sample rate); {@code speedFactor < 1} produces more frames (lower
     * pitch). Only S16 signed PCM (LE or BE) is supported; other formats are returned
     * unchanged with a warning.
     */
    private static byte[] resamplePcm(CachedWav wav, double speedFactor) {
        AudioFormat fmt = wav.audioFormat();
        if (!fmt.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                || fmt.getSampleSizeInBits() != 16) {
            Log.warnf("resamplePcm: unsupported format %s — pitch shift skipped", fmt);
            return wav.pcmData();
        }

        byte[] src = wav.pcmData();
        int bytesPerFrame = wav.bytesPerFrame();
        int channels = fmt.getChannels();
        boolean bigEndian = fmt.isBigEndian();

        int inputFrames = src.length / bytesPerFrame;
        int outputFrames = Math.max(1, (int) Math.round(inputFrames / speedFactor));
        byte[] out = new byte[outputFrames * bytesPerFrame];

        for (int i = 0; i < outputFrames; i++) {
            double srcPos = i * speedFactor;
            int f0 = Math.min((int) srcPos, inputFrames - 1);
            int f1 = Math.min(f0 + 1, inputFrames - 1);
            double frac = srcPos - f0;

            for (int ch = 0; ch < channels; ch++) {
                int off0 = f0 * bytesPerFrame + ch * 2;
                int off1 = f1 * bytesPerFrame + ch * 2;

                short s0 = bigEndian
                        ? (short) ((src[off0] << 8) | (src[off0 + 1] & 0xFF))
                        : (short) ((src[off0] & 0xFF) | (src[off0 + 1] << 8));
                short s1 = bigEndian
                        ? (short) ((src[off1] << 8) | (src[off1 + 1] & 0xFF))
                        : (short) ((src[off1] & 0xFF) | (src[off1 + 1] << 8));

                short result = (short) Math.round(s0 + frac * (s1 - s0));
                int outOff = i * bytesPerFrame + ch * 2;
                if (bigEndian) {
                    out[outOff]     = (byte) ((result >> 8) & 0xFF);
                    out[outOff + 1] = (byte) (result & 0xFF);
                } else {
                    out[outOff]     = (byte) (result & 0xFF);
                    out[outOff + 1] = (byte) ((result >> 8) & 0xFF);
                }
            }
        }
        return out;
    }

    /**
     * Knuth algorithm for sampling k ~ Poisson(mu). Suitable for small mu (< 30).
     * Returns 0 immediately when mu is effectively zero.
     */
    private static int poissonSample(double mu) {
        if (mu <= 0.0) return 0;
        double L = Math.exp(-mu);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= ThreadLocalRandom.current().nextDouble();
        } while (p > L);
        return k - 1;
    }

    // -------------------------------------------------------------------------
    // InputChannel / GaugeChannel contract
    // -------------------------------------------------------------------------

    @Override
    public void start() {
        toolkit.setElementState(channelBin, State.PLAYING);
        Log.debugf("StochasticGaugeChannel [%s] started.", getChannelName());
    }

    @Override
    public Element getSrcElement() {
        return channelBin;
    }

    @Override
    public Caps getCaps() {
        return bundleCaps;
    }

    @Override
    public void dispose() {
        scheduler.shutdown();
        toolkit.setElementState(channelBin, State.NULL);
    }

    // -------------------------------------------------------------------------
    // ChirpSlot — persistent always-streaming AppSrc sub-channel
    // -------------------------------------------------------------------------

    /**
     * One lane in the pool. The AppSrc streams continuously: PCM from a
     * {@link CachedWav} while a chirp is active, silent zeroed buffers otherwise.
     * <p>
     * Thread-safety: {@code play()} is called from the scheduler thread;
     * {@code onNeedData()} is called from a GStreamer streaming thread. Both
     * synchronise on {@code this}.
     */
    private class ChirpSlot {

        final Bin slotBin;
        private final AppSrc slotSrc;
        private final Element slotVol;

        private CachedWav currentWav = null;
        /** PCM data for the active chirp — either original or resampled for pitch shift. */
        private byte[] currentData = null;
        private int byteOffset = 0;

        ChirpSlot(int index) {
            String id = getChannelName() + "_slot_" + index;

            slotBin = toolkit.createBin(id + "_bin");
            slotSrc = toolkit.makeAppSrc(id + "_src");
            toolkit.setElementProperty(slotSrc, "format", Format.TIME);
            toolkit.setElementProperty(slotSrc, "is-live", true);
            toolkit.setElementProperty(slotSrc, "emit-signals", true);
            toolkit.setElementProperty(slotSrc, "do-timestamp", true);
            toolkit.setAppSrcCaps(slotSrc, bundleCaps);

            slotVol = toolkit.makeElement("volume", id + "_vol");
            toolkit.setElementProperty(slotVol, "volume", 1.0);

            Element conv = toolkit.makeElement("audioconvert", id + "_conv");
            Element resample = toolkit.makeElement("audioresample", id + "_res");

            toolkit.addMany(slotBin, slotSrc, slotVol, conv, resample);
            toolkit.linkMany(slotSrc, slotVol, conv, resample);
            toolkit.addPad(slotBin, toolkit.createGhostPad("src",
                    toolkit.getStaticPad(resample, "src")));

            toolkit.connectNeedData(slotSrc, (elem, requestedSize) -> onNeedData(elem, requestedSize));
        }

        /**
         * Assign a new chirp to this slot. Returns {@code false} if the slot is busy.
         * <p>
         * When {@link #pitchRandomization} is non-zero, the PCM data is resampled in
         * Java (linear interpolation) to achieve a random pitch shift in
         * {@code [-pitchRandomization, +pitchRandomization]} semitones.
         * A speed factor > 1 produces fewer output frames → higher pitch at the same
         * sample rate. The AppSrc caps are never changed, so {@code audioresample}
         * is never flushed and no tail frames are lost.
         */
        synchronized boolean play(CachedWav wav, double gstVolume) {
            if (currentWav != null) return false;
            toolkit.setElementProperty(slotVol, "volume", gstVolume);
            if (pitchRandomization > 0.0) {
                double semitones = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * pitchRandomization;
                double speedFactor = Math.pow(2.0, semitones / 12.0);
                currentData = resamplePcm(wav, speedFactor);
                Log.tracef("[%s] slot pitch: %.3f semitones (speed=%.4f, %d→%d bytes)",
                        getChannelName(), semitones, speedFactor, wav.pcmData().length, currentData.length);
            } else {
                currentData = wav.pcmData();
            }
            currentWav = wav;
            byteOffset = 0;
            return true;
        }

        synchronized boolean isIdle() {
            return currentWav == null;
        }

        private void onNeedData(AppSrc elem, int requestedSize) {
            synchronized (this) {
                if (currentWav == null) {
                    pushSilence(elem);
                    return;
                }

                int align = currentWav.bytesPerFrame();
                int remaining = currentData.length - byteOffset;

                if (remaining <= 0) {
                    currentWav = null;
                    currentData = null;
                    pushSilence(elem);
                    return;
                }

                int reqSize = (requestedSize > 0) ? requestedSize : 4096;
                int bufSize = Math.min(remaining, reqSize);
                bufSize -= (bufSize % align);

                if (bufSize <= 0) {
                    currentWav = null;
                    currentData = null;
                    pushSilence(elem);
                    return;
                }

                Buffer buf = toolkit.createBuffer(bufSize);
                toolkit.fillBuffer(buf, currentData, byteOffset, bufSize);
                long frames = bufSize / align;
                long durationNano = (long) (frames * 1_000_000_000L / currentWav.sampleRate());
                toolkit.setDuration(buf, durationNano);
                toolkit.pushBuffer(elem, buf);
                byteOffset += bufSize;
            }
        }

        private void pushSilence(AppSrc elem) {
            Buffer silBuf = toolkit.createBuffer(silenceBuffer.length);
            toolkit.fillBuffer(silBuf, silenceBuffer, 0, silenceBuffer.length);
            toolkit.setDuration(silBuf, silenceDurationNano);
            toolkit.pushBuffer(elem, silBuf);
        }
    }
}
