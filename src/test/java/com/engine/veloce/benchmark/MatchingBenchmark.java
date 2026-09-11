package com.engine.veloce.benchmark;

import com.engine.veloce.domain.book.LimitOrderBook;
import com.engine.veloce.domain.model.OrderStatus;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.pool.OrderEntryPool;
import com.engine.veloce.domain.pool.PriceLevelPool;
import com.engine.veloce.domain.pool.TradeEventPool;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:-RestrictContended", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC", "-XX:+ZGenerational"})
@State(Scope.Benchmark)
public class MatchingBenchmark {

    private static final int ORDER_COUNT = 100_000;

    private LimitOrderBook book;
    private OrderEntryPool orderPool;
    private PriceLevelPool levelPool;
    private TradeEventPool tradePool;

    // Pre-allocated benchmark order input streams to isolate matching speed from RNG overhead
    private long[] orderIds;
    private Side[] sides;
    private OrderType[] types;
    private long[] prices;
    private long[] quantities;

    private int orderIndex;

    @Setup(Level.Trial)
    public void setupTrial() {
        orderIds = new long[ORDER_COUNT];
        sides = new Side[ORDER_COUNT];
        types = new OrderType[ORDER_COUNT];
        prices = new long[ORDER_COUNT];
        quantities = new long[ORDER_COUNT];

        Random random = new Random(12345);
        long mid = 150_0000; // $150.0000

        for (int i = 0; i < ORDER_COUNT; i++) {
            orderIds[i] = i + 1;
            sides[i] = random.nextBoolean() ? Side.BID : Side.ASK;
            types[i] = random.nextInt(10) < 8 ? OrderType.LIMIT : OrderType.IOC;
            int spreadOffset = (random.nextInt(10)) * 100;
            prices[i] = sides[i] == Side.BID ? (mid - spreadOffset) : (mid + spreadOffset);
            quantities[i] = (random.nextInt(5) + 1) * 10L;
        }
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        orderPool = new OrderEntryPool(200_000);
        levelPool = new PriceLevelPool(10_000);
        tradePool = new TradeEventPool(50_000);
        book = new LimitOrderBook("AAPL", orderPool, levelPool, tradePool);
        orderIndex = 0;
    }

    @Benchmark
    public void benchmarkMatchingThroughput(Blackhole blackhole) {
        int idx = orderIndex % ORDER_COUNT;
        OrderStatus status = book.processOrder(
                orderIds[idx],
                sides[idx],
                types[idx],
                prices[idx],
                quantities[idx],
                idx
        );
        blackhole.consume(status);
        orderIndex++;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MatchingBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .build();

        new Runner(opt).run();
    }
}
