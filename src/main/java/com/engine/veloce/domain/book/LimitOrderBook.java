package com.engine.veloce.domain.book;

import com.engine.veloce.domain.model.OrderStatus;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.SelfTradePreventionMode;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.domain.pool.OrderEntryPool;
import com.engine.veloce.domain.pool.PriceLevelPool;
import com.engine.veloce.domain.pool.TradeEventPool;
import com.engine.veloce.domain.trade.BookSnapshot;
import com.engine.veloce.domain.trade.TradeEvent;
import com.engine.veloce.domain.trade.TradeListener;
import org.agrona.collections.Long2ObjectHashMap;

/**
 * Ultra-low-latency dual-sided Limit Order Book with FIFO price-time priority.
 * Designed for single-writer thread execution with zero runtime heap allocations.
 */
public final class LimitOrderBook {
    private final String symbol;

    // Top-of-book pointers
    private PriceLevel bestBid;
    private PriceLevel bestAsk;

    // Fast O(1) indices using Agrona primitive long hash maps
    private final Long2ObjectHashMap<PriceLevel> bidLevels;
    private final Long2ObjectHashMap<PriceLevel> askLevels;
    private final Long2ObjectHashMap<OrderEntry> activeOrders;

    // Zero-allocation pre-allocated object pools
    private final OrderEntryPool orderPool;
    private final PriceLevelPool levelPool;
    private final TradeEventPool tradePool;

    private final StopOrderBook stopOrderBook;
    private final StopOrderBook.TriggerCallback stopTriggerCallback;
    private SelfTradePreventionMode stpMode = SelfTradePreventionMode.NONE;
    private long lastTradedPrice;

    private TradeListener tradeListener;
    private long tradeSequence;

    public LimitOrderBook(String symbol,
                          OrderEntryPool orderPool,
                          PriceLevelPool levelPool,
                          TradeEventPool tradePool) {
        this.symbol = symbol;
        this.orderPool = orderPool;
        this.levelPool = levelPool;
        this.tradePool = tradePool;
        this.stopOrderBook = new StopOrderBook();
        this.stopTriggerCallback = (sId, sPartId, sSide, sType, sLimitPrice, sQty, sTs) ->
                processOrder(sId, sPartId, sSide, sType, sLimitPrice, sQty, sQty, 0L, sTs);

        this.bidLevels = new Long2ObjectHashMap<>(4096, 0.6f);
        this.askLevels = new Long2ObjectHashMap<>(4096, 0.6f);
        this.activeOrders = new Long2ObjectHashMap<>(65536, 0.6f);
    }

    public void setTradeListener(TradeListener tradeListener) {
        this.tradeListener = tradeListener;
    }

    public void setSelfTradePreventionMode(SelfTradePreventionMode stpMode) {
        this.stpMode = stpMode;
    }

    public SelfTradePreventionMode getSelfTradePreventionMode() {
        return stpMode;
    }

    public StopOrderBook getStopOrderBook() {
        return stopOrderBook;
    }

    public long getLastTradedPrice() {
        return lastTradedPrice;
    }

    /**
     * Processes an incoming order using FIFO price-time priority.
     *
     * @return resulting order status
     */
    public OrderStatus processOrder(long orderId,
                                    Side side,
                                    OrderType type,
                                    long price,
                                    long qty,
                                    long timestampNs) {
        return processOrder(orderId, 0L, side, type, price, qty, qty, 0L, timestampNs);
    }

