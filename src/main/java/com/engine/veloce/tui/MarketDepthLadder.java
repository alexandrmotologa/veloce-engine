package com.engine.veloce.tui;

import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.trade.BookSnapshot;
import com.engine.veloce.telemetry.LatencyRecorder;

import java.io.PrintStream;
import java.util.Locale;

/**
 * ANSI terminal market depth ladder renderer.
 * Visualizes order book state, spread, volume bars, latency percentiles, and trade tape.
 */
public final class MarketDepthLadder {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CLEAR_SCREEN = "\u001B[H\u001B[2J";

    private final PrintStream out;

    public MarketDepthLadder(PrintStream out) {
        this.out = out;
    }

    public void render(String symbol,
                       BookSnapshot snapshot,
                       LatencyRecorder latency,
                       long totalOrders,
                       long totalMatches,
                       double matchRate,
                       TapeWidget tape) {
        StringBuilder sb = new StringBuilder(2048);

        // Position cursor at top-left
        sb.append("\u001B[H");

        sb.append(CYAN).append("========================================================================================\n");
        sb.append(BOLD).append("  VELOCE-ENGINE | High-Frequency Limit Order Book & Matching Core\n").append(RESET);
        sb.append(CYAN).append("========================================================================================\n").append(RESET);

        // Header telemetry
        sb.append(String.format(Locale.US,
                " Symbol: %s%-6s%s | Orders: %s%,d%s | Matches: %s%,d%s | Rate: %s%,.0f matches/s%s\n",
                BOLD, symbol, RESET,
                BOLD, totalOrders, RESET,
                BOLD, totalMatches, RESET,
                GREEN + BOLD, matchRate, RESET));

        sb.append(String.format(Locale.US,
                " Latency (µs):  p50: %s%.2f%s | p90: %s%.2f%s | p99: %s%.2f%s | p99.9: %s%.2f%s | Max: %s%.2f%s\n",
                CYAN, latency.getP50Micros(), RESET,
                CYAN, latency.getP90Micros(), RESET,
                YELLOW, latency.getP99Micros(), RESET,
                RED, latency.getP999Micros(), RESET,
                RED + BOLD, latency.getMaxMicros(), RESET));

        double spread = snapshot.getSpread() / 10000.0;
        double bestBid = snapshot.getBestBidPrice() / 10000.0;
        double bestAsk = snapshot.getBestAskPrice() / 10000.0;

        sb.append(String.format(Locale.US,
                " Spread: %s$%.4f%s | Best Bid: %s$%.4f%s | Best Ask: %s$%.4f%s\n",
                BOLD, spread, RESET,
                GREEN, bestBid, RESET,
                RED, bestAsk, RESET));

        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(BOLD).append(String.format(" %-22s %-15s %12s %-15s %20s \n",
                "BID QTY", "BID DEPTH", "PRICE", "ASK DEPTH", "ASK QTY")).append(RESET);
        sb.append("----------------------------------------------------------------------------------------\n");

        int maxRows = Math.max(snapshot.getBidCount(), snapshot.getAskCount());
        maxRows = Math.min(maxRows, 10);

        long maxVolume = 1;
        for (int i = 0; i < maxRows; i++) {
            if (i < snapshot.getBidCount()) maxVolume = Math.max(maxVolume, snapshot.getBidVolume(i));
            if (i < snapshot.getAskCount()) maxVolume = Math.max(maxVolume, snapshot.getAskVolume(i));
        }

        // Render ladder rows
        for (int i = 0; i < Math.max(maxRows, 5); i++) {
            // Bid side
            String bidQtyStr = "";
            String bidBar = "";
            String bidPriceStr = "";

            if (i < snapshot.getBidCount()) {
                long bPrice = snapshot.getBidPrice(i);
                long bQty = snapshot.getBidVolume(i);
                bidQtyStr = String.format(Locale.US, "%,d", bQty);
                bidPriceStr = String.format(Locale.US, "$%.4f", bPrice / 10000.0);
                int barLen = (int) Math.min(15, (bQty * 15) / maxVolume);
                bidBar = "█".repeat(Math.max(1, barLen));
            }

            // Ask side
            String askQtyStr = "";
            String askBar = "";
            String askPriceStr = "";

            if (i < snapshot.getAskCount()) {
                long aPrice = snapshot.getAskPrice(i);
                long aQty = snapshot.getAskVolume(i);
                askQtyStr = String.format(Locale.US, "%,d", aQty);
                askPriceStr = String.format(Locale.US, "$%.4f", aPrice / 10000.0);
                int barLen = (int) Math.min(15, (aQty * 15) / maxVolume);
                askBar = "█".repeat(Math.max(1, barLen));
            }

            String centerPrice = !bidPriceStr.isEmpty() ? bidPriceStr : askPriceStr;

            sb.append(String.format(" %20s %s%-15s%s | %s%12s%s | %s%-15s%s %-20s\n",
                    GREEN + bidQtyStr + RESET,
                    GREEN, bidBar, RESET,
                    BOLD + YELLOW, centerPrice, RESET,
                    RED, askBar, RESET,
                    RED + askQtyStr + RESET));
        }

        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(BOLD).append(" RECENT TRANSACTION TAPE\n").append(RESET);
        sb.append("----------------------------------------------------------------------------------------\n");

        int tapeCount = 0;
        for (TapeWidget.TapeEntry entry : tape.getRecentTrades()) {
            if (tapeCount++ >= 5) break;
            String sideColor = entry.side() == Side.BID ? GREEN : RED;
            sb.append(String.format(Locale.US, " [Trade #%d] %s%-4s%s %8s shares @ %s$%.4f%s\n",
                    entry.tradeId(),
                    sideColor + BOLD, entry.side(), RESET,
                    String.format("%,d", entry.qty()),
                    BOLD, entry.price() / 10000.0, RESET));
        }
        while (tapeCount++ < 5) {
            sb.append(" --\n");
        }

        sb.append(CYAN).append("========================================================================================\n").append(RESET);

        out.print(sb);
        out.flush();
    }
}
