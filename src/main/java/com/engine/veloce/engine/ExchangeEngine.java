package com.engine.veloce.engine;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.trade.BookSnapshot;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Top-level multi-symbol exchange orchestrator.
 * Routes orders to symbol-specific MatchingEngine instances.
 */
public final class ExchangeEngine {

    private final Map<String, MatchingEngine> engines = new ConcurrentHashMap<>();
    private final WaitStrategyType defaultWaitStrategy;

    public ExchangeEngine(WaitStrategyType defaultWaitStrategy) {
        this.defaultWaitStrategy = defaultWaitStrategy;
    }

    public synchronized MatchingEngine registerSymbol(String symbol) {
        return registerSymbol(symbol, 1_000_000, 100_000, 100_000, 1024 * 1024, defaultWaitStrategy);
    }

    public synchronized MatchingEngine registerSymbol(String symbol,
                                                      int orderPoolCap,
                                                      int levelPoolCap,
                                                      int tradePoolCap,
                                                      int ringBufferSize,
                                                      WaitStrategyType waitStrategy) {
        MatchingEngine engine = new MatchingEngine(
                symbol,
                orderPoolCap,
                levelPoolCap,
                tradePoolCap,
                ringBufferSize,
                waitStrategy,
                ProducerType.MULTI
        );
        engines.put(symbol, engine);
        engine.start();
        return engine;
    }

    public MatchingEngine getEngine(String symbol) {
        return engines.get(symbol);
    }

    public void submitOrder(String symbol,
                            long orderId,
                            Side side,
                            OrderType orderType,
                            long price,
                            long quantity,
                            long timestampNs) {
        MatchingEngine engine = engines.get(symbol);
        if (engine != null) {
            engine.submitOrder(orderId, side, orderType, price, quantity, timestampNs);
        }
    }

    public void cancelOrder(String symbol, long orderId, long timestampNs) {
        MatchingEngine engine = engines.get(symbol);
        if (engine != null) {
            engine.cancelOrder(orderId, timestampNs);
        }
    }

    public void captureSnapshot(String symbol, BookSnapshot snapshot, long timestampNs) {
        MatchingEngine engine = engines.get(symbol);
        if (engine != null) {
            engine.captureSnapshot(snapshot, timestampNs);
        }
    }

    public Collection<String> getSymbols() {
        return engines.keySet();
    }

    public synchronized void shutdown() {
        for (MatchingEngine engine : engines.values()) {
            engine.shutdown();
        }
    }
}
