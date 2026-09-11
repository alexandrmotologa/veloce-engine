package com.engine.veloce.engine;

import com.engine.veloce.domain.book.LimitOrderBook;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.pool.OrderEntryPool;
import com.engine.veloce.domain.pool.PriceLevelPool;
import com.engine.veloce.domain.pool.TradeEventPool;
import com.engine.veloce.domain.trade.BookSnapshot;
import com.engine.veloce.domain.trade.TradeListener;
import com.lmax.disruptor.dsl.ProducerType;

/**
 * Top-level matching engine orchestrating the Disruptor ring buffer and LimitOrderBook.
 */
public final class MatchingEngine {

    private final LimitOrderBook book;
    private final OrderDisruptorRing ring;
    private final OrderEventHandler handler;

    public MatchingEngine(String symbol,
                          int orderPoolCapacity,
                          int levelPoolCapacity,
                          int tradePoolCapacity,
                          int ringBufferSize,
                          WaitStrategyType waitStrategyType,
                          ProducerType producerType) {
        OrderEntryPool orderPool = new OrderEntryPool(orderPoolCapacity);
        PriceLevelPool levelPool = new PriceLevelPool(levelPoolCapacity);
        TradeEventPool tradePool = new TradeEventPool(tradePoolCapacity);

        this.book = new LimitOrderBook(symbol, orderPool, levelPool, tradePool);
        this.handler = new OrderEventHandler(book);
        this.ring = new OrderDisruptorRing(handler, waitStrategyType, ringBufferSize, producerType);
    }

    public void setTradeListener(TradeListener listener) {
        this.book.setTradeListener(listener);
    }

    public void setWriteAheadLog(com.engine.veloce.journal.WriteAheadLog wal) {
        this.handler.setWriteAheadLog(wal);
    }

    public void start() {
        ring.start();
    }

    public void shutdown() {
        ring.shutdown();
    }

    public void submitOrder(long orderId,
                            Side side,
                            OrderType orderType,
                            long price,
                            long quantity,
                            long timestampNs) {
        ring.publishNewOrder(orderId, side, orderType, price, quantity, timestampNs);
    }

    public void cancelOrder(long orderId, long timestampNs) {
        ring.publishCancel(orderId, timestampNs);
    }

    public void reduceOrder(long orderId, long newQty, long timestampNs) {
        ring.publishReduce(orderId, newQty, timestampNs);
    }

    public void captureSnapshot(BookSnapshot snapshot, long timestampNs) {
        book.captureSnapshot(snapshot, timestampNs);
    }

    public LimitOrderBook getBook() {
        return book;
    }

    public OrderEventHandler getHandler() {
        return handler;
    }

    public OrderDisruptorRing getRing() {
        return ring;
    }

    public long getProcessedCount() {
        return handler.getProcessedCount();
    }
}
