package com.engine.veloce.domain.book;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages resting conditional Stop-Loss and Stop-Limit orders.
 * Triggered automatically when the last traded price crosses the stop trigger price.
 */
public final class StopOrderBook {

    public record StopEntry(
            long orderId,
            long participantId,
            Side side,
            OrderType orderType,
            long limitPrice,
            long stopPrice,
            long quantity,
            long timestampNs
    ) {}

    @FunctionalInterface
    public interface TriggerCallback {
        void onTriggered(long orderId,
                         long participantId,
                         Side side,
                         OrderType orderType,
                         long limitPrice,
                         long quantity,
                         long timestampNs);
    }

    private final List<StopEntry> restingStops = new ArrayList<>(256);

    public void addStopOrder(long orderId,
                             long participantId,
                             Side side,
                             OrderType orderType,
                             long limitPrice,
                             long stopPrice,
                             long quantity,
                             long timestampNs) {
        restingStops.add(new StopEntry(
                orderId, participantId, side, orderType, limitPrice, stopPrice, quantity, timestampNs
        ));
    }

    public boolean cancelStopOrder(long orderId) {
        Iterator<StopEntry> it = restingStops.iterator();
        while (it.hasNext()) {
            if (it.next().orderId() == orderId) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates stop orders against the last trade price and fires triggered orders.
     */
    public void evaluateTriggers(long lastTradePrice, TriggerCallback callback) {
        if (restingStops.isEmpty()) {
            return;
        }

        Iterator<StopEntry> it = restingStops.iterator();
        while (it.hasNext()) {
            StopEntry stop = it.next();
            boolean triggered = false;

            if (stop.side() == Side.BID) {
                // Buy stop: triggers when market price rises to or above stop price
                if (lastTradePrice >= stop.stopPrice()) {
                    triggered = true;
                }
            } else {
                // Sell stop: triggers when market price drops to or below stop price
                if (lastTradePrice <= stop.stopPrice()) {
                    triggered = true;
                }
            }

            if (triggered) {
                it.remove();
                OrderType targetType = (stop.orderType() == OrderType.LIMIT) ? OrderType.LIMIT : OrderType.MARKET;
                callback.onTriggered(
                        stop.orderId(),
                        stop.participantId(),
                        stop.side(),
                        targetType,
                        stop.limitPrice(),
                        stop.quantity(),
                        stop.timestampNs()
                );
            }
        }
    }

    public int getRestingCount() {
        return restingStops.size();
    }

    public boolean isEmpty() {
        return restingStops.isEmpty();
    }
}
