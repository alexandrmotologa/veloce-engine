package com.engine.veloce.engine;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExchangeEngineTest {

    private ExchangeEngine exchange;

    @BeforeEach
    void setUp() {
        exchange = new ExchangeEngine(WaitStrategyType.YIELDING);
        exchange.registerSymbol("AAPL", 10_000, 1_000, 1_000, 4096, WaitStrategyType.YIELDING);
        exchange.registerSymbol("BTC-USD", 10_000, 1_000, 1_000, 4096, WaitStrategyType.YIELDING);
    }

    @AfterEach
    void tearDown() {
        exchange.shutdown();
    }

    @Test
    void shouldRouteOrdersToMultipleSymbols() throws InterruptedException {
        assertThat(exchange.getSymbols()).containsExactlyInAnyOrder("AAPL", "BTC-USD");

        // Submit to AAPL
        exchange.submitOrder("AAPL", 1, Side.BID, OrderType.LIMIT, 1500000, 10, System.nanoTime());
        // Submit to BTC-USD
        exchange.submitOrder("BTC-USD", 2, Side.BID, OrderType.LIMIT, 650000000, 5, System.nanoTime());

        Thread.sleep(100);

        assertThat(exchange.getEngine("AAPL").getProcessedCount()).isEqualTo(1);
        assertThat(exchange.getEngine("BTC-USD").getProcessedCount()).isEqualTo(1);
    }
}
