package com.coherentnetworksolutions.reson8.audio.input;

import org.freedesktop.gstreamer.Caps;

import io.quarkus.logging.Log;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Shared {@link InputChannel} state: identity, target/current intensity in 0–100 space.
 * <p>
 * Smoothing and per-tick modulation are opt-in: subclasses call
 * {@link #stepSmoothTowardTarget(double, double)} from a scheduler (or nowhere) and may
 * override {@link #onTargetIntensityChanged(double)} for immediate side effects. Defaults
 * are no-ops beyond storing a clamped target.
 */
public abstract class BaseInputChannel implements InputChannel {

    protected final String channelName;

    private final Object intensityLock = new Object();
    private double targetIntensity = 0.0;
    private double currentIntensity = 0.0;
    private double ceiling = 100.0;

    /**
     * @param channelName          stable channel id
     * @param initialTargetPercent initial {@link #getTargetIntensity()} (0–100)
     * @param initialCurrentPercent initial {@link #getCurrentIntensity()} (0–100), e.g. 0 for ramp-up
     */
    protected BaseInputChannel(String channelName, double initialTargetPercent, double initialCurrentPercent) {
        this.channelName = channelName;
        synchronized (intensityLock) {
            this.targetIntensity = clampIntensity(initialTargetPercent);
            this.currentIntensity = clampIntensity(initialCurrentPercent);
        }
    }

    /** Clamp to the public intensity contract (0–100). */
    protected static double clampIntensity(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, d));
    }

    @Override
    public final String getChannelName() {
        return channelName;
    }

    /**
     * Initialises the ceiling from a config-supplied value (0–100). Call from a
     * subclass constructor after {@code super(...)}.
     */
    protected final void initCeiling(double configuredCeiling) {
        synchronized (intensityLock) {
            this.ceiling = clampIntensity(configuredCeiling);
        }
    }

    @Override
    public double getCeiling() {
        synchronized (intensityLock) {
            return ceiling;
        }
    }

    @Override
    public void setCeiling(@Min(0) @Max(100) double newCeiling) {
        synchronized (intensityLock) {
            this.ceiling = clampIntensity(newCeiling);
        }
    }

    @Override
    public void setTargetIntensity(@Min(0) @Max(100) double intensityPercent) {
        Log.debugf("setIntensity([%s]) on [%s]", intensityPercent, getChannelName());
        double clamped = clampIntensity(intensityPercent);
        synchronized (intensityLock) {
            this.targetIntensity = clamped;
        }
        onTargetIntensityChanged(clamped);
    }

    /**
     * Called after {@link #setTargetIntensity(double)} stores a clamped value. Default is
     * no-op (smooth channels update audio from the scheduler; instant channels may snap
     * {@link #currentIntensity} here).
     */
    protected void onTargetIntensityChanged(double clampedTarget) {
        // default no-op
    }

    @Override
    public final double getTargetIntensity() {
        synchronized (intensityLock) {
            return targetIntensity;
        }
    }

    @Override
    public final double getCurrentIntensity() {
        synchronized (intensityLock) {
            return currentIntensity;
        }
    }

    @Override
    public abstract Caps getCaps();

    /**
     * Directly set the reported current intensity (0–100), e.g. after custom physics.
     * Prefer {@link #stepSmoothTowardTarget(double, double)} for eased motion toward target.
     */
    protected final void setCurrentIntensityPercent(double percent) {
        synchronized (intensityLock) {
            this.currentIntensity = clampIntensity(percent);
        }
    }

    /**
     * Exponential move of current toward target: {@code current += (target - current) * rate}.
     * Use from a periodic tick. With {@code rate <= 0}, does nothing.
     */
    protected final void stepSmoothTowardTarget(double rate, double epsilon) {
        if (rate <= 0.0) {
            return;
        }
        synchronized (intensityLock) {
            double t = targetIntensity;
            double c = currentIntensity;
            if (Math.abs(c - t) <= epsilon) {
                currentIntensity = t;
            } else {
                currentIntensity = c + (t - c) * rate;
            }
        }
    }
}
