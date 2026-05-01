package com.coherentnetworksolutions.reson8.audio.utils.map;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.analysis.solvers.UnivariateSolver;


import io.quarkus.logging.Log;

public class SplineSignalCurveMap implements SignalCurveMap {

    private final PolynomialSplineFunction spline;
    private boolean descendingOut;
    private double minIn;
    private double maxIn;
    private double minOut;
    private double maxOut;

    public SplineSignalCurveMap(List<Point> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("At least 2 points are required for spline mapping.");
        }
        if (points.get(0).input() >= points.get(points.size() - 1).input()) {
            throw new IllegalArgumentException("Input values must be in strictly increasing order.");
        }

        Log.debugf("Constructing SplineSignalMapper with points: %s", points);

        List<Point> sorted = preparePoints(points);

        Log.debugf("Prepared points for SplineSignalMapper: %s", sorted);

        // 3. Interpolate ONCE
        double[] x = sorted.stream().mapToDouble(p -> p.input()).toArray();
        double[] y = sorted.stream().mapToDouble(p -> p.output()).toArray();

        Log.debugf("X values for spline: %s", java.util.Arrays.toString(x));
        Log.debugf("Y values for spline: %s", java.util.Arrays.toString(y));

        spline = new SplineInterpolator().interpolate(x, y);

        // inputs always go up
        minIn = x[0];
        maxIn = x[x.length - 1];

        if (y[0] > y[y.length - 1]) {
            descendingOut = true;
        } else {
            descendingOut = false;
        }

        //outputs may be increasing or decreasing, so we find min/max for clamping
        if (descendingOut){
            Log.debugf("Output is descending, flipping min/max for clamping");
            minOut = Math.max(y[0], y[y.length - 1]);
            maxOut = Math.min(y[0], y[y.length - 1]);
        } else {
            minOut = Math.min(y[0], y[y.length - 1]);
            maxOut = Math.max(y[0], y[y.length - 1]);
        }
        Log.debugf("SplineSignalMapper input range: [%f, %f]", minIn, maxIn);
        Log.debugf("SplineSignalMapper output range: [%f, %f]", minOut, maxOut);
        
    }


    private List<Point> preparePoints(List<Point> points) {
        List<Point> mutable = new ArrayList<>(points);

        // Sort ascending
        mutable.sort((a, b) -> Double.compare(a.input(), b.input()));

        if (mutable.get(0).input() != 0.0) {
            mutable.add(0, new Point(0.0, 0.0));
            Log.debugf("Added (0,0) to the beginning of points for Spline");
        }
        
        return mutable;
    }

    /**
     * Maps an input value to an output value using the spline interpolation.

     */
    @Override
    public double map(double value) {
        Log.debugf("Mapping input value: %f", value);

        if (value <= minIn){
            Log.debugf("Input below minIn (%f <= %f), returning boundary output: %f", value, minIn,minOut );
            return minOut;
        }
        if (value >= maxIn) {
            Log.debugf("Input above maxIn (%f >= %f), returning boundary output: %f", value, maxIn, minOut);  
            return maxOut;
        }
        
        double result = spline.value(value);
        Log.debugf("Spline output before clamping: %f", result);

        if (descendingOut) {
            Log.debug("Output is descending, flipping result for clamping");
            result = Math.min(minOut, Math.max(maxOut, result));
        } else {
            result = Math.max(minOut, Math.min(maxOut, result));
        }
        return result;

    }

    /** Returns the raw spline value without clamping, for diagnostic/visualisation use only. */
    @Override
    public double mapUnclamped(double value) {
        if (value <= minIn) return spline.value(minIn);
        if (value >= maxIn) return spline.value(maxIn);
        return spline.value(value);
    }

    @Override
    public double minInput() {
        return minIn;
    }

    @Override
    public double maxInput() {
        return maxIn;
    }

    @Override
    public double inverse(double value) {
        if (value <= minOut || value >= maxOut) {
            Log.debugf("Inverse input value %f is out of output range [%f, %f], returning boundary input", value, minOut, maxOut);
            return value <= minOut ? minIn : maxIn;
        }
        UnivariateSolver solver = new BrentSolver();
        double searchMin = minIn;
        double searchMax = maxIn;
        double root = solver.solve(100, x -> spline.value(x) - value, searchMin, searchMax);
        Log.debugf("Inverse mapping for output value %f found input: %f", value, root);
        return root;
    }   

}
