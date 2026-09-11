package com.engine.veloce.engine;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.trade.TradeEvent;
import com.lmax.disruptor.dsl.ProducerType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

public class MatchingEngineTest {

    private MatchingEngine engine;
    private AtomicLong tradeCount;

    @BeforeEach
    void setUp() {
        tradeCount = new AtomicLong();
        engine = new MatchingEngine(
                "AAPL",
                100_000,
                10_000,
                10_000,
                65536,
                WaitStrategyType.YIELDING,
                ProducerType.MULTI
        );
        engine.setTradeListener((TradeEvent trade) -> tradeCount.incrementAndGet());
        engine.start();
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    void shouldProcessOrdersThroughDisruptorRing() throws InterruptedException {
        int orderPairs = 10_000;

        for (int i = 1; i <= orderPairs; i++) {
            long bidId = i;
            long askId = orderPairs + i;

            engine.submitOrder(bidId, Side.BID, OrderType.LIMIT, 10000, 10, System.nanoTime());
            engine.submitOrder(askId, Side.ASK, OrderType.LIMIT, 10000, 10, System.nanoTime());
        }

        // Wait for single-writer core to drain the ring buffer
        long deadline = System.currentTimeMillis() + 5000;
        while (engine.getProcessedCount() < orderPairs * 2L && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertThat(engine.getProcessedCount()).isEqualTo(orderPairs * 2L);
        assertThat(tradeCount.get()).isEqualTo(orderPairs);
        assertThat(engine.getBook().getActiveOrderCount()).isEqualTo(0);
    }
}
