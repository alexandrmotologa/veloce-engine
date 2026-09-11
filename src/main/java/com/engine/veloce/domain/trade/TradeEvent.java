package com.engine.veloce.domain.trade;

import com.engine.veloce.domain.model.Side;

/**
 * Reusable execution event describing a match between a maker and a taker order.
 */
public final class TradeEvent {
    private long tradeId;
    private long timestampNs;
    private long makerOrderId;
    private long takerOrderId;
    private long price;
    private long executedQty;
    private Side takerSide;
    private boolean makerFullyFilled;
    private boolean takerFullyFilled;
    private int poolIndex;

    public void set(long tradeId,
                    long timestampNs,
                    long makerOrderId,
                    long takerOrderId,
                    long price,
                    long executedQty,
                    Side takerSide,
                    boolean makerFullyFilled,
                    boolean takerFullyFilled) {
        this.tradeId = tradeId;
        this.timestampNs = timestampNs;
        this.makerOrderId = makerOrderId;
        this.takerOrderId = takerOrderId;
        this.price = price;
        this.executedQty = executedQty;
        this.takerSide = takerSide;
        this.makerFullyFilled = makerFullyFilled;
        this.takerFullyFilled = takerFullyFilled;
    }

    public void reset() {
        this.tradeId = 0;
        this.timestampNs = 0;
        this.makerOrderId = 0;
        this.takerOrderId = 0;
        this.price = 0;
        this.executedQty = 0;
        this.takerSide = null;
        this.makerFullyFilled = false;
        this.takerFullyFilled = false;
    }

    public long getTradeId() {
        return tradeId;
    }

    public long getTimestampNs() {
        return timestampNs;
    }

    public long getMakerOrderId() {
        return makerOrderId;
    }

    public long getTakerOrderId() {
        return takerOrderId;
    }

    public long getPrice() {
        return price;
    }

    public long getExecutedQty() {
        return executedQty;
    }

    public Side getTakerSide() {
        return takerSide;
    }

    public boolean isMakerFullyFilled() {
        return makerFullyFilled;
    }

    public boolean isTakerFullyFilled() {
        return takerFullyFilled;
    }

    public int getPoolIndex() {
        return poolIndex;
    }

    public void setPoolIndex(int poolIndex) {
        this.poolIndex = poolIndex;
    }
}
