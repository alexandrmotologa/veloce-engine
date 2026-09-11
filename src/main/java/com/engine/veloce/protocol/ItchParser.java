package com.engine.veloce.protocol;

import com.engine.veloce.domain.model.Side;
import org.agrona.DirectBuffer;

import java.nio.ByteOrder;

/**
 * Zero-copy binary parser for NASDAQ ITCH 5.0 protocol messages.
 * Reads big-endian network byte representations directly from DirectBuffer.
 */
public final class ItchParser {

    public static final byte MSG_TYPE_ADD_ORDER = (byte) 'A';
    public static final byte MSG_TYPE_ORDER_EXECUTED = (byte) 'E';
    public static final byte MSG_TYPE_ORDER_CANCEL = (byte) 'X';
    public static final byte MSG_TYPE_ORDER_DELETE = (byte) 'D';

    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private final byte[] symbolBuffer = new byte[8];

    /**
     * Parses an ITCH 5.0 binary frame and dispatches to the provided handler without heap allocation.
     *
     * @param buffer memory buffer holding the frame
     * @param offset starting offset
     * @param handler callback receiver
     * @return number of bytes consumed by the message
     */
    public int parse(DirectBuffer buffer, int offset, ItchMessageHandler handler) {
        byte messageType = buffer.getByte(offset);

        return switch (messageType) {
            case MSG_TYPE_ADD_ORDER -> parseAddOrder(buffer, offset, handler);
            case MSG_TYPE_ORDER_EXECUTED -> parseOrderExecuted(buffer, offset, handler);
            case MSG_TYPE_ORDER_CANCEL -> parseOrderCancel(buffer, offset, handler);
            case MSG_TYPE_ORDER_DELETE -> parseOrderDelete(buffer, offset, handler);
            default -> 0;
        };
    }

    private int parseAddOrder(DirectBuffer buffer, int offset, ItchMessageHandler handler) {
        long timestampNs = read48BitTimestamp(buffer, offset + 5);
        long orderRef = buffer.getLong(offset + 11, BYTE_ORDER);
        byte sideByte = buffer.getByte(offset + 19);
        Side side = (sideByte == 'B') ? Side.BID : Side.ASK;
        int shares = buffer.getInt(offset + 20, BYTE_ORDER);

        buffer.getBytes(offset + 24, symbolBuffer, 0, 8);
        int rawPrice = buffer.getInt(offset + 32, BYTE_ORDER);
        long price = rawPrice; // scaled by 10,000

        handler.onAddOrder(timestampNs, orderRef, side, shares, price, symbolBuffer, 0, 8);
        return 36;
    }

    private int parseOrderExecuted(DirectBuffer buffer, int offset, ItchMessageHandler handler) {
        long timestampNs = read48BitTimestamp(buffer, offset + 5);
        long orderRef = buffer.getLong(offset + 11, BYTE_ORDER);
        int executedShares = buffer.getInt(offset + 19, BYTE_ORDER);
        long matchNumber = buffer.getLong(offset + 23, BYTE_ORDER);

        handler.onOrderExecuted(timestampNs, orderRef, executedShares, matchNumber);
        return 31;
    }

    private int parseOrderCancel(DirectBuffer buffer, int offset, ItchMessageHandler handler) {
        long timestampNs = read48BitTimestamp(buffer, offset + 5);
        long orderRef = buffer.getLong(offset + 11, BYTE_ORDER);
        int canceledShares = buffer.getInt(offset + 19, BYTE_ORDER);

        handler.onOrderCancel(timestampNs, orderRef, canceledShares);
        return 23;
    }

    private int parseOrderDelete(DirectBuffer buffer, int offset, ItchMessageHandler handler) {
        long timestampNs = read48BitTimestamp(buffer, offset + 5);
        long orderRef = buffer.getLong(offset + 11, BYTE_ORDER);

        handler.onOrderDelete(timestampNs, orderRef);
        return 19;
    }

    private static long read48BitTimestamp(DirectBuffer buffer, int offset) {
        long hi = buffer.getShort(offset, BYTE_ORDER) & 0xFFFFL;
        long lo = buffer.getInt(offset + 2, BYTE_ORDER) & 0xFFFFFFFFL;
        return (hi << 32) | lo;
    }
}
