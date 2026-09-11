package com.engine.veloce.network;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.engine.MatchingEngine;
import com.engine.veloce.engine.WaitStrategyType;
import com.engine.veloce.telemetry.LatencyRecorder;
import com.lmax.disruptor.dsl.ProducerType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class WebGatewayTest {

    private MatchingEngine engine;
    private LatencyRecorder latencyRecorder;
    private WebGateway gateway;
    private String baseUrl;

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

        latencyRecorder = new LatencyRecorder();
        gateway = new WebGateway(0, engine, latencyRecorder);
        gateway.start();

        baseUrl = "http://127.0.0.1:" + gateway.getBoundPort();
    }

    @AfterEach
    void tearDown() {
        gateway.stop();
        engine.shutdown();
    }

    @Test
    void shouldServeStaticAssets() throws Exception {
        // Test index.html
        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/").toURL().openConnection();
        conn.setRequestMethod("GET");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(body).contains("VELOCE");
        assertThat(conn.getContentType()).contains("text/html");

        // Test style.css
        HttpURLConnection cssConn = (HttpURLConnection) URI.create(baseUrl + "/style.css").toURL().openConnection();
        cssConn.setRequestMethod("GET");
        assertThat(cssConn.getResponseCode()).isEqualTo(200);
        assertThat(cssConn.getContentType()).contains("text/css");

        // Test app.js
        HttpURLConnection jsConn = (HttpURLConnection) URI.create(baseUrl + "/app.js").toURL().openConnection();
        jsConn.setRequestMethod("GET");
        assertThat(jsConn.getResponseCode()).isEqualTo(200);
        assertThat(jsConn.getContentType()).contains("application/javascript");
    }

    @Test
    void shouldServeBookSnapshot() throws Exception {
        // Submit orders to seed book
        engine.submitOrder(101L, Side.BID, OrderType.LIMIT, 1500000L, 50, System.nanoTime());
        engine.submitOrder(102L, Side.ASK, OrderType.LIMIT, 1520000L, 30, System.nanoTime());

        long deadline = System.currentTimeMillis() + 1000;
        while (engine.getProcessedCount() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/snapshot").toURL().openConnection();
        conn.setRequestMethod("GET");
        assertThat(conn.getResponseCode()).isEqualTo(200);

        String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"symbol\":\"AAPL\"");
        assertThat(json).contains("\"bids\":");
        assertThat(json).contains("\"asks\":");
        assertThat(json).contains("\"bestBid\":150.0000");
        assertThat(json).contains("\"bestAsk\":152.0000");
    }

    @Test
    void shouldAcceptOrderViaRestAndAllowCancel() throws Exception {
        // 1. Submit order
        HttpURLConnection postConn = (HttpURLConnection) URI.create(baseUrl + "/api/order").toURL().openConnection();
        postConn.setRequestMethod("POST");
        postConn.setDoOutput(true);
        postConn.setRequestProperty("Content-Type", "application/json");

        String orderJson = "{\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":155.50,\"qty\":40}";
        try (OutputStream os = postConn.getOutputStream()) {
            os.write(orderJson.getBytes(StandardCharsets.UTF_8));
        }

        assertThat(postConn.getResponseCode()).isEqualTo(200);
        String resp = new String(postConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(resp).contains("\"status\":\"ACCEPTED\"");
        assertThat(resp).contains("\"orderId\":");

        long deadline = System.currentTimeMillis() + 1000;
        while (engine.getProcessedCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(engine.getProcessedCount()).isGreaterThanOrEqualTo(1);

        // 2. Cancel order
        HttpURLConnection cancelConn = (HttpURLConnection) URI.create(baseUrl + "/api/cancel").toURL().openConnection();
        cancelConn.setRequestMethod("POST");
        cancelConn.setDoOutput(true);
        cancelConn.setRequestProperty("Content-Type", "application/json");

        String cancelJson = "{\"orderId\":9999}";
        try (OutputStream os = cancelConn.getOutputStream()) {
            os.write(cancelJson.getBytes(StandardCharsets.UTF_8));
        }

        assertThat(cancelConn.getResponseCode()).isEqualTo(200);
        String cancelResp = new String(cancelConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(cancelResp).contains("\"status\":\"CANCEL_SUBMITTED\"");
    }

    @Test
    void shouldEstablishSseStream() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/events").toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setReadTimeout(2000);

        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getContentType()).contains("text/event-stream");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            // Read first line or event from stream
            String line = reader.readLine();
            assertThat(line).isNotNull();
        }
    }
}
