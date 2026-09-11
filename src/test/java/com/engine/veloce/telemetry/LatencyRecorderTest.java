package com.engine.veloce.telemetry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LatencyRecorderTest {

    @Test
    void shouldRecordAndComputePercentiles() {
        LatencyRecorder recorder = new LatencyRecorder();

        // Record 1,000 values from 100ns to 100,000ns
        for (int i = 1; i <= 1000; i++) {
            recorder.record(i * 100L);
        }

        recorder.sample();

        assertThat(recorder.getTotalCount()).isEqualTo(1000);
        assertThat(recorder.getP50Micros()).isGreaterThan(0.0);
        assertThat(recorder.getP99Micros()).isGreaterThanOrEqualTo(recorder.getP50Micros());
        assertThat(recorder.getMaxMicros()).isGreaterThanOrEqualTo(recorder.getP99Micros());
    }
}
