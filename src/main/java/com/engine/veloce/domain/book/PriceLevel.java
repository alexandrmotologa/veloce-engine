package com.engine.veloce.domain.book;

/**
 * Represents a price point in the order book, holding an intrusive FIFO queue
 * of resting OrderEntry nodes.
 */
public final class PriceLevel {
    private long price;
    private long totalVolume;
    private int orderCount;

    private OrderEntry head;
    private OrderEntry tail;

    // Intrusive links for sorted PriceLevel chain in LimitOrderBook
    PriceLevel prev;
    PriceLevel next;

    private int poolIndex;

    public void init(long price, int poolIndex) {
        this.price = price;
        this.poolIndex = poolIndex;
        this.totalVolume = 0;
        this.orderCount = 0;
        this.head = null;
        this.tail = null;
        this.prev = null;
        this.next = null;
    }

    public void reset() {
        this.price = 0;
        this.totalVolume = 0;
        this.orderCount = 0;
        this.head = null;
        this.tail = null;
        this.prev = null;
        this.next = null;
    }

    /**
     * Appends an order to the tail of this price level (FIFO priority) in O(1) time.
     */
    public void append(OrderEntry order) {
        order.parentLevel = this;
        order.prev = tail;
        order.next = null;

        if (tail != null) {
            tail.next = order;
        } else {
            head = order;
        }
        tail = order;

        orderCount++;
        totalVolume += order.getRemainingQty();
    }

    /**
     * Removes an arbitrary order from this price level in O(1) time using intrusive links.
     */
    public void remove(OrderEntry order) {
        if (order.parentLevel != this) {
            return;
        }

        if (order.prev != null) {
            order.prev.next = order.next;
        } else {
            head = order.next;
        }

        if (order.next != null) {
            order.next.prev = order.prev;
        } else {
            tail = order.prev;
        }

        orderCount--;
        totalVolume -= order.getRemainingQty();

        order.prev = null;
        order.next = null;
        order.setParentLevel(null);
    }

    /**
     * Removes and returns the order at the head of the queue in O(1) time.
     */
    public OrderEntry poll() {
        if (head == null) {
            return null;
        }
        OrderEntry order = head;
        remove(order);
        return order;
    }

    public void reduceVolume(long delta) {
        this.totalVolume -= delta;
    }

    public boolean isEmpty() {
        return orderCount == 0;
    }

    public long getPrice() {
        return price;
    }

    public long getTotalVolume() {
        return totalVolume;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public OrderEntry getHead() {
        return head;
    }

    public OrderEntry getTail() {
        return tail;
    }

    public PriceLevel getPrev() {
        return prev;
    }

    public PriceLevel getNext() {
        return next;
    }

    public int getPoolIndex() {
        return poolIndex;
    }
}
