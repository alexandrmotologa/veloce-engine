package com.engine.veloce.protocol;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class FixCodecTest {

    @Test
    void shouldDecodeNewOrderSingle() {
        // 35=D|11=12345|54=1|38=100|44=150.25|40=2|59=3|
        String fixMsg = "35=D\u000111=12345\u000154=1\u000138=100\u000144=150.25\u000140=2\u000159=3\u0001";
        byte[] bytes = fixMsg.getBytes(StandardCharsets.US_ASCII);

        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        FixCodec codec = new FixCodec();

        AtomicBoolean handled = new AtomicBoolean();

        codec.decode(buffer, 0, bytes.length, new FixMessageHandler() {
            @Override
            public void onNewOrderSingle(long clOrdId, Side side, OrderType orderType, long price, long qty) {
                assertThat(clOrdId).isEqualTo(12345L);
                assertThat(side).isEqualTo(Side.BID);
                assertThat(orderType).isEqualTo(OrderType.IOC);
                assertThat(price).isEqualTo(1502500L); // 150.25 * 10,000
                assertThat(qty).isEqualTo(100L);
                handled.set(true);
            }

            @Override
            public void onOrderCancelRequest(long clOrdId, long origClOrdId) {}
        });

        assertThat(handled.get()).isTrue();
    }

    @Test
    void shouldDecodeOrderCancelRequest() {
        // 35=F|11=99999|41=12345|
        String fixMsg = "35=F\u000111=99999\u000141=12345\u0001";
        byte[] bytes = fixMsg.getBytes(StandardCharsets.US_ASCII);

        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        FixCodec codec = new FixCodec();

        AtomicBoolean handled = new AtomicBoolean();

        codec.decode(buffer, 0, bytes.length, new FixMessageHandler() {
            @Override
            public void onNewOrderSingle(long clOrdId, Side side, OrderType orderType, long price, long qty) {}

            @Override
            public void onOrderCancelRequest(long clOrdId, long origClOrdId) {
                assertThat(clOrdId).isEqualTo(99999L);
                assertThat(origClOrdId).isEqualTo(12345L);
                handled.set(true);
            }
        });

        assertThat(handled.get()).isTrue();
    }
}
