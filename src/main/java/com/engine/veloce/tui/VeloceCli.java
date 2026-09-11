package com.engine.veloce.tui;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.trade.BookSnapshot;
import com.engine.veloce.domain.trade.TradeEvent;
import com.engine.veloce.engine.MatchingEngine;
import com.engine.veloce.engine.WaitStrategyType;
import com.engine.veloce.telemetry.LatencyRecorder;
import com.lmax.disruptor.dsl.ProducerType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Command(
        name = "veloce-engine",
        mixinStandardHelpOptions = true,
        version = "veloce-engine 1.0.0",
        description = "Ultra-low-latency Java 21 Limit Order Book & Matching Engine"
)
public final class VeloceCli implements Callable<Integer> {

    @Option(names = {"--synthetic-load"}, description = "Generates synthetic order flow for stress and visualization")
    private boolean syntheticLoad = false;

    @Option(names = {"--rate"}, defaultValue = "100000", description = "Target orders per second for synthetic load")
    private int rate = 100_000;

    @Option(names = {"--symbol"}, defaultValue = "AAPL", description = "Instrument ticker symbol")
    private String symbol = "AAPL";

    @Option(names = {"--wait-strategy"}, defaultValue = "BUSY_SPIN", description = "Disruptor wait strategy: BUSY_SPIN, YIELDING, SLEEPING")
    private WaitStrategyType waitStrategy = WaitStrategyType.BUSY_SPIN;

    @Option(names = {"--headless"}, description = "Disables ANSI terminal graphics (useful for headless CI)")
    private boolean headless = false;

    @Option(names = {"--duration"}, defaultValue = "0", description = "Duration in seconds to run before exiting (0 for infinite)")
    private int duration = 0;

    @Option(names = {"--tcp-port"}, defaultValue = "9881", description = "TCP order gateway port (0 to disable)")
    private int tcpPort = 9881;

    @Option(names = {"--http-port"}, defaultValue = "8080", description = "Embedded WebGateway HTTP/SSE port (0 to disable)")
    private int httpPort = 8080;

    @Option(names = {"--wal"}, description = "Path to write-ahead log file (optional)")
    private String walPath = null;

    @Override
    public Integer call() throws Exception {
        System.out.println("Starting VeloceEngine for " + symbol + " with " + waitStrategy + " wait strategy...");

        MatchingEngine engine = new MatchingEngine(
                symbol,
                1_000_000,
                100_000,
                100_000,
                1024 * 1024,
                waitStrategy,
                ProducerType.MULTI
        );

        com.engine.veloce.journal.WriteAheadLog wal = null;
        if (walPath != null && !walPath.isBlank()) {
            java.io.File walFile = new java.io.File(walPath);
            wal = new com.engine.veloce.journal.WriteAheadLog(walFile, 64L * 1024 * 1024);
            engine.setWriteAheadLog(wal);
            System.out.println("[WAL] Active write-ahead log mapped to " + walFile.getAbsolutePath());
        }

        LatencyRecorder latencyRecorder = new LatencyRecorder();
        TapeWidget tapeWidget = new TapeWidget(10);
        AtomicLong totalMatches = new AtomicLong();

        engine.setTradeListener((TradeEvent trade) -> {
            totalMatches.incrementAndGet();
            tapeWidget.addTrade(trade.getTradeId(), trade.getTakerSide(),
                    trade.getPrice(), trade.getExecutedQty(), trade.getTimestampNs());
        });

        engine.start();

        com.engine.veloce.network.TcpOrderServer tcpServer = null;
        if (tcpPort > 0) {
            tcpServer = new com.engine.veloce.network.TcpOrderServer(tcpPort, engine);
            tcpServer.start();
            System.out.println("[TCP] Java NIO order gateway listening on port " + tcpServer.getBoundPort());
        }

        com.engine.veloce.network.WebGateway webGateway = null;
        if (httpPort > 0) {
            webGateway = new com.engine.veloce.network.WebGateway(httpPort, engine, latencyRecorder);
            webGateway.start();
            System.out.println("[HTTP] Web Dashboard & SSE Gateway available at http://127.0.0.1:" + webGateway.getBoundPort());
        }

        AtomicBoolean running = new AtomicBoolean(true);
        final com.engine.veloce.network.TcpOrderServer finalTcp = tcpServer;
        final com.engine.veloce.network.WebGateway finalWeb = webGateway;
        final com.engine.veloce.journal.WriteAheadLog finalWal = wal;

        Thread shutdownHook = new Thread(() -> {
            running.set(false);
            if (finalWeb != null) finalWeb.stop();
            if (finalTcp != null) finalTcp.stop();
            engine.shutdown();
            if (finalWal != null) {
                try {
                    finalWal.close();
                } catch (Exception ignored) {}
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // Background synthetic load generator
        Thread generatorThread = null;
        if (syntheticLoad) {
            generatorThread = new Thread(() -> runSyntheticGenerator(engine, latencyRecorder, running), "synthetic-generator");
            generatorThread.setDaemon(true);
            generatorThread.start();
        }

        MarketDepthLadder ladder = new MarketDepthLadder(System.out);
        BookSnapshot snapshot = new BookSnapshot();

        long startTime = System.currentTimeMillis();
        long lastMatches = 0;
        long lastTime = System.currentTimeMillis();

        while (running.get()) {
            Thread.sleep(100); // 10 FPS refresh

            long now = System.currentTimeMillis();
            if (duration > 0 && (now - startTime) >= duration * 1000L) {
                break;
            }

            long currentMatches = totalMatches.get();
            double matchRate = (currentMatches - lastMatches) * 1000.0 / Math.max(1, now - lastTime);
            lastMatches = currentMatches;
            lastTime = now;

            latencyRecorder.sample();
            engine.captureSnapshot(snapshot, System.nanoTime());

            if (!headless) {
                ladder.render(
                        symbol,
                        snapshot,
                        latencyRecorder,
                        engine.getProcessedCount(),
                        currentMatches,
                        matchRate,
                        tapeWidget
                );
            }
        }

        running.set(false);
        engine.shutdown();
        System.out.println("VeloceEngine shutdown gracefully. Total processed: " + engine.getProcessedCount() + " orders.");
        return 0;
    }

    private void runSyntheticGenerator(MatchingEngine engine, LatencyRecorder latency, AtomicBoolean running) {
        Random random = new Random(42);
        long orderId = 1;
        long midPrice = 150_0000; // $150.0000

        while (running.get()) {
            // Slight random walk of mid price
            if (random.nextInt(100) < 5) {
                midPrice += (random.nextBoolean() ? 100 : -100);
            }

            Side side = random.nextBoolean() ? Side.BID : Side.ASK;
            int offset = random.nextInt(20) * 100; // 0 to $0.20 spread
            long price = (side == Side.BID) ? (midPrice - offset) : (midPrice + offset);

            // 85% Limit, 10% Market, 5% IOC
            int typeRoll = random.nextInt(100);
            OrderType type = typeRoll < 85 ? OrderType.LIMIT : (typeRoll < 95 ? OrderType.MARKET : OrderType.IOC);
            long qty = (random.nextInt(10) + 1) * 10L;

            long t0 = System.nanoTime();
            engine.submitOrder(orderId++, side, type, price, qty, t0);
            long t1 = System.nanoTime();
            latency.record(t1 - t0);

            // Throttle to target rate if needed
            if (rate < 1_000_000) {
                long sleepNanos = 1_000_000_000L / rate;
                if (sleepNanos > 100_000) {
                    try {
                        Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        }
    }
}
