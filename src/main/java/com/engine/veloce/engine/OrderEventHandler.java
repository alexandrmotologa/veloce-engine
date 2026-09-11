package com.engine.veloce.engine;

import com.engine.veloce.domain.book.LimitOrderBook;
import com.engine.veloce.domain.model.OrderStatus;
import com.lmax.disruptor.EventHandler;

/**
 * Single-writer event handler executing on the pinned Disruptor consumer thread.
 * Guarantees completely lock-free, single-threaded mutation of the LimitOrderBook.
 */
public final class OrderEventHandler implements EventHandler<OrderEvent> {

    private final LimitOrderBook book;
    private long processedCount;

    public OrderEventHandler(LimitOrderBook book) {
        this.book = book;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        OrderCommandType command = event.getCommandType();
        if (command == null) {
            return;
        }

        switch (command) {
            case NEW_ORDER -> {
                OrderStatus status = book.processOrder(
                        event.getOrderId(),
                        event.getSide(),
                        event.getOrderType(),
                        event.getPrice(),
                        event.getQuantity(),
                        event.getTimestampNs()
                );
                event.setResultStatus(status);
            }
            case CANCEL_ORDER -> {
                boolean canceled = book.cancelOrder(event.getOrderId());
                event.setResultStatus(canceled ? OrderStatus.CANCELED : OrderStatus.REJECTED);
            }
            case REDUCE_ORDER -> {
                boolean reduced = book.reduceOrderQty(event.getOrderId(), event.getQuantity());
                event.setResultStatus(reduced ? OrderStatus.PARTIALLY_FILLED : OrderStatus.REJECTED);
            }
            case SNAPSHOT_REQUEST -> {
                // Snapshot handled asynchronously or on-demand
            }
        }

        processedCount++;
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public LimitOrderBook getBook() {
        return book;
    }
}
