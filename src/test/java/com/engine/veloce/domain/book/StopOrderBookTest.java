package com.engine.veloce.domain.book;

import com.engine.veloce.domain.model.OrderStatus;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.pool.OrderEntryPool;
import com.engine.veloce.domain.pool.PriceLevelPool;
import com.engine.veloce.domain.pool.TradeEventPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StopOrderBookTest {

    private LimitOrderBook book;

    @BeforeEach
    void setUp() {
        OrderEntryPool orderPool = new OrderEntryPool(1000);
        PriceLevelPool levelPool = new PriceLevelPool(100);
        TradeEventPool tradePool = new TradeEventPool(100);
        book = new LimitOrderBook("AAPL", orderPool, levelPool, tradePool);
    }

    @Test
    void shouldRestInStopBookUntilTriggerPriceIsHit() {
        // Place a Stop-Loss sell order: triggers when last trade drops to or below 9900 ($99.00)
        OrderStatus s1 = book.processOrder(100, 1, Side.ASK, OrderType.LIMIT, 9800, 50, 50, 9900, 1000);
        assertThat(s1).isEqualTo(OrderStatus.NEW);
        assertThat(book.getStopOrderBook().getRestingCount()).isEqualTo(1);
        assertThat(book.getActiveOrderCount()).isEqualTo(0);
        assertThat(book.getBestAsk()).isNull();

        // Trade occurs at $100.00: stop is not triggered
        book.processOrder(1, Side.BID, OrderType.LIMIT, 10000, 10, 1001);
        book.processOrder(2, Side.ASK, OrderType.LIMIT, 10000, 10, 1002);
        assertThat(book.getLastTradedPrice()).isEqualTo(10000);
        assertThat(book.getStopOrderBook().getRestingCount()).isEqualTo(1);
        assertThat(book.getOrder(100)).isNull();

        // Trade occurs at $98.50: triggers Stop-Loss order 100!
        book.processOrder(3, Side.BID, OrderType.LIMIT, 9850, 10, 1003);
        book.processOrder(4, Side.ASK, OrderType.LIMIT, 9850, 10, 1004);

        assertThat(book.getLastTradedPrice()).isEqualTo(9850);
        // Stop order was triggered and promoted into main book
        assertThat(book.getStopOrderBook().getRestingCount()).isEqualTo(0);
        assertThat(book.getOrder(100)).isNotNull();
        assertThat(book.getBestAsk().getPrice()).isEqualTo(9800);
        assertThat(book.getBestAsk().getTotalVolume()).isEqualTo(50);
    }
}