    /**
     * Processes an incoming order with support for Participant IDs (STP), Iceberg display quantities,
     * and conditional Stop triggers.
     */
    public OrderStatus processOrder(long orderId,
                                    long participantId,
                                    Side side,
                                    OrderType type,
                                    long price,
                                    long qty,
                                    long displayQty,
                                    long stopPrice,
                                    long timestampNs) {
        if (qty <= 0) {
            return OrderStatus.REJECTED;
        }

        // 1. Conditional Stop order routing
        if (stopPrice > 0) {
            stopOrderBook.addStopOrder(orderId, participantId, side, type, price, stopPrice, qty, timestampNs);
            return OrderStatus.NEW;
        }

        // 2. Post-Only check: must not cross opposite book
        if (type == OrderType.POST_ONLY) {
            if (side == Side.BID && bestAsk != null && bestAsk.getPrice() <= price) {
                return OrderStatus.REJECTED;
            }
            if (side == Side.ASK && bestBid != null && bestBid.getPrice() >= price) {
                return OrderStatus.REJECTED;
            }
        }

        // 3. Fill-Or-Kill (FOK) check: verify if entire order can be filled immediately
        if (type == OrderType.FOK) {
            if (!canFulfillFok(side, price, qty)) {
                return OrderStatus.CANCELED;
            }
        }

        long remainingQty = qty;
        boolean matchedAny = false;

        // 4. Match against opposite side
        if (side == Side.BID) {
            while (remainingQty > 0 && bestAsk != null) {
                if (type != OrderType.MARKET && bestAsk.getPrice() > price) {
                    break;
                }
                long matchPrice = bestAsk.getPrice();
                OrderEntry makerOrder = bestAsk.getHead();

                // Self-Trade Prevention (STP) check
                if (stpMode != SelfTradePreventionMode.NONE && participantId != 0
                        && makerOrder.getParticipantId() != 0 && makerOrder.getParticipantId() == participantId) {
                    if (stpMode == SelfTradePreventionMode.CANCEL_NEWEST) {
                        return matchedAny ? OrderStatus.PARTIALLY_FILLED : OrderStatus.CANCELED;
                    } else if (stpMode == SelfTradePreventionMode.CANCEL_OLDEST) {
                        activeOrders.remove(makerOrder.getOrderId());
                        bestAsk.remove(makerOrder);
                        orderPool.release(makerOrder);
                        if (bestAsk.isEmpty()) {
                            removeAskLevel(bestAsk);
                        }
                        continue;
                    } else if (stpMode == SelfTradePreventionMode.DECREMENT_AND_CANCEL) {
                        long cancelQty = Math.min(remainingQty, makerOrder.getRemainingQty());
                        makerOrder.reduceQty(cancelQty);
                        bestAsk.reduceVolume(cancelQty);
                        remainingQty -= cancelQty;
                        if (makerOrder.isFilled()) {
                            activeOrders.remove(makerOrder.getOrderId());
                            bestAsk.remove(makerOrder);
                            orderPool.release(makerOrder);
                        }
                        if (bestAsk.isEmpty()) {
                            removeAskLevel(bestAsk);
                        }
                        if (remainingQty == 0) {
                            return matchedAny ? OrderStatus.PARTIALLY_FILLED : OrderStatus.CANCELED;
                        }
                        continue;
                    }
                }

                long matchQty = Math.min(remainingQty, makerOrder.getRemainingQty());
                makerOrder.reduceQty(matchQty);
                bestAsk.reduceVolume(matchQty);
                remainingQty -= matchQty;
                matchedAny = true;

                boolean makerFilled = makerOrder.isFilled();
                boolean takerFilled = remainingQty == 0;
                this.lastTradedPrice = matchPrice;

                emitTrade(++tradeSequence, timestampNs, makerOrder.getOrderId(), orderId,
                        matchPrice, matchQty, Side.BID, makerFilled, takerFilled);

                if (makerFilled) {
                    if (makerOrder.isIceberg() && makerOrder.getHiddenQty() > 0) {
                        long replenished = makerOrder.replenish();
                        bestAsk.remove(makerOrder);
                        bestAsk.append(makerOrder);
                        bestAsk.reduceVolume(-replenished);
                    } else {
                        activeOrders.remove(makerOrder.getOrderId());
                        bestAsk.remove(makerOrder);
                        orderPool.release(makerOrder);
                    }
                }

                if (bestAsk.isEmpty()) {
                    removeAskLevel(bestAsk);
                }

                // Check stop orders after each match
                if (!stopOrderBook.isEmpty()) {
                    stopOrderBook.evaluateTriggers(lastTradedPrice, stopTriggerCallback);
                }
            }
        } else {
            // Side.ASK
            while (remainingQty > 0 && bestBid != null) {
                if (type != OrderType.MARKET && bestBid.getPrice() < price) {
                    break;
                }
                long matchPrice = bestBid.getPrice();
                OrderEntry makerOrder = bestBid.getHead();

                // Self-Trade Prevention (STP) check
                if (stpMode != SelfTradePreventionMode.NONE && participantId != 0
                        && makerOrder.getParticipantId() != 0 && makerOrder.getParticipantId() == participantId) {
                    if (stpMode == SelfTradePreventionMode.CANCEL_NEWEST) {
                        return matchedAny ? OrderStatus.PARTIALLY_FILLED : OrderStatus.CANCELED;
                    } else if (stpMode == SelfTradePreventionMode.CANCEL_OLDEST) {
                        activeOrders.remove(makerOrder.getOrderId());
                        bestBid.remove(makerOrder);
                        orderPool.release(makerOrder);
                        if (bestBid.isEmpty()) {
                            removeBidLevel(bestBid);
                        }
                        continue;
                    } else if (stpMode == SelfTradePreventionMode.DECREMENT_AND_CANCEL) {
                        long cancelQty = Math.min(remainingQty, makerOrder.getRemainingQty());
                        makerOrder.reduceQty(cancelQty);
                        bestBid.reduceVolume(cancelQty);
                        remainingQty -= cancelQty;
                        if (makerOrder.isFilled()) {
                            activeOrders.remove(makerOrder.getOrderId());
                            bestBid.remove(makerOrder);
                            orderPool.release(makerOrder);
                        }
                        if (bestBid.isEmpty()) {
                            removeBidLevel(bestBid);
                        }
                        if (remainingQty == 0) {
                            return matchedAny ? OrderStatus.PARTIALLY_FILLED : OrderStatus.CANCELED;
                        }
                        continue;
                    }
                }

                long matchQty = Math.min(remainingQty, makerOrder.getRemainingQty());
                makerOrder.reduceQty(matchQty);
                bestBid.reduceVolume(matchQty);
                remainingQty -= matchQty;
                matchedAny = true;

                boolean makerFilled = makerOrder.isFilled();
                boolean takerFilled = remainingQty == 0;
                this.lastTradedPrice = matchPrice;

                emitTrade(++tradeSequence, timestampNs, makerOrder.getOrderId(), orderId,
                        matchPrice, matchQty, Side.ASK, makerFilled, takerFilled);

                if (makerFilled) {
                    if (makerOrder.isIceberg() && makerOrder.getHiddenQty() > 0) {
                        long replenished = makerOrder.replenish();
                        bestBid.remove(makerOrder);
                        bestBid.append(makerOrder);
                        bestBid.reduceVolume(-replenished);
                    } else {
                        activeOrders.remove(makerOrder.getOrderId());
                        bestBid.remove(makerOrder);
                        orderPool.release(makerOrder);
                    }
                }

                if (bestBid.isEmpty()) {
                    removeBidLevel(bestBid);
                }

                // Check stop orders after each match
                if (!stopOrderBook.isEmpty()) {
                    stopOrderBook.evaluateTriggers(lastTradedPrice, stopTriggerCallback);
                }
            }
        }

        // 5. Handle remaining unfilled quantity
        if (remainingQty == 0) {
            return OrderStatus.FILLED;
        }

        // Market, IOC, and FOK orders never rest in the book
        if (type == OrderType.MARKET || type == OrderType.IOC || type == OrderType.FOK) {
            return matchedAny ? OrderStatus.PARTIALLY_FILLED : OrderStatus.CANCELED;
        }

        // 6. Rest remaining LIMIT or POST_ONLY order in the book
        OrderEntry entry = orderPool.acquire();
        if (entry == null) {
            return matchedAny ? OrderStatus.PARTIALLY_FILLED : OrderStatus.REJECTED;
        }

        PriceLevel level;
        if (side == Side.BID) {
            level = bidLevels.get(price);
            if (level == null) {
                level = levelPool.acquire(price);
                if (level == null) {
                    orderPool.release(entry);
                    return matchedAny ? OrderStatus.PARTIALLY_FILLED : OrderStatus.REJECTED;
                }
                insertBidLevel(level);
                bidLevels.put(price, level);
            }
        } else {
            level = askLevels.get(price);
            if (level == null) {
                level = levelPool.acquire(price);
                if (level == null) {
                    orderPool.release(entry);
                    return matchedAny ? OrderStatus.PARTIALLY_FILLED : OrderStatus.REJECTED;
                }
                insertAskLevel(level);
                askLevels.put(price, level);
            }
        }

        long effectiveDisplay = (displayQty > 0 && displayQty < remainingQty) ? displayQty : remainingQty;
        long hidden = remainingQty - effectiveDisplay;

        entry.init(orderId, participantId, price, remainingQty, effectiveDisplay, hidden, timestampNs, side, type, level, entry.getPoolIndex());
        level.append(entry);
        activeOrders.put(orderId, entry);

        return matchedAny ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW;
    }

