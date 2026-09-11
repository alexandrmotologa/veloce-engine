package com.engine.veloce.domain.pool;

import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.trade.TradeEvent;

/**
 * Pre-allocated pool of TradeEvent objects.
 */
public final class TradeEventPool {
    private final TradeEvent[] events;
    private final int[] freeIndices;
    private final int capacity;
    private int top;

    public TradeEventPool(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.events = new TradeEvent[capacity];
        this.freeIndices = new int[capacity];
        this.top = capacity - 1;

        for (int i = 0; i < capacity; i++) {
            events[i] = new TradeEvent();
            events[i].setPoolIndex(i);
            freeIndices[i] = i;
        }
    }

    public TradeEvent acquire(long tradeId,
                              long timestampNs,
                              long makerOrderId,
                              long takerOrderId,
                              long price,
                              long executedQty,
                              Side takerSide,
                              boolean makerFullyFilled,
                              boolean takerFullyFilled) {
        if (top < 0) {
            return null;
        }
        int index = freeIndices[top--];
        TradeEvent event = events[index];
        event.set(tradeId, timestampNs, makerOrderId, takerOrderId, price, executedQty,
                takerSide, makerFullyFilled, takerFullyFilled);
        return event;
    }

    public void release(TradeEvent event) {
        if (event == null) {
            return;
        }
        int poolIndex = event.getPoolIndex();
        event.reset();
        event.setPoolIndex(poolIndex);
        freeIndices[++top] = poolIndex;
    }

    public int available() {
        return top + 1;
    }

    public int getCapacity() {
        return capacity;
    }
}
