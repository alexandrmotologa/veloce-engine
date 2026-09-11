package com.engine.veloce.domain.book;

import com.engine.veloce.domain.model.OrderStatus;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.SelfTradePreventionMode;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.pool.OrderEntryPool;
import com.engine.veloce.domain.pool.PriceLevelPool;
import com.engine.veloce.domain.pool.TradeEventPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SelfTradePreventionTest {

    private LimitOrderBook book;
    private AtomicInteger tradesCount;

    @BeforeEach
    void setUp() {
        OrderEntryPool orderPool = new OrderEntryPool(1000);
        PriceLevelPool levelPool = new PriceLevelPool(100);
        TradeEventPool tradePool = new TradeEventPool(100);
        book = new LimitOrderBook("AAPL", orderPool, levelPool, tradePool);
        tradesCount = new AtomicInteger();
        book.setTradeListener(trade -> tradesCount.incrementAndGet());
    }

    @Test
    void shouldAllowSelfTradeWhenModeIsNone() {
        book.setSelfTradePreventionMode(SelfTradePreventionMode.NONE);

        // Participant 42 places resting bid
        book.processOrder(1, 42, Side.BID, OrderType.LIMIT, 10000, 10, 10, 0, 1000);
        // Participant 42 places crossing ask
        OrderStatus status = book.processOrder(2, 42, Side.ASK, OrderType.LIMIT, 10000, 10, 10, 0, 1001);

        assertThat(status).isEqualTo(OrderStatus.FILLED);
        assertThat(tradesCount.get()).isEqualTo(1);
    }

    @Test
    void shouldCancelNewestOrderOnSelfTrade() {
        book.setSelfTradePreventionMode(SelfTradePreventionMode.CANCEL_NEWEST);

        // Participant 42 places resting bid
        book.processOrder(1, 42, Side.BID, OrderType.LIMIT, 10000, 10, 10, 0, 1000);
        // Participant 42 places crossing ask
        OrderStatus status = book.processOrder(2, 42, Side.ASK, OrderType.LIMIT, 10000, 10, 10, 0, 1001);

        assertThat(status).isEqualTo(OrderStatus.CANCELED);
        assertThat(tradesCount.get()).isEqualTo(0);
        // Resting order 1 remains in the book
        assertThat(book.getOrder(1)).isNotNull();
    }

    @Test
    void shouldCancelOldestOrderOnSelfTrade() {
        book.setSelfTradePreventionMode(SelfTradePreventionMode.CANCEL_OLDEST);

        // Participant 42 places resting bid
        book.processOrder(1, 42, Side.BID, OrderType.LIMIT, 10000, 10, 10, 0, 1000);
        // Participant 42 places crossing ask
        OrderStatus status = book.processOrder(2, 42, Side.ASK, OrderType.LIMIT, 10000, 10, 10, 0, 1001);

        assertThat(tradesCount.get()).isEqualTo(0);
        // Resting order 1 is canceled
        assertThat(book.getOrder(1)).isNull();
        // Incoming order 2 is placed in book
        assertThat(book.getOrder(2)).isNotNull();
        assertThat(status).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void shouldDecrementAndCancelOnSelfTrade() {
        book.setSelfTradePreventionMode(SelfTradePreventionMode.DECREMENT_AND_CANCEL);

        // Participant 42 places resting bid for 15 shares
        book.processOrder(1, 42, Side.BID, OrderType.LIMIT, 10000, 15, 15, 0, 1000);
        // Participant 42 places crossing ask for 10 shares
        OrderStatus status = book.processOrder(2, 42, Side.ASK, OrderType.LIMIT, 10000, 10, 10, 0, 1001);

        assertThat(tradesCount.get()).isEqualTo(0);
        // Incoming order (10 shares) is canceled
        assertThat(status).isEqualTo(OrderStatus.CANCELED);
        // Resting order is decremented by 10 shares, 5 remain
        assertThat(book.getOrder(1)).isNotNull();
        assertThat(book.getOrder(1).getRemainingQty()).isEqualTo(5);
    }
}