    /**
     * Cancels an active order in O(1) time.
     *
     * @return true if found and canceled, false if not found
     */
    public boolean cancelOrder(long orderId) {
        OrderEntry order = activeOrders.remove(orderId);
        if (order == null) {
            return false;
        }

        PriceLevel level = order.getParentLevel();
        if (level != null) {
            level.remove(order);
            if (level.isEmpty()) {
                if (order.getSide() == Side.BID) {
                    removeBidLevel(level);
                } else {
                    removeAskLevel(level);
                }
            }
        }

        orderPool.release(order);
        return true;
    }

    /**
     * Reduces the quantity of an existing order in-place without losing time priority.
     */
    public boolean reduceOrderQty(long orderId, long newQty) {
        OrderEntry order = activeOrders.get(orderId);
        if (order == null || newQty <= 0 || newQty >= order.getRemainingQty()) {
            return false;
        }
        long delta = order.getRemainingQty() - newQty;
        order.setRemainingQty(newQty);
        PriceLevel level = order.getParentLevel();
        if (level != null) {
            level.reduceVolume(delta);
        }
        return true;
    }

    private boolean canFulfillFok(Side side, long price, long qty) {
        long available = 0;
        if (side == Side.BID) {
            PriceLevel level = bestAsk;
            while (level != null && level.getPrice() <= price) {
                available += level.getTotalVolume();
                if (available >= qty) {
                    return true;
                }
                level = level.getNext();
            }
        } else {
            PriceLevel level = bestBid;
            while (level != null && level.getPrice() >= price) {
                available += level.getTotalVolume();
                if (available >= qty) {
                    return true;
                }
                level = level.getNext();
            }
        }
        return false;
    }

