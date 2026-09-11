package com.engine.veloce.telemetry;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.SingleWriterRecorder;

/**
 * High-precision latency telemetry using HdrHistogram.
 * Thread-safe single-writer recording with microsecond and nanosecond precision.
 */
public final class LatencyRecorder {

    // Range: 1 ns to 100,000,000 ns (100 ms) with 3 significant digits
    private final SingleWriterRecorder recorder = new SingleWriterRecorder(1, 100_000_000L, 3);
    private Histogram intervalHistogram;

    public void record(long latencyNs) {
        if (latencyNs > 0) {
            recorder.recordValue(Math.min(latencyNs, 100_000_000L));
        }
    }

    public synchronized void sample() {
        intervalHistogram = recorder.getIntervalHistogram(intervalHistogram);
    }

    public double getP50Micros() {
        return intervalHistogram != null ? intervalHistogram.getValueAtPercentile(50.0) / 1000.0 : 0.0;
    }

    public double getP90Micros() {
        return intervalHistogram != null ? intervalHistogram.getValueAtPercentile(90.0) / 1000.0 : 0.0;
    }

    public double getP99Micros() {
        return intervalHistogram != null ? intervalHistogram.getValueAtPercentile(99.0) / 1000.0 : 0.0;
    }

    public double getP999Micros() {
        return intervalHistogram != null ? intervalHistogram.getValueAtPercentile(99.9) / 1000.0 : 0.0;
    }

    public double getMaxMicros() {
        return intervalHistogram != null ? intervalHistogram.getMaxValue() / 1000.0 : 0.0;
    }

    public long getTotalCount() {
        return intervalHistogram != null ? intervalHistogram.getTotalCount() : 0;
    }
}
