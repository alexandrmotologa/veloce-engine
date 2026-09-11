package com.engine.veloce.domain.risk;

/**
 * High-frequency market volatility circuit breaker.
 * Automatically halts trading if prices move more than a defined threshold within a sliding window.
 */
public final class CircuitBreaker {

    public enum State {
        ACTIVE,
        HALTED
    }

    private final double maxPriceMovementPercent;
    private final long timeWindowNs;
    private final long haltDurationNs;

    private State state = State.ACTIVE;
    private long referencePrice;
    private long windowStartNs;
    private long haltStartNs;

    public CircuitBreaker(double maxPriceMovementPercent,
                          long timeWindowSeconds,
                          long haltDurationSeconds) {
        this.maxPriceMovementPercent = maxPriceMovementPercent;
        this.timeWindowNs = timeWindowSeconds * 1_000_000_000L;
        this.haltDurationNs = haltDurationSeconds * 1_000_000_000L;
    }

    /**
     * Inspects executed trade prices to detect extreme volatility spikes.
     */
    public synchronized void onTrade(long price, long timestampNs) {
        if (state == State.HALTED) {
            checkHaltExpiry(timestampNs);
            return;
        }

        if (referencePrice == 0 || (timestampNs - windowStartNs) > timeWindowNs) {
            referencePrice = price;
            windowStartNs = timestampNs;
            return;
        }

        double priceChangePercent = Math.abs(price - referencePrice) * 100.0 / referencePrice;
        if (priceChangePercent >= maxPriceMovementPercent) {
            state = State.HALTED;
            haltStartNs = timestampNs;
        }
    }

    public synchronized boolean isHalted(long currentTimestampNs) {
        if (state == State.HALTED) {
            checkHaltExpiry(currentTimestampNs);
        }
        return state == State.HALTED;
    }

    private void checkHaltExpiry(long currentTimestampNs) {
        if (state == State.HALTED && (currentTimestampNs - haltStartNs) >= haltDurationNs) {
            state = State.ACTIVE;
            referencePrice = 0; // reset reference on resume
        }
    }

    public synchronized void manualHalt(long timestampNs) {
        this.state = State.HALTED;
        this.haltStartNs = timestampNs;
    }

    public synchronized void manualResume() {
        this.state = State.ACTIVE;
        this.referencePrice = 0;
    }

    public State getState() {
        return state;
    }
}
