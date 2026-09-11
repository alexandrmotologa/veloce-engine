package com.engine.veloce.network;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.trade.BookSnapshot;
import com.engine.veloce.domain.trade.TradeEvent;
import com.engine.veloce.engine.MatchingEngine;
import com.engine.veloce.telemetry.LatencyRecorder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Embedded HTTP and Real-Time Event Gateway for browser trading and telemetry.
 * Provides web dashboard hosting, REST order placement, and Server-Sent Events (SSE) market data streaming.
 */
public final class WebGateway {

    private final int requestedPort;
    private final MatchingEngine engine;
    private final LatencyRecorder latencyRecorder;
    private final AtomicLong totalTrades = new AtomicLong();
    private final CopyOnWriteArrayList<OutputStream> sseClients = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private int boundPort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread broadcastThread;

    public WebGateway(int port, MatchingEngine engine, LatencyRecorder latencyRecorder) {
        this.requestedPort = port;
        this.engine = engine;
        this.latencyRecorder = latencyRecorder;

        this.engine.setTradeListener((TradeEvent trade) -> {
            totalTrades.incrementAndGet();
            broadcastTrade(trade);
        });
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(requestedPort), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/", new StaticResourceHandler("web/index.html", "text/html"));
        server.createContext("/style.css", new StaticResourceHandler("web/style.css", "text/css"));
        server.createContext("/app.js", new StaticResourceHandler("web/app.js", "application/javascript"));
        server.createContext("/api/snapshot", this::handleSnapshot);
        server.createContext("/api/order", this::handleOrderSubmission);
        server.createContext("/api/cancel", this::handleOrderCancel);
        server.createContext("/api/events", this::handleSseStream);

        server.start();
        this.boundPort = server.getAddress().getPort();
        this.running.set(true);

        // Background periodic snapshot broadcaster (10 FPS)
        this.broadcastThread = new Thread(this::runBroadcastLoop, "veloce-sse-broadcaster");
        this.broadcastThread.setDaemon(true);
        this.broadcastThread.start();
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        if (server != null) {
            server.stop(0);
        }
        for (OutputStream os : sseClients) {
            try {
                os.close();
            } catch (IOException ignored) {}
        }
        sseClients.clear();
    }

    private void handleSnapshot(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        BookSnapshot snapshot = new BookSnapshot();
        engine.captureSnapshot(snapshot, System.nanoTime());
        String json = buildSnapshotJson(snapshot);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleOrderSubmission(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        long orderId = System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
        Side side = body.contains("\"side\":\"SELL\"") || body.contains("\"side\":\"ASK\"") ? Side.ASK : Side.BID;
        OrderType type = OrderType.LIMIT;
        if (body.contains("\"type\":\"MARKET\"")) type = OrderType.MARKET;
        else if (body.contains("\"type\":\"IOC\"")) type = OrderType.IOC;
        else if (body.contains("\"type\":\"FOK\"")) type = OrderType.FOK;
        else if (body.contains("\"type\":\"POST_ONLY\"")) type = OrderType.POST_ONLY;

        long price = extractLong(body, "price", 10000); // scaled by 10,000
        long qty = extractLong(body, "qty", 1);
        if (qty <= 0) qty = 10;

        engine.submitOrder(orderId, side, type, price, qty, System.nanoTime());

        String response = String.format("{\"status\":\"ACCEPTED\",\"orderId\":%d}", orderId);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleOrderCancel(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        long orderId = extractLong(body, "orderId", 1);
        engine.cancelOrder(orderId, System.nanoTime());

        String response = "{\"status\":\"CANCEL_SUBMITTED\"}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleSseStream(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        sseClients.add(os);
    }

    private void runBroadcastLoop() {
        BookSnapshot snapshot = new BookSnapshot();
        while (running.get()) {
            try {
                Thread.sleep(100); // 100ms refresh
                if (sseClients.isEmpty()) {
                    continue;
                }

                engine.captureSnapshot(snapshot, System.nanoTime());
                String json = buildSnapshotJson(snapshot);
                String sseData = "event: snapshot\ndata: " + json + "\n\n";
                byte[] bytes = sseData.getBytes(StandardCharsets.UTF_8);

                for (OutputStream os : sseClients) {
                    try {
                        os.write(bytes);
                        os.flush();
                    } catch (IOException e) {
                        sseClients.remove(os);
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void broadcastTrade(TradeEvent trade) {
        if (sseClients.isEmpty()) {
            return;
        }
        String json = String.format(Locale.US,
                "{\"tradeId\":%d,\"side\":\"%s\",\"price\":%.4f,\"qty\":%d,\"timestamp\":%d}",
                trade.getTradeId(), trade.getTakerSide(), trade.getPrice() / 10000.0,
                trade.getExecutedQty(), trade.getTimestampNs()
        );
        String sseData = "event: trade\ndata: " + json + "\n\n";
        byte[] bytes = sseData.getBytes(StandardCharsets.UTF_8);

        for (OutputStream os : sseClients) {
            try {
                os.write(bytes);
                os.flush();
            } catch (IOException e) {
                sseClients.remove(os);
            }
        }
    }

    private String buildSnapshotJson(BookSnapshot snapshot) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{");
        sb.append("\"symbol\":\"").append(engine.getBook().getSymbol()).append("\",");
        sb.append(String.format(Locale.US, "\"spread\":%.4f,", snapshot.getSpread() / 10000.0));
        sb.append(String.format(Locale.US, "\"bestBid\":%.4f,", snapshot.getBestBidPrice() / 10000.0));
        sb.append(String.format(Locale.US, "\"bestAsk\":%.4f,", snapshot.getBestAskPrice() / 10000.0));
        sb.append("\"processedOrders\":").append(engine.getProcessedCount()).append(",");
        sb.append("\"totalTrades\":").append(totalTrades.get()).append(",");

        sb.append("\"bids\":[");
        for (int i = 0; i < snapshot.getBidCount(); i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(Locale.US, "[%.4f,%d]", snapshot.getBidPrice(i) / 10000.0, snapshot.getBidVolume(i)));
        }
        sb.append("],");

        sb.append("\"asks\":[");
        for (int i = 0; i < snapshot.getAskCount(); i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(Locale.US, "[%.4f,%d]", snapshot.getAskPrice(i) / 10000.0, snapshot.getAskVolume(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static long extractLong(String json, String key, long scale) {
        int idx = json.indexOf("\"" + key + "\":");
        if (idx == -1) {
            return 0;
        }
        int start = idx + key.length() + 3;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) {
            end++;
        }
        String valStr = json.substring(start, end).trim();
        try {
            if (valStr.contains(".")) {
                double d = Double.parseDouble(valStr);
                return (long) (d * scale);
            } else {
                return Long.parseLong(valStr) * (scale == 10000 ? 10000 : 1);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public int getBoundPort() {
        return boundPort;
    }

    private static class StaticResourceHandler implements HttpHandler {
        private final String resourcePath;
        private final String contentType;

        StaticResourceHandler(String resourcePath, String contentType) {
            this.resourcePath = resourcePath;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = in.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
    }
}
