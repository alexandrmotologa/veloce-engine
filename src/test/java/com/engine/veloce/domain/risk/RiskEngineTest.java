package com.engine.veloce.domain.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RiskEngineTest {

    private CircuitBreaker circuitBreaker;
    private RiskEngine riskEngine;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker(10.0, 5, 2);
        // 5% price collar, max qty 1,000, max notional $100,000
        riskEngine = new RiskEngine(circuitBreaker, 5.0, 1000, 100_000);
    }

    @Test
    void shouldApproveValidOrder() {
        long mid = 100_0000; // $100.00
        long price = 102_0000; // $102.00 (+2% collar)
        long qty = 500; // Notional = $51,000 <= $100,000

        RiskEngine.RiskResult result = riskEngine.validateOrder(price, qty, mid, 1000);
        assertThat(result).isEqualTo(RiskEngine.RiskResult.APPROVED);
    }

    @Test
    void shouldRejectOversizedQuantity() {
        RiskEngine.RiskResult result = riskEngine.validateOrder(100_0000, 1500, 100_0000, 1000);
        assertThat(result).isEqualTo(RiskEngine.RiskResult.REJECTED_MAX_QTY);
    }

    @Test
    void shouldRejectOversizedNotional() {
        // Price $200, Qty 800 -> Notional = $160,000 > $100,000
        RiskEngine.RiskResult result = riskEngine.validateOrder(200_0000, 800, 200_0000, 1000);
        assertThat(result).isEqualTo(RiskEngine.RiskResult.REJECTED_MAX_NOTIONAL);
    }

    @Test
    void shouldRejectFatFingerPriceCollarDeviation() {
        long mid = 100_0000; // $100.00
        long fatFingerPrice = 110_0000; // $110.00 (+10% deviation, allowed is 5%)

        RiskEngine.RiskResult result = riskEngine.validateOrder(fatFingerPrice, 100, mid, 1000);
        assertThat(result).isEqualTo(RiskEngine.RiskResult.REJECTED_PRICE_COLLAR);
    }

    @Test
    void shouldRejectWhenCircuitBreakerIsHalted() {
        circuitBreaker.manualHalt(1000);

        RiskEngine.RiskResult result = riskEngine.validateOrder(100_0000, 10, 100_0000, 1000);
        assertThat(result).isEqualTo(RiskEngine.RiskResult.REJECTED_CIRCUIT_BREAKER);
    }
}
