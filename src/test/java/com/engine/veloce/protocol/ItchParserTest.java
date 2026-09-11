package com.engine.veloce.protocol;

import com.engine.veloce.domain.model.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class ItchParserTest {

    @Test
    void shouldParseAddOrderMessage() {
        ByteBuffer raw = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN);

        raw.put((byte) 'A');              // 0: MsgType
        raw.putShort((short) 1);          // 1-2: Locate
        raw.putShort((short) 10);         // 3-4: Tracking
        raw.putShort((short) 0);          // 5-6: Timestamp hi
        raw.putInt(12345678);             // 7-10: Timestamp lo
        raw.putLong(999888777L);          // 11-18: OrderRef
        raw.put((byte) 'B');              // 19: Side (Buy)
        raw.putInt(200);                  // 20-23: Shares
        raw.put("AAPL    ".getBytes(StandardCharsets.US_ASCII)); // 24-31: Symbol
        raw.putInt(1502500);              // 32-35: Price ($150.2500)

        UnsafeBuffer buffer = new UnsafeBuffer(raw.array());
        ItchParser parser = new ItchParser();

        AtomicBoolean handled = new AtomicBoolean();

        int bytesParsed = parser.parse(buffer, 0, new ItchMessageHandler() {
            @Override
            public void onAddOrder(long timestampNs, long orderReference, Side side,
                                   int shares, long price, byte[] stockSymbol, int offset, int length) {
                assertThat(orderReference).isEqualTo(999888777L);
                assertThat(side).isEqualTo(Side.BID);
                assertThat(shares).isEqualTo(200);
                assertThat(price).isEqualTo(1502500L);
                String symbol = new String(stockSymbol, offset, length, StandardCharsets.US_ASCII).trim();
                assertThat(symbol).isEqualTo("AAPL");
                handled.set(true);
            }

            @Override
            public void onOrderExecuted(long timestampNs, long orderReference, int executedShares, long matchNumber) {}

            @Override
            public void onOrderCancel(long timestampNs, long orderReference, int canceledShares) {}

            @Override
            public void onOrderDelete(long timestampNs, long orderReference) {}
        });

        assertThat(bytesParsed).isEqualTo(36);
        assertThat(handled.get()).isTrue();
    }

    @Test
    void shouldParseOrderExecutedMessage() {
        ByteBuffer raw = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN);

        raw.put((byte) 'E');              // MsgType
        raw.putShort((short) 1);
        raw.putShort((short) 10);
        raw.putShort((short) 0);
        raw.putInt(12345678);
        raw.putLong(999888777L);          // OrderRef
        raw.putInt(75);                   // Executed shares
        raw.putLong(555444333L);          // MatchNumber

        UnsafeBuffer buffer = new UnsafeBuffer(raw.array());
        ItchParser parser = new ItchParser();
        AtomicBoolean handled = new AtomicBoolean();

        int bytesParsed = parser.parse(buffer, 0, new ItchMessageHandler() {
            @Override
            public void onAddOrder(long timestampNs, long orderReference, Side side, int shares, long price, byte[] stockSymbol, int symbolOffset, int symbolLength) {}

            @Override
            public void onOrderExecuted(long timestampNs, long orderReference, int executedShares, long matchNumber) {
                assertThat(orderReference).isEqualTo(999888777L);
                assertThat(executedShares).isEqualTo(75);
                assertThat(matchNumber).isEqualTo(555444333L);
                handled.set(true);
            }

            @Override
            public void onOrderCancel(long timestampNs, long orderReference, int canceledShares) {}

            @Override
            public void onOrderDelete(long timestampNs, long orderReference) {}
        });

        assertThat(bytesParsed).isEqualTo(31);
        assertThat(handled.get()).isTrue();
    }
}
