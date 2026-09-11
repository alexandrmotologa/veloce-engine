package com.engine.veloce.protocol;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import org.agrona.DirectBuffer;

/**
 * High-performance zero-copy FIX 4.4 tag-value message decoder.
 * Scans byte buffers directly without string instantiations.
 */
public final class FixCodec {

    public static final byte SOH = 0x01;

    public void decode(DirectBuffer buffer, int offset, int length, FixMessageHandler handler) {
        int end = offset + length;
        int cursor = offset;

        char msgType = ' ';
        long clOrdId = 0;
        long origClOrdId = 0;
        Side side = null;
        OrderType orderType = OrderType.LIMIT;
        long price = 0;
        long qty = 0;
        char timeInForce = '0';

        while (cursor < end) {
            // Parse Tag
            int tag = 0;
            while (cursor < end && buffer.getByte(cursor) != '=') {
                byte b = buffer.getByte(cursor++);
                if (b >= '0' && b <= '9') {
                    tag = tag * 10 + (b - '0');
                }
            }
            cursor++; // skip '='

            int valStart = cursor;
            while (cursor < end && buffer.getByte(cursor) != SOH) {
                cursor++;
            }
            int valLen = cursor - valStart;
            cursor++; // skip SOH

            switch (tag) {
                case 35 -> msgType = (char) buffer.getByte(valStart);
                case 11 -> clOrdId = parseLong(buffer, valStart, valLen);
                case 41 -> origClOrdId = parseLong(buffer, valStart, valLen);
                case 54 -> {
                    byte s = buffer.getByte(valStart);
                    side = (s == '1') ? Side.BID : Side.ASK;
                }
                case 38 -> qty = parseLong(buffer, valStart, valLen);
                case 44 -> price = parsePrice(buffer, valStart, valLen);
                case 40 -> {
                    byte ot = buffer.getByte(valStart);
                    if (ot == '1') {
                        orderType = OrderType.MARKET;
                    } else {
                        orderType = OrderType.LIMIT;
                    }
                }
                case 59 -> {
                    timeInForce = (char) buffer.getByte(valStart);
                    if (timeInForce == '3') {
                        orderType = OrderType.IOC;
                    } else if (timeInForce == '4') {
                        orderType = OrderType.FOK;
                    }
                }
            }
        }

        if (msgType == 'D') {
            handler.onNewOrderSingle(clOrdId, side, orderType, price, qty);
        } else if (msgType == 'F') {
            handler.onOrderCancelRequest(clOrdId, origClOrdId);
        }
    }

    private static long parseLong(DirectBuffer buffer, int start, int length) {
        long result = 0;
        int i = 0;
        boolean negative = false;
        if (length > 0 && buffer.getByte(start) == '-') {
            negative = true;
            i = 1;
        }
        for (; i < length; i++) {
            byte b = buffer.getByte(start + i);
            if (b >= '0' && b <= '9') {
                result = result * 10 + (b - '0');
            }
        }
        return negative ? -result : result;
    }

    /**
     * Parses price with fixed 4 decimal places (scaled by 10,000).
     */
    private static long parsePrice(DirectBuffer buffer, int start, int length) {
        long integerPart = 0;
        long fractionalPart = 0;
        int fractionDigits = 0;
        boolean inFraction = false;

        for (int i = 0; i < length; i++) {
            byte b = buffer.getByte(start + i);
            if (b == '.') {
                inFraction = true;
            } else if (b >= '0' && b <= '9') {
                if (!inFraction) {
                    integerPart = integerPart * 10 + (b - '0');
                } else if (fractionDigits < 4) {
                    fractionalPart = fractionalPart * 10 + (b - '0');
                    fractionDigits++;
                }
            }
        }

        while (fractionDigits < 4) {
            fractionalPart *= 10;
            fractionDigits++;
        }

        return integerPart * 10_000L + fractionalPart;
    }
}
