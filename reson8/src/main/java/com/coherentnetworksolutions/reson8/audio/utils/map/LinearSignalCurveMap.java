package com.coherentnetworksolutions.reson8.audio.utils.map;

import java.util.List;

public class LinearSignalCurveMap implements SignalCurveMap {

    private final double slope;
    private final double intercept;
    private final double minInput;
    private final double maxInput;

    public LinearSignalCurveMap(List<Point> points) {
        if (points == null || points.size() != 2) {
            throw new IllegalArgumentException("LinearSignalMapper requires exactly two points.");
        }

        Point p0 = points.get(0);
        Point p1 = points.get(1);

        if (Double.compare(p0.input(), p1.input()) == 0) {
            throw new IllegalArgumentException("Input values must differ between the two points.");
        }

        this.slope = (p1.output() - p0.output()) / (p1.input() - p0.input());
        this.intercept = p0.output() - slope * p0.input();
        this.minInput = Math.min(p0.input(), p1.input());
        this.maxInput = Math.max(p0.input(), p1.input());
    }

    @Override
    public double map(double value) {
        return slope * value + intercept;
    }

    @Override
    public double inverse(double value) {
        if (Double.compare(slope, 0.0) == 0) {
            throw new IllegalStateException("Slope cannot be zero for inverse mapping.");
        }
        return (value - intercept) / slope;
    }

    @Override
    public double minInput() {
        return minInput;
    }

    @Override
    public double maxInput() {
        return maxInput;
    }

}
