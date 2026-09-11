package com.engine.veloce.domain.risk;

/**
 * Pre-trade risk validation engine.
 * Protects the market against fat-finger errors, oversized orders, and extreme volatility.
 */
public final class RiskEngine {

    public enum RiskResult {
        APPROVED,
        REJECTED_CIRCUIT_BREAKER,
        REJECTED_PRICE_COLLAR,
        REJECTED_MAX_QTY,
        REJECTED_MAX_NOTIONAL
    }

    private final CircuitBreaker circuitBreaker;
    private final double maxPriceCollarPercent;
    private final long maxOrderQty;
    private final long maxNotional;

    public RiskEngine(CircuitBreaker circuitBreaker,
                      double maxPriceCollarPercent,
                      long maxOrderQty,
                      long maxNotional) {
        this.circuitBreaker = circuitBreaker;
        this.maxPriceCollarPercent = maxPriceCollarPercent;
        this.maxOrderQty = maxOrderQty;
        this.maxNotional = maxNotional;
    }

    /**
     * Evaluates pre-trade risk invariants before an order can enter the matching pipeline.
     */
    public RiskResult validateOrder(long price, long qty, long midPrice, long timestampNs) {
        // 1. Circuit breaker check
        if (circuitBreaker != null && circuitBreaker.isHalted(timestampNs)) {
            return RiskResult.REJECTED_CIRCUIT_BREAKER;
        }

        // 2. Maximum quantity limit check
        if (maxOrderQty > 0 && qty > maxOrderQty) {
            return RiskResult.REJECTED_MAX_QTY;
        }

        // 3. Maximum notional check: price is scaled by 10,000, so notional = (price * qty) / 10,000
        if (maxNotional > 0 && price > 0) {
            long notional = (price * qty) / 10_000L;
            if (notional > maxNotional) {
                return RiskResult.REJECTED_MAX_NOTIONAL;
            }
        }

        // 4. Fat-finger price collar check against current mid price
        if (maxPriceCollarPercent > 0 && midPrice > 0 && price > 0) {
            double deviationPercent = Math.abs(price - midPrice) * 100.0 / midPrice;
            if (deviationPercent > maxPriceCollarPercent) {
                return RiskResult.REJECTED_PRICE_COLLAR;
            }
        }

        return RiskResult.APPROVED;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
