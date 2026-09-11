package com.engine.veloce.domain.risk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CircuitBreakerTest {

    @Test
    void shouldHaltTradingWhenPriceMovementExceedsThreshold() {
        // 10% move within 5 seconds triggers a 2-second halt
        CircuitBreaker cb = new CircuitBreaker(10.0, 5, 2);

        long t0 = 1_000_000_000L; // 1 sec
        cb.onTrade(100_0000, t0); // $100.00
        assertThat(cb.isHalted(t0)).isFalse();

        // Trade at $105 (+5%): within threshold
        long t1 = t0 + 1_000_000_000L;
        cb.onTrade(105_0000, t1);
        assertThat(cb.isHalted(t1)).isFalse();

        // Trade at $112 (+12% from $100): exceeds 10% threshold!
        long t2 = t1 + 500_000_000L;
        cb.onTrade(112_0000, t2);
        assertThat(cb.isHalted(t2)).isTrue();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALTED);

        // Within halt duration (1.5 seconds later): still halted
        long t3 = t2 + 1_500_000_000L;
        assertThat(cb.isHalted(t3)).isTrue();

        // After halt duration (2.5 seconds later): resumes active
        long t4 = t2 + 2_500_000_000L;
        assertThat(cb.isHalted(t4)).isFalse();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.ACTIVE);
    }
}
