package com.engine.veloce.domain.trade;

import java.util.Arrays;

/**
 * Pre-allocated snapshot container for L2 order book depth.
 */
public final class BookSnapshot {
    public static final int MAX_DEPTH = 20;

    private final long[] bidPrices = new long[MAX_DEPTH];
    private final long[] bidVolumes = new long[MAX_DEPTH];
    private int bidCount;

    private final long[] askPrices = new long[MAX_DEPTH];
    private final long[] askVolumes = new long[MAX_DEPTH];
    private int askCount;

    private long timestampNs;

    public void reset() {
        this.bidCount = 0;
        this.askCount = 0;
        this.timestampNs = 0;
        Arrays.fill(bidPrices, 0);
        Arrays.fill(bidVolumes, 0);
        Arrays.fill(askPrices, 0);
        Arrays.fill(askVolumes, 0);
    }

    public void addBid(long price, long volume) {
        if (bidCount < MAX_DEPTH) {
            bidPrices[bidCount] = price;
            bidVolumes[bidCount] = volume;
            bidCount++;
        }
    }

    public void addAsk(long price, long volume) {
        if (askCount < MAX_DEPTH) {
            askPrices[askCount] = price;
            askVolumes[askCount] = volume;
            askCount++;
        }
    }

    public void setTimestampNs(long timestampNs) {
        this.timestampNs = timestampNs;
    }

    public int getBidCount() {
        return bidCount;
    }

    public int getAskCount() {
        return askCount;
    }

    public long getBidPrice(int index) {
        return bidPrices[index];
    }

    public long getBidVolume(int index) {
        return bidVolumes[index];
    }

    public long getAskPrice(int index) {
        return askPrices[index];
    }

    public long getAskVolume(int index) {
        return askVolumes[index];
    }

    public long getTimestampNs() {
        return timestampNs;
    }

    public long getBestBidPrice() {
        return bidCount > 0 ? bidPrices[0] : 0;
    }

    public long getBestAskPrice() {
        return askCount > 0 ? askPrices[0] : 0;
    }

    public long getSpread() {
        if (bidCount > 0 && askCount > 0) {
            return askPrices[0] - bidPrices[0];
        }
        return 0;
    }
}
