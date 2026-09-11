package com.engine.veloce.domain.pool;

import com.engine.veloce.domain.book.LimitOrderBook;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ZeroGcAllocationTest {

    @Test
    void shouldAllocateZeroBytesOnHotPath() {
        com.sun.management.ThreadMXBean threadBean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

        if (!threadBean.isThreadAllocatedMemorySupported()) {
            System.out.println("Thread allocated memory measurement is not supported on this JVM.");
            return;
        }

        threadBean.setThreadAllocatedMemoryEnabled(true);
        long threadId = Thread.currentThread().threadId();

        OrderEntryPool orderPool = new OrderEntryPool(100_000);
        PriceLevelPool levelPool = new PriceLevelPool(10_000);
        TradeEventPool tradePool = new TradeEventPool(10_000);

        LimitOrderBook book = new LimitOrderBook("AAPL", orderPool, levelPool, tradePool);

        // 1. Warm-up phase: run 10,000 matches to allow JVM JIT warmup and inline caching
        long orderIdSeq = 1;
        for (int i = 0; i < 10_000; i++) {
            long bidId = orderIdSeq++;
            long askId = orderIdSeq++;
            book.processOrder(bidId, Side.BID, OrderType.LIMIT, 10000, 10, i);
            book.processOrder(askId, Side.ASK, OrderType.LIMIT, 10000, 10, i);
        }

        assertThat(book.getActiveOrderCount()).isEqualTo(0);

        // 2. Measure hot path over 50,000 matching iterations (100,000 orders total)
        long beforeAllocatedBytes = threadBean.getThreadAllocatedBytes(threadId);

        for (int i = 0; i < 50_000; i++) {
            long bidId = orderIdSeq++;
            long askId = orderIdSeq++;
            book.processOrder(bidId, Side.BID, OrderType.LIMIT, 10000, 10, i);
            book.processOrder(askId, Side.ASK, OrderType.LIMIT, 10000, 10, i);
        }

        long afterAllocatedBytes = threadBean.getThreadAllocatedBytes(threadId);
        long allocatedOnHotPath = afterAllocatedBytes - beforeAllocatedBytes;

        System.out.println("Hot path allocated bytes over 50,000 matches (100,000 orders): " + allocatedOnHotPath + " bytes");

        assertThat(book.getActiveOrderCount()).isEqualTo(0);
        assertThat(allocatedOnHotPath).isEqualTo(0L);
    }
}
