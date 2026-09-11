package com.engine.veloce.domain.book;

import com.engine.veloce.domain.model.OrderStatus;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.pool.OrderEntryPool;
import com.engine.veloce.domain.pool.PriceLevelPool;
import com.engine.veloce.domain.pool.TradeEventPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class IcebergOrderTest {

    private LimitOrderBook book;
    private AtomicInteger tradeCount;

    @BeforeEach
    void setUp() {
        OrderEntryPool orderPool = new OrderEntryPool(1000);
        PriceLevelPool levelPool = new PriceLevelPool(100);
        TradeEventPool tradePool = new TradeEventPool(100);
        book = new LimitOrderBook("AAPL", orderPool, levelPool, tradePool);
        tradeCount = new AtomicInteger();
        book.setTradeListener(t -> tradeCount.incrementAndGet());
    }

    @Test
    void shouldDisplayOnlyPeakVolumeAndReplenishWhenFilled() {
        // Place an Iceberg limit bid: 100 total shares, display peak of 25 shares at $100
        OrderStatus s1 = book.processOrder(1, 10, Side.BID, OrderType.LIMIT, 10000, 100, 25, 0, 1000);
        assertThat(s1).isEqualTo(OrderStatus.NEW);

        // Only 25 shares should be visible in the public price level
        assertThat(book.getBestBid().getTotalVolume()).isEqualTo(25);
        assertThat(book.getOrder(1).getRemainingQty()).isEqualTo(25);
        assertThat(book.getOrder(1).getHiddenQty()).isEqualTo(75);

        // Incoming sell of 25 shares fills visible peak
        OrderStatus s2 = book.processOrder(2, 20, Side.ASK, OrderType.LIMIT, 10000, 25, 25, 0, 1001);
        assertThat(s2).isEqualTo(OrderStatus.FILLED);
        assertThat(tradeCount.get()).isEqualTo(1);

        // Order 1 should automatically replenish next 25 shares from hidden pool
        assertThat(book.getOrder(1)).isNotNull();
        assertThat(book.getBestBid().getTotalVolume()).isEqualTo(25);
        assertThat(book.getOrder(1).getRemainingQty()).isEqualTo(25);
        assertThat(book.getOrder(1).getHiddenQty()).isEqualTo(50);
    }

    @Test
    void shouldMoveReplenishedSliceToTailOfPriceLevel() {
        // Iceberg Order 1: 50 shares, 10 display
        book.processOrder(1, 10, Side.BID, OrderType.LIMIT, 10000, 50, 10, 0, 1000);

        // Regular Order 2: 20 shares at same price
        book.processOrder(2, 20, Side.BID, OrderType.LIMIT, 10000, 20, 20, 0, 1001);

        // At level: Order 1 (10) -> Order 2 (20). Total volume = 30
        assertThat(book.getBestBid().getTotalVolume()).isEqualTo(30);
        assertThat(book.getBestBid().getHead().getOrderId()).isEqualTo(1);

        // Sell 10 shares: fills Order 1 display (10 shares).
        // Order 1 replenishes 10 shares and moves to tail behind Order 2!
        book.processOrder(3, 30, Side.ASK, OrderType.LIMIT, 10000, 10, 10, 0, 1002);

        assertThat(book.getBestBid().getHead().getOrderId()).isEqualTo(2);
        assertThat(book.getBestBid().getTail().getOrderId()).isEqualTo(1);
    }
}
