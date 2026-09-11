package com.engine.veloce.domain.pool;

import com.engine.veloce.domain.book.OrderEntry;

/**
 * High-speed, bounded, pre-allocated pool of OrderEntry nodes.
 * Guarantees zero garbage collection allocations during order lifecycle.
 */
public final class OrderEntryPool {
    private final OrderEntry[] entries;
    private final int[] freeIndices;
    private final int capacity;
    private int top;

    public OrderEntryPool(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.entries = new OrderEntry[capacity];
        this.freeIndices = new int[capacity];
        this.top = capacity - 1;

        for (int i = 0; i < capacity; i++) {
            entries[i] = new OrderEntry();
            entries[i].setPoolIndex(i);
            freeIndices[i] = i;
        }
    }

    /**
     * Acquires an unused OrderEntry in O(1) time without heap allocations.
     *
     * @return pre-allocated OrderEntry, or null if the pool is exhausted.
     */
    public OrderEntry acquire() {
        if (top < 0) {
            return null;
        }
        int index = freeIndices[top--];
        return entries[index];
    }

    /**
     * Releases an OrderEntry back to the pool in O(1) time.
     *
     * @param entry order entry to recycle
     */
    public void release(OrderEntry entry) {
        if (entry == null) {
            return;
        }
        int poolIndex = entry.getPoolIndex();
        entry.reset();
        entry.setPoolIndex(poolIndex);
        freeIndices[++top] = poolIndex;
    }

    public int available() {
        return top + 1;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isExhausted() {
        return top < 0;
    }
}
