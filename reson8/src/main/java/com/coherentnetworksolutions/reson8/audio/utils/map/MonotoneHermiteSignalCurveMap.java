package com.coherentnetworksolutions.reson8.audio.utils.map;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.analysis.solvers.BrentSolver;

import io.quarkus.logging.Log;

/**
 * Piecewise Cubic Hermite Interpolating Polynomial (PCHIP) using the
 * Fritsch-Carlson monotone derivative estimation.
 *
 * <p>Properties:
 * <ul>
 *   <li>Passes exactly through every configured control point.</li>
 *   <li><strong>Monotone-preserving</strong>: if all outputs ascend, the curve
 *       never dips between two knots (no overshoot/undershoot).</li>
 *   <li>C¹ continuous: smooth (rounded) at each knot rather than kinked.</li>
 *   <li>Works with as few as 2 points.</li>
 * </ul>
 *
 * <p>Use {@code interpolation: monotone} in application.yml.
 */
public class MonotoneHermiteSignalCurveMap implements SignalCurveMap {

    private final double[] x;
    private final double[] y;
    /** Tangent slopes at each knot, computed by Fritsch-Carlson. */
    private final double[] d;

    public MonotoneHermiteSignalCurveMap(List<Point> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("MonotoneHermiteSignalCurveMap requires at least 2 points.");
        }

        List<Point> sorted = points.stream()
                .sorted(Comparator.comparingDouble(p -> p.input()))
                .toList();

        int n = sorted.size();
        x = new double[n];
        y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = sorted.get(i).input();
            y[i] = sorted.get(i).output();
        }

        d = computePchipDerivatives(x, y);
        Log.debugf("MonotoneHermiteSignalCurveMap built: x=%s  d=%s",
                Arrays.toString(x), Arrays.toString(d));
    }

    // --- Fritsch-Carlson derivative estimation ---

    private static double[] computePchipDerivatives(double[] x, double[] y) {
        int n = x.length;
        double[] h     = new double[n - 1]; // interval widths
        double[] delta = new double[n - 1]; // secant slopes

        for (int k = 0; k < n - 1; k++) {
            h[k]     = x[k + 1] - x[k];
            delta[k] = (y[k + 1] - y[k]) / h[k];
        }

        double[] d = new double[n];

        // Endpoint slopes: one-sided secant, then guard against overshoot
        d[0]     = delta[0];
        d[n - 1] = delta[n - 2];
        endpointSafeguard(d, delta, 0,     0);
        endpointSafeguard(d, delta, n - 1, n - 2);

        // Interior slopes: weighted harmonic mean of adjacent secants
        for (int k = 1; k < n - 1; k++) {
            if (delta[k - 1] * delta[k] <= 0.0) {
                // Opposing signs or plateau → clamp to flat to preserve monotonicity
                d[k] = 0.0;
            } else {
                double w1 = 2.0 * h[k] + h[k - 1];
                double w2 = h[k] + 2.0 * h[k - 1];
                d[k] = (w1 + w2) / (w1 / delta[k - 1] + w2 / delta[k]);
            }
        }

        return d;
    }

    /** Prevent endpoint derivative from overshooting or reversing sign. */
    private static void endpointSafeguard(double[] d, double[] delta, int dIdx, int secIdx) {
        if (delta[secIdx] == 0.0) {
            d[dIdx] = 0.0;
        } else if (d[dIdx] / delta[secIdx] < 0.0) {
            d[dIdx] = 0.0;
        } else if (Math.abs(d[dIdx]) > 3.0 * Math.abs(delta[secIdx])) {
            d[dIdx] = 3.0 * delta[secIdx];
        }
    }

    // --- Cubic Hermite evaluation ---

    @Override
    public double map(double value) {
        if (value <= x[0]) return y[0];
        if (value >= x[x.length - 1]) return y[y.length - 1];

        int k = intervalIndex(value);
        double h = x[k + 1] - x[k];
        double t = (value - x[k]) / h;

        double t2 = t * t;
        double t3 = t2 * t;
        // Cubic Hermite basis
        double h00 =  2*t3 - 3*t2 + 1;
        double h10 =    t3 - 2*t2 + t;
        double h01 = -2*t3 + 3*t2;
        double h11 =    t3 -   t2;

        return h00 * y[k] + h10 * h * d[k] + h01 * y[k + 1] + h11 * h * d[k + 1];
    }

    /** Binary search for the interval index k such that x[k] <= value < x[k+1]. */
    private int intervalIndex(double value) {
        int lo = 0, hi = x.length - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (x[mid] <= value) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    @Override
    public double inverse(double value) {
        if (value <= y[0]) return x[0];
        if (value >= y[y.length - 1]) return x[x.length - 1];
        return new BrentSolver().solve(100, v -> map(v) - value, x[0], x[x.length - 1]);
    }

    @Override
    public double minInput() { return x[0]; }

    @Override
    public double maxInput() { return x[x.length - 1]; }
}
