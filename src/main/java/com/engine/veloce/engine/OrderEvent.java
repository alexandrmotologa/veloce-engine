package com.engine.veloce.engine;

import com.engine.veloce.domain.model.OrderStatus;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.lmax.disruptor.EventFactory;

/**
 * Pre-allocated carrier event inside the LMAX Disruptor ring buffer.
 * Uses 64-byte padding to prevent CPU cache-line bouncing (false sharing).
 */
public class OrderEvent {

    public static final EventFactory<OrderEvent> FACTORY = OrderEvent::new;

    // Front padding (56 bytes + object header = 64 bytes)
    protected long p1, p2, p3, p4, p5, p6, p7;

    private OrderCommandType commandType;
    private long orderId;
    private Side side;
    private OrderType orderType;
    private long price;
    private long quantity;
    private long timestampNs;
    private OrderStatus resultStatus;

    // Back padding (56 bytes)
    protected long p8, p9, p10, p11, p12, p13, p14;

    public void setNewOrder(long orderId,
                            Side side,
                            OrderType orderType,
                            long price,
                            long quantity,
                            long timestampNs) {
        this.commandType = OrderCommandType.NEW_ORDER;
        this.orderId = orderId;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.quantity = quantity;
        this.timestampNs = timestampNs;
        this.resultStatus = null;
    }

    public void setCancel(long orderId, long timestampNs) {
        this.commandType = OrderCommandType.CANCEL_ORDER;
        this.orderId = orderId;
        this.timestampNs = timestampNs;
        this.resultStatus = null;
    }

    public void setReduce(long orderId, long newQty, long timestampNs) {
        this.commandType = OrderCommandType.REDUCE_ORDER;
        this.orderId = orderId;
        this.quantity = newQty;
        this.timestampNs = timestampNs;
        this.resultStatus = null;
    }

    public void setSnapshot(long timestampNs) {
        this.commandType = OrderCommandType.SNAPSHOT_REQUEST;
        this.timestampNs = timestampNs;
        this.resultStatus = null;
    }

    public void reset() {
        this.commandType = null;
        this.orderId = 0;
        this.side = null;
        this.orderType = null;
        this.price = 0;
        this.quantity = 0;
        this.timestampNs = 0;
        this.resultStatus = null;
    }

    public OrderCommandType getCommandType() {
        return commandType;
    }

    public long getOrderId() {
        return orderId;
    }

    public Side getSide() {
        return side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public long getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getTimestampNs() {
        return timestampNs;
    }

    public OrderStatus getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(OrderStatus resultStatus) {
        this.resultStatus = resultStatus;
    }
}
