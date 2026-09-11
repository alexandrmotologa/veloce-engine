package com.engine.veloce.domain.book;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;

/**
 * Intrusive doubly-linked list node representing an order resting in the book.
 * Pre-allocated inside OrderEntryPool to avoid heap allocations.
 */
public final class OrderEntry {
    private long orderId;
    private long participantId;
    private long price;
    private long remainingQty;
    private long initialQty;
    private long initialDisplayQty;
    private long hiddenQty;
    private long timestampNs;
    private Side side;
    private OrderType orderType;
    PriceLevel parentLevel;

    // Intrusive doubly-linked list pointers within the PriceLevel FIFO queue
    OrderEntry prev;
    OrderEntry next;

    // Index position within the pre-allocated pool
    private int poolIndex;

    public void init(long orderId,
                     long price,
                     long initialQty,
                     long timestampNs,
                     Side side,
                     OrderType orderType,
                     PriceLevel parentLevel,
                     int poolIndex) {
        init(orderId, 0, price, initialQty, initialQty, 0, timestampNs, side, orderType, parentLevel, poolIndex);
    }

    public void init(long orderId,
                     long participantId,
                     long price,
                     long initialQty,
                     long displayQty,
                     long hiddenQty,
                     long timestampNs,
                     Side side,
                     OrderType orderType,
                     PriceLevel parentLevel,
                     int poolIndex) {
        this.orderId = orderId;
        this.participantId = participantId;
        this.price = price;
        this.initialQty = initialQty;
        this.remainingQty = displayQty;
        this.initialDisplayQty = displayQty;
        this.hiddenQty = hiddenQty;
        this.timestampNs = timestampNs;
        this.side = side;
        this.orderType = orderType;
        this.parentLevel = parentLevel;
        this.poolIndex = poolIndex;
        this.prev = null;
        this.next = null;
    }

    public void reset() {
        this.orderId = 0;
        this.participantId = 0;
        this.price = 0;
        this.remainingQty = 0;
        this.initialQty = 0;
        this.initialDisplayQty = 0;
        this.hiddenQty = 0;
        this.timestampNs = 0;
        this.side = null;
        this.orderType = null;
        this.parentLevel = null;
        this.prev = null;
        this.next = null;
    }

    public boolean isIceberg() {
        return hiddenQty > 0 || initialDisplayQty < initialQty;
    }

    public long replenish() {
        if (hiddenQty <= 0) {
            return 0;
        }
        long replenishAmount = Math.min(initialDisplayQty, hiddenQty);
        this.remainingQty = replenishAmount;
        this.hiddenQty -= replenishAmount;
        return replenishAmount;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getPrice() {
        return price;
    }

    public long getRemainingQty() {
        return remainingQty;
    }

    public long getInitialQty() {
        return initialQty;
    }

    public long getTimestampNs() {
        return timestampNs;
    }

    public Side getSide() {
        return side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public PriceLevel getParentLevel() {
        return parentLevel;
    }

    public void setParentLevel(PriceLevel parentLevel) {
        this.parentLevel = parentLevel;
    }

    public int getPoolIndex() {
        return poolIndex;
    }

    public void setPoolIndex(int poolIndex) {
        this.poolIndex = poolIndex;
    }

    public OrderEntry getPrev() {
        return prev;
    }

    public OrderEntry getNext() {
        return next;
    }

    public boolean isFilled() {
        return remainingQty <= 0;
    }

    public void reduceQty(long delta) {
        if (delta > remainingQty) {
            throw new IllegalArgumentException("Cannot reduce by " + delta + ", remaining is " + remainingQty);
        }
        this.remainingQty -= delta;
    }

    public void setRemainingQty(long remainingQty) {
        this.remainingQty = remainingQty;
    }

    public long getParticipantId() {
        return participantId;
    }

    public long getHiddenQty() {
        return hiddenQty;
    }

    public long getInitialDisplayQty() {
        return initialDisplayQty;
    }
}
