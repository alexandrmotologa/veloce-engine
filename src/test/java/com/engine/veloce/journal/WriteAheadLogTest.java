package com.engine.veloce.journal;

import com.engine.veloce.domain.book.LimitOrderBook;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.pool.OrderEntryPool;
import com.engine.veloce.domain.pool.PriceLevelPool;
import com.engine.veloce.domain.pool.TradeEventPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class WriteAheadLogTest {

    @Test
    void shouldWriteAndReplayOrdersDeterministically(@TempDir Path tempDir) throws Exception {
        File walFile = tempDir.resolve("market.wal").toFile();

        // 1. Write 200 orders to the WAL
        try (WriteAheadLog wal = new WriteAheadLog(walFile, 1024 * 1024)) {
            for (int i = 1; i <= 100; i++) {
                wal.appendOrder(i, Side.BID, OrderType.LIMIT, 10000 + i, 10, i * 1000L);
            }
            for (int i = 101; i <= 200; i++) {
                wal.appendOrder(i, Side.ASK, OrderType.LIMIT, 20000 + i, 20, i * 1000L);
            }
            assertThat(wal.getSequenceNumber()).isEqualTo(200);
        }

        // 2. Simulate crash recovery: instantiate clean LimitOrderBook and replay
        OrderEntryPool orderPool = new OrderEntryPool(1000);
        PriceLevelPool levelPool = new PriceLevelPool(500);
        TradeEventPool tradePool = new TradeEventPool(500);
        LimitOrderBook recoveredBook = new LimitOrderBook("AAPL", orderPool, levelPool, tradePool);

        long recoveredCount = ReplayEngine.replayIntoBook(walFile, recoveredBook);

        assertThat(recoveredCount).isEqualTo(200);
        assertThat(recoveredBook.getActiveOrderCount()).isEqualTo(200);
        // Best Bid was the highest price (order 100 at 10100)
        assertThat(recoveredBook.getBestBid().getPrice()).isEqualTo(10100);
        // Best Ask was the lowest price (order 101 at 20101)
        assertThat(recoveredBook.getBestAsk().getPrice()).isEqualTo(20101);
    }
}
