package com.engine.veloce.domain.pool;

import com.engine.veloce.domain.book.PriceLevel;

/**
 * Pre-allocated pool of PriceLevel objects.
 */
public final class PriceLevelPool {
    private final PriceLevel[] levels;
    private final int[] freeIndices;
    private final int capacity;
    private int top;

    public PriceLevelPool(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.levels = new PriceLevel[capacity];
        this.freeIndices = new int[capacity];
        this.top = capacity - 1;

        for (int i = 0; i < capacity; i++) {
            levels[i] = new PriceLevel();
            levels[i].init(0, i);
            freeIndices[i] = i;
        }
    }

    /**
     * Acquires a PriceLevel for the specified price in O(1) time.
     *
     * @param price the price point
     * @return initialized PriceLevel, or null if the pool is exhausted
     */
    public PriceLevel acquire(long price) {
        if (top < 0) {
            return null;
        }
        int index = freeIndices[top--];
        PriceLevel level = levels[index];
        level.init(price, index);
        return level;
    }

    /**
     * Releases a PriceLevel back into the pool in O(1) time.
     *
     * @param level the price level to recycle
     */
    public void release(PriceLevel level) {
        if (level == null) {
            return;
        }
        int index = level.getPoolIndex();
        level.reset();
        freeIndices[++top] = index;
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
