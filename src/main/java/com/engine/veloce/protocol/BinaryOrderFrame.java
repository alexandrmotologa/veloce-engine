package com.engine.veloce.protocol;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Compact 32-byte native binary wire frame for high-speed inter-process order transport.
 * Layout aligns on 8-byte boundaries for single-instruction vectorized CPU loads.
 *
 * Offset  Length  Field
 * 0       8       timestampNs (long)
 * 8       8       orderId (long)
 * 16      8       price (long fixed-point)
 * 24      4       quantity (int)
 * 28      1       side (byte: 1=BID, 2=ASK)
 * 29      1       orderType (byte: 1=LIMIT, 2=MARKET, 3=IOC, 4=FOK, 5=POST_ONLY)
 * 30      2       padding (reserved)
 */
public final class BinaryOrderFrame {

    public static final int FRAME_LENGTH = 32;

    public static final int OFFSET_TIMESTAMP = 0;
    public static final int OFFSET_ORDER_ID = 8;
    public static final int OFFSET_PRICE = 16;
    public static final int OFFSET_QUANTITY = 24;
    public static final int OFFSET_SIDE = 28;
    public static final int OFFSET_ORDER_TYPE = 29;
    public static final int OFFSET_PADDING = 30;

    private BinaryOrderFrame() {}

    public static void encode(MutableDirectBuffer buffer,
                              int offset,
                              long timestampNs,
                              long orderId,
                              long price,
                              int quantity,
                              Side side,
                              OrderType orderType) {
        buffer.putLong(offset + OFFSET_TIMESTAMP, timestampNs);
        buffer.putLong(offset + OFFSET_ORDER_ID, orderId);
        buffer.putLong(offset + OFFSET_PRICE, price);
        buffer.putInt(offset + OFFSET_QUANTITY, quantity);
        buffer.putByte(offset + OFFSET_SIDE, side.getCode());
        buffer.putByte(offset + OFFSET_ORDER_TYPE, orderType.getCode());
        buffer.putShort(offset + OFFSET_PADDING, (short) 0);
    }

    public static long readTimestampNs(DirectBuffer buffer, int offset) {
        return buffer.getLong(offset + OFFSET_TIMESTAMP);
    }

    public static long readOrderId(DirectBuffer buffer, int offset) {
        return buffer.getLong(offset + OFFSET_ORDER_ID);
    }

    public static long readPrice(DirectBuffer buffer, int offset) {
        return buffer.getLong(offset + OFFSET_PRICE);
    }

    public static int readQuantity(DirectBuffer buffer, int offset) {
        return buffer.getInt(offset + OFFSET_QUANTITY);
    }

    public static Side readSide(DirectBuffer buffer, int offset) {
        return Side.fromCode(buffer.getByte(offset + OFFSET_SIDE));
    }

    public static OrderType readOrderType(DirectBuffer buffer, int offset) {
        return OrderType.fromCode(buffer.getByte(offset + OFFSET_ORDER_TYPE));
    }
}
