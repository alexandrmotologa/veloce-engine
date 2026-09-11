package com.engine.veloce.domain.trade;

/**
 * High-frequency callback interface for matched trade events.
 */
@FunctionalInterface
public interface TradeListener {
    /**
     * Invoked when a trade match occurs on the single-writer thread.
     *
     * @param trade the trade event details
     */
    void onTrade(TradeEvent trade);
}
