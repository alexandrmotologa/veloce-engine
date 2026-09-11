package com.engine.veloce.integration;

import com.engine.veloce.domain.book.LimitOrderBook;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.pool.OrderEntryPool;
import com.engine.veloce.domain.pool.PriceLevelPool;
import com.engine.veloce.domain.pool.TradeEventPool;
import com.engine.veloce.engine.MatchingEngine;
import com.engine.veloce.engine.WaitStrategyType;
import com.engine.veloce.journal.ReplayEngine;
import com.engine.veloce.journal.WriteAheadLog;
import com.engine.veloce.network.TcpOrderServer;
import com.engine.veloce.network.WebGateway;
import com.engine.veloce.protocol.BinaryOrderFrame;
import com.engine.veloce.telemetry.LatencyRecorder;
import com.lmax.disruptor.dsl.ProducerType;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class EndToEndIntegrationTest {

    @Test
    void shouldExecuteFullTradingPipelineWithCrashRecovery(@TempDir Path tempDir) throws Exception {
        File walFile = tempDir.resolve("market_e2e.wal").toFile();
        WriteAheadLog wal = new WriteAheadLog(walFile, 4 * 1024 * 1024);

        MatchingEngine engine = null;
        TcpOrderServer tcpServer = null;
        WebGateway webGateway = null;

        int expectedActiveOrders;
        long expectedBestBid;
        long expectedBestAsk;

        try {
            // 1. Initialize Matching Core
            engine = new MatchingEngine(
                    "AAPL",
                    10_000,
                    2_000,
                    2_000,
                    4096,
                    WaitStrategyType.YIELDING,
                    ProducerType.MULTI
            );
            engine.setWriteAheadLog(wal);
            engine.start();

            // 2. Start TCP Order Gateway and Embedded Web Gateway
            tcpServer = new TcpOrderServer(0, engine);
            tcpServer.start();

            LatencyRecorder latencyRecorder = new LatencyRecorder();
            webGateway = new WebGateway(0, engine, latencyRecorder);
            webGateway.start();

            int tcpPort = tcpServer.getBoundPort();
            int httpPort = webGateway.getBoundPort();

            // 3. Connect TCP Client and Stream Binary Order Frames
            try (Socket clientSocket = new Socket("127.0.0.1", tcpPort)) {
                clientSocket.setTcpNoDelay(true);
                OutputStream out = clientSocket.getOutputStream();

                ByteBuffer buffer = ByteBuffer.allocate(BinaryOrderFrame.FRAME_LENGTH);
                UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

                long orderIdCounter = 1000;

                // Submit 40 passive bids: $140.00 to $149.75
                for (int i = 0; i < 40; i++) {
                    orderIdCounter++;
                    buffer.clear();
                    long price = 1400000L + (i * 2500L);
                    BinaryOrderFrame.encode(
                            unsafeBuffer,
                            0,
                            System.nanoTime(),
                            orderIdCounter,
                            price,
                            10,
                            Side.BID,
                            OrderType.LIMIT
                    );
                    out.write(buffer.array());
                }

                // Submit 40 passive asks: $151.00 to $160.75
                for (int i = 0; i < 40; i++) {
                    orderIdCounter++;
                    buffer.clear();
                    long price = 1510000L + (i * 2500L);
                    BinaryOrderFrame.encode(
                            unsafeBuffer,
                            0,
                            System.nanoTime(),
                            orderIdCounter,
                            price,
                            10,
                            Side.ASK,
                            OrderType.LIMIT
                    );
                    out.write(buffer.array());
                }

                // Submit 10 aggressive crossing asks at $149.00
                // 4 will match bids >= 149.00, and 6 will rest on the ask book at 149.00
                for (int i = 0; i < 10; i++) {
                    orderIdCounter++;
                    buffer.clear();
                    BinaryOrderFrame.encode(
                            unsafeBuffer,
                            0,
                            System.nanoTime(),
                            orderIdCounter,
                            1490000L,
                            10,
                            Side.ASK,
                            OrderType.LIMIT
                    );
                    out.write(buffer.array());
                }

                out.flush();

                // Await processing
                long deadline = System.currentTimeMillis() + 3000;
                while (engine.getProcessedCount() < 90 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10);
                }

                assertThat(engine.getProcessedCount()).isEqualTo(90);
            }

            // 4. Verify HTTP REST /api/snapshot endpoint reflects state
            HttpURLConnection httpConn = (HttpURLConnection) URI.create("http://127.0.0.1:" + httpPort + "/api/snapshot").toURL().openConnection();
            httpConn.setRequestMethod("GET");
            assertThat(httpConn.getResponseCode()).isEqualTo(200);

            String snapshotJson;
            try (InputStream in = httpConn.getInputStream()) {
                snapshotJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertThat(snapshotJson).contains("\"symbol\":\"AAPL\"");
            assertThat(snapshotJson).contains("\"processedOrders\":90");
            assertThat(snapshotJson).contains("\"totalTrades\":4");

            // Record expected state before simulated crash
            expectedActiveOrders = engine.getBook().getActiveOrderCount();
            expectedBestBid = engine.getBook().getBestBid().getPrice();
            expectedBestAsk = engine.getBook().getBestAsk().getPrice();

        } finally {
            // Ensure all network servers and WAL resources are gracefully released
            if (webGateway != null) webGateway.stop();
            if (tcpServer != null) tcpServer.stop();
            if (engine != null) engine.shutdown();
            wal.close();
        }

        // 5. Simulate crash recovery: replay WAL into a fresh book
        OrderEntryPool orderPool = new OrderEntryPool(10_000);
        PriceLevelPool levelPool = new PriceLevelPool(2_000);
        TradeEventPool tradePool = new TradeEventPool(2_000);
        LimitOrderBook recoveredBook = new LimitOrderBook("AAPL", orderPool, levelPool, tradePool);

        long replayedCount = ReplayEngine.replayIntoBook(walFile, recoveredBook);

        assertThat(replayedCount).isEqualTo(90);
        assertThat(recoveredBook.getActiveOrderCount()).isEqualTo(expectedActiveOrders);
        assertThat(recoveredBook.getBestBid().getPrice()).isEqualTo(expectedBestBid);
        assertThat(recoveredBook.getBestAsk().getPrice()).isEqualTo(expectedBestAsk);
    }
}
