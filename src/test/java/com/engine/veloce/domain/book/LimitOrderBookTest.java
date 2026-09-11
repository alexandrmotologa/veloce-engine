package com.engine.veloce.domain.book;

import com.engine.veloce.domain.model.OrderStatus;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.pool.OrderEntryPool;
import com.engine.veloce.domain.pool.PriceLevelPool;
import com.engine.veloce.domain.pool.TradeEventPool;
import com.engine.veloce.domain.trade.BookSnapshot;
import com.engine.veloce.domain.trade.TradeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LimitOrderBookTest {

    private LimitOrderBook book;
    private List<TradeInfo> executedTrades;

    record TradeInfo(long makerOrderId, long takerOrderId, long price, long qty) {}

    @BeforeEach
    void setUp() {
        OrderEntryPool orderPool = new OrderEntryPool(1000);
        PriceLevelPool levelPool = new PriceLevelPool(100);
        TradeEventPool tradePool = new TradeEventPool(100);

        book = new LimitOrderBook("AAPL", orderPool, levelPool, tradePool);
        executedTrades = new ArrayList<>();

        book.setTradeListener((TradeEvent trade) -> {
            executedTrades.add(new TradeInfo(
                    trade.getMakerOrderId(),
                    trade.getTakerOrderId(),
                    trade.getPrice(),
                    trade.getExecutedQty()
            ));
        });
    }

    @Test
    void shouldRestPassiveOrdersInBook() {
        OrderStatus s1 = book.processOrder(1, Side.BID, OrderType.LIMIT, 10000, 10, 1000);
        OrderStatus s2 = book.processOrder(2, Side.ASK, OrderType.LIMIT, 10100, 20, 1001);

        assertThat(s1).isEqualTo(OrderStatus.NEW);
        assertThat(s2).isEqualTo(OrderStatus.NEW);
        assertThat(book.getActiveOrderCount()).isEqualTo(2);

        assertThat(book.getBestBid().getPrice()).isEqualTo(10000);
        assertThat(book.getBestBid().getTotalVolume()).isEqualTo(10);
        assertThat(book.getBestAsk().getPrice()).isEqualTo(10100);
        assertThat(book.getBestAsk().getTotalVolume()).isEqualTo(20);
    }

    @Test
    void shouldEnforceFifoPriceTimePriority() {
        // Two bids at the same price: order 1 arrives before order 2
        book.processOrder(1, Side.BID, OrderType.LIMIT, 10000, 50, 1000);
        book.processOrder(2, Side.BID, OrderType.LIMIT, 10000, 50, 1001);

        // Incoming sell order of 70 shares should completely fill order 1 (50 shares),
        // and partially fill order 2 (20 shares)
        OrderStatus status = book.processOrder(3, Side.ASK, OrderType.LIMIT, 10000, 70, 1002);

        assertThat(status).isEqualTo(OrderStatus.FILLED);
        assertThat(executedTrades).hasSize(2);

        assertThat(executedTrades.get(0)).isEqualTo(new TradeInfo(1, 3, 10000, 50));
        assertThat(executedTrades.get(1)).isEqualTo(new TradeInfo(2, 3, 10000, 20));

        // Order 1 is filled and removed, Order 2 has 30 remaining
        assertThat(book.getOrder(1)).isNull();
        assertThat(book.getOrder(2)).isNotNull();
        assertThat(book.getOrder(2).getRemainingQty()).isEqualTo(30);
        assertThat(book.getBestBid().getTotalVolume()).isEqualTo(30);
    }

    @Test
    void shouldExecuteAgainstBetterPriceFirst() {
        // Bids at 100 and 102
        book.processOrder(1, Side.BID, OrderType.LIMIT, 10000, 10, 1000);
        book.processOrder(2, Side.BID, OrderType.LIMIT, 10200, 10, 1001);

        // Sell order at 99 should match against highest bid (102) first
        book.processOrder(3, Side.ASK, OrderType.LIMIT, 9900, 15, 1002);

        assertThat(executedTrades).hasSize(2);
        assertThat(executedTrades.get(0)).isEqualTo(new TradeInfo(2, 3, 10200, 10));
        assertThat(executedTrades.get(1)).isEqualTo(new TradeInfo(1, 3, 10000, 5));

        assertThat(book.getOrder(2)).isNull();
        assertThat(book.getOrder(1).getRemainingQty()).isEqualTo(5);
        assertThat(book.getBestBid().getPrice()).isEqualTo(10000);
    }

    @Test
    void shouldHandleMarketOrders() {
        book.processOrder(1, Side.ASK, OrderType.LIMIT, 10100, 20, 1000);
        book.processOrder(2, Side.ASK, OrderType.LIMIT, 10200, 30, 1001);

        // Market buy for 25 shares: 20 at 10100, 5 at 10200
        OrderStatus status = book.processOrder(3, Side.BID, OrderType.MARKET, 0, 25, 1002);

        assertThat(status).isEqualTo(OrderStatus.FILLED);
        assertThat(executedTrades).hasSize(2);
        assertThat(executedTrades.get(0)).isEqualTo(new TradeInfo(1, 3, 10100, 20));
        assertThat(executedTrades.get(1)).isEqualTo(new TradeInfo(2, 3, 10200, 5));
        assertThat(book.getBestAsk().getPrice()).isEqualTo(10200);
        assertThat(book.getBestAsk().getTotalVolume()).isEqualTo(25);
    }

    @Test
    void shouldHandleImmediateOrCancel() {
        book.processOrder(1, Side.ASK, OrderType.LIMIT, 10100, 10, 1000);

        // IOC buy for 25 shares: fills 10, cancels remaining 15 without resting in book
        OrderStatus status = book.processOrder(2, Side.BID, OrderType.IOC, 10100, 25, 1001);

        assertThat(status).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(executedTrades).hasSize(1);
        assertThat(executedTrades.get(0)).isEqualTo(new TradeInfo(1, 2, 10100, 10));
        assertThat(book.getActiveOrderCount()).isEqualTo(0);
        assertThat(book.getBestBid()).isNull();
        assertThat(book.getBestAsk()).isNull();
    }

    @Test
    void shouldHandleFillOrKill() {
        book.processOrder(1, Side.ASK, OrderType.LIMIT, 10100, 10, 1000);

        // FOK buy for 15 shares: only 10 available, so entire order is killed/canceled
        OrderStatus status = book.processOrder(2, Side.BID, OrderType.FOK, 10100, 15, 1001);
        assertThat(status).isEqualTo(OrderStatus.CANCELED);
        assertThat(executedTrades).isEmpty();
        assertThat(book.getBestAsk().getTotalVolume()).isEqualTo(10);

        // FOK buy for 10 shares: exactly 10 available, so fully filled
        OrderStatus s2 = book.processOrder(3, Side.BID, OrderType.FOK, 10100, 10, 1002);
        assertThat(s2).isEqualTo(OrderStatus.FILLED);
        assertThat(executedTrades).hasSize(1);
    }

    @Test
    void shouldRejectPostOnlyWhenCrossingSpread() {
        book.processOrder(1, Side.ASK, OrderType.LIMIT, 10100, 10, 1000);

        // Post-only bid at 10100 would cross best ask: must be rejected
        OrderStatus status = book.processOrder(2, Side.BID, OrderType.POST_ONLY, 10100, 10, 1001);
        assertThat(status).isEqualTo(OrderStatus.REJECTED);
        assertThat(executedTrades).isEmpty();

        // Post-only bid at 10000 does not cross: accepted
        OrderStatus s2 = book.processOrder(3, Side.BID, OrderType.POST_ONLY, 10000, 10, 1002);
        assertThat(s2).isEqualTo(OrderStatus.NEW);
        assertThat(book.getBestBid().getPrice()).isEqualTo(10000);
    }

    @Test
    void shouldCancelOrderInConstantTime() {
        book.processOrder(1, Side.BID, OrderType.LIMIT, 10000, 10, 1000);
        book.processOrder(2, Side.BID, OrderType.LIMIT, 10000, 20, 1001);

        assertThat(book.cancelOrder(1)).isTrue();
        assertThat(book.getOrder(1)).isNull();
        assertThat(book.getOrder(2)).isNotNull();
        assertThat(book.getBestBid().getTotalVolume()).isEqualTo(20);

        // Canceling order 2 should clean up price level
        assertThat(book.cancelOrder(2)).isTrue();
        assertThat(book.getBestBid()).isNull();
        assertThat(book.getActiveOrderCount()).isEqualTo(0);

        // Canceling non-existent order returns false
        assertThat(book.cancelOrder(999)).isFalse();
    }

    @Test
    void shouldReduceOrderQtyInPlace() {
        book.processOrder(1, Side.BID, OrderType.LIMIT, 10000, 50, 1000);

        assertThat(book.reduceOrderQty(1, 30)).isTrue();
        assertThat(book.getOrder(1).getRemainingQty()).isEqualTo(30);
        assertThat(book.getBestBid().getTotalVolume()).isEqualTo(30);

        // Cannot reduce to a higher or invalid quantity
        assertThat(book.reduceOrderQty(1, 40)).isFalse();
        assertThat(book.reduceOrderQty(1, 0)).isFalse();
    }

    @Test
    void shouldCaptureMarketDepthSnapshot() {
        book.processOrder(1, Side.BID, OrderType.LIMIT, 10000, 10, 1000);
        book.processOrder(2, Side.BID, OrderType.LIMIT, 9900, 20, 1001);
        book.processOrder(3, Side.ASK, OrderType.LIMIT, 10100, 30, 1002);
        book.processOrder(4, Side.ASK, OrderType.LIMIT, 10200, 40, 1003);

        BookSnapshot snapshot = new BookSnapshot();
        book.captureSnapshot(snapshot, 2000);

        assertThat(snapshot.getBidCount()).isEqualTo(2);
        assertThat(snapshot.getAskCount()).isEqualTo(2);

        assertThat(snapshot.getBidPrice(0)).isEqualTo(10000);
        assertThat(snapshot.getBidVolume(0)).isEqualTo(10);
        assertThat(snapshot.getBidPrice(1)).isEqualTo(9900);
        assertThat(snapshot.getBidVolume(1)).isEqualTo(20);

        assertThat(snapshot.getAskPrice(0)).isEqualTo(10100);
        assertThat(snapshot.getAskVolume(0)).isEqualTo(30);
        assertThat(snapshot.getAskPrice(1)).isEqualTo(10200);
        assertThat(snapshot.getAskVolume(1)).isEqualTo(40);

        assertThat(snapshot.getSpread()).isEqualTo(100);
    }
}
