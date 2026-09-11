package com.engine.veloce.tui;

import com.engine.veloce.domain.model.Side;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Fixed-size circular buffer of recent trade executions for live terminal stream.
 */
public final class TapeWidget {

    public record TapeEntry(long tradeId, Side side, long price, long qty, long timestampNs) {}

    private final ConcurrentLinkedDeque<TapeEntry> recentTrades = new ConcurrentLinkedDeque<>();
    private final int maxEntries;

    public TapeWidget(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public void addTrade(long tradeId, Side side, long price, long qty, long timestampNs) {
        recentTrades.addFirst(new TapeEntry(tradeId, side, price, qty, timestampNs));
        while (recentTrades.size() > maxEntries) {
            recentTrades.removeLast();
        }
    }

    public ConcurrentLinkedDeque<TapeEntry> getRecentTrades() {
        return recentTrades;
    }
}