    private void insertBidLevel(PriceLevel newLevel) {
        long price = newLevel.getPrice();
        if (bestBid == null) {
            bestBid = newLevel;
            newLevel.prev = null;
            newLevel.next = null;
            return;
        }
        if (price > bestBid.getPrice()) {
            newLevel.next = bestBid;
            newLevel.prev = null;
            bestBid.prev = newLevel;
            bestBid = newLevel;
            return;
        }

        PriceLevel curr = bestBid;
        while (curr.next != null && curr.next.getPrice() > price) {
            curr = curr.next;
        }

        newLevel.next = curr.next;
        newLevel.prev = curr;
        if (curr.next != null) {
            curr.next.prev = newLevel;
        }
        curr.next = newLevel;
    }

    private void removeBidLevel(PriceLevel level) {
        bidLevels.remove(level.getPrice());
        if (level == bestBid) {
            bestBid = level.next;
            if (bestBid != null) {
                bestBid.prev = null;
            }
        } else {
            if (level.prev != null) {
                level.prev.next = level.next;
            }
            if (level.next != null) {
                level.next.prev = level.prev;
            }
        }
        levelPool.release(level);
    }

    private void insertAskLevel(PriceLevel newLevel) {
        long price = newLevel.getPrice();
        if (bestAsk == null) {
            bestAsk = newLevel;
            newLevel.prev = null;
            newLevel.next = null;
            return;
        }
        if (price < bestAsk.getPrice()) {
            newLevel.next = bestAsk;
            newLevel.prev = null;
            bestAsk.prev = newLevel;
            bestAsk = newLevel;
            return;
        }

        PriceLevel curr = bestAsk;
        while (curr.next != null && curr.next.getPrice() < price) {
            curr = curr.next;
        }

        newLevel.next = curr.next;
        newLevel.prev = curr;
        if (curr.next != null) {
            curr.next.prev = newLevel;
        }
        curr.next = newLevel;
    }

    private void removeAskLevel(PriceLevel level) {
        askLevels.remove(level.getPrice());
        if (level == bestAsk) {
            bestAsk = level.next;
            if (bestAsk != null) {
                bestAsk.prev = null;
            }
        } else {
            if (level.prev != null) {
                level.prev.next = level.next;
            }
            if (level.next != null) {
                level.next.prev = level.prev;
            }
        }
        levelPool.release(level);
    }

    private void emitTrade(long tradeId,
                           long timestampNs,
                           long makerOrderId,
                           long takerOrderId,
                           long price,
                           long executedQty,
                           Side takerSide,
                           boolean makerFullyFilled,
                           boolean takerFullyFilled) {
        if (tradeListener != null) {
            TradeEvent event = tradePool.acquire(tradeId, timestampNs, makerOrderId,
                    takerOrderId, price, executedQty, takerSide, makerFullyFilled, takerFullyFilled);
            if (event != null) {
                try {
                    tradeListener.onTrade(event);
                } finally {
                    tradePool.release(event);
                }
            }
        }
    }

    /**
     * Populates a pre-allocated BookSnapshot without creating objects.
     */
    public void captureSnapshot(BookSnapshot snapshot, long timestampNs) {
        snapshot.reset();
        snapshot.setTimestampNs(timestampNs);

        PriceLevel bid = bestBid;
        while (bid != null && snapshot.getBidCount() < BookSnapshot.MAX_DEPTH) {
            snapshot.addBid(bid.getPrice(), bid.getTotalVolume());
            bid = bid.getNext();
        }

        PriceLevel ask = bestAsk;
        while (ask != null && snapshot.getAskCount() < BookSnapshot.MAX_DEPTH) {
            snapshot.addAsk(ask.getPrice(), ask.getTotalVolume());
            ask = ask.getNext();
        }
    }

    public PriceLevel getBestBid() {
        return bestBid;
    }

    public PriceLevel getBestAsk() {
        return bestAsk;
    }

    public OrderEntry getOrder(long orderId) {
        return activeOrders.get(orderId);
    }

    public int getActiveOrderCount() {
        return activeOrders.size();
    }

    public String getSymbol() {
        return symbol;
    }

    public long getTradeSequence() {
        return tradeSequence;
    }
}
