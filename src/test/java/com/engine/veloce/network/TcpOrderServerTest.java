package com.engine.veloce.network;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.engine.MatchingEngine;
import com.engine.veloce.engine.WaitStrategyType;
import com.engine.veloce.protocol.BinaryOrderFrame;
import com.lmax.disruptor.dsl.ProducerType;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class TcpOrderServerTest {

    private MatchingEngine engine;
    private TcpOrderServer server;

    @BeforeEach
    void setUp() throws Exception {
        engine = new MatchingEngine(
                "AAPL",
                10_000,
                1_000,
                1_000,
                4096,
                WaitStrategyType.YIELDING,
                ProducerType.MULTI
        );
        engine.start();

        // Port 0 selects an available ephemeral port
        server = new TcpOrderServer(0, engine);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        engine.shutdown();
    }

    @Test
    void shouldReceiveBinaryFramesOverTcpSocket() throws Exception {
        int port = server.getBoundPort();
        assertThat(port).isGreaterThan(0);

        try (Socket clientSocket = new Socket("127.0.0.1", port)) {
            clientSocket.setTcpNoDelay(true);
            OutputStream out = clientSocket.getOutputStream();

            ByteBuffer byteBuffer = ByteBuffer.allocate(BinaryOrderFrame.FRAME_LENGTH);
            UnsafeBuffer unsafeBuffer = new UnsafeBuffer(byteBuffer);

            // Send 20 bid orders over TCP
            for (int i = 1; i <= 20; i++) {
                byteBuffer.clear();
                BinaryOrderFrame.encode(
                        unsafeBuffer,
                        0,
                        System.nanoTime(),
                        i,
                        1500000 + i,
                        10,
                        Side.BID,
                        OrderType.LIMIT
                );
                out.write(byteBuffer.array());
            }
            out.flush();

            // Allow NIO selector to drain socket buffer
            long deadline = System.currentTimeMillis() + 3000;
            while (engine.getProcessedCount() < 20 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }

            assertThat(engine.getProcessedCount()).isEqualTo(20);
            assertThat(engine.getBook().getActiveOrderCount()).isEqualTo(20);
        }
    }
}
