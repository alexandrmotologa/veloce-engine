package com.engine.veloce.protocol;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryOrderFrameTest {

    @Test
    void shouldEncodeAndDecode32ByteFrame() {
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(64));

        long timestamp = 1718000000123456L;
        long orderId = 9876543210L;
        long price = 1005000L; // $100.5000
        int qty = 500;
        Side side = Side.BID;
        OrderType type = OrderType.IOC;

        BinaryOrderFrame.encode(buffer, 0, timestamp, orderId, price, qty, side, type);

        assertThat(BinaryOrderFrame.readTimestampNs(buffer, 0)).isEqualTo(timestamp);
        assertThat(BinaryOrderFrame.readOrderId(buffer, 0)).isEqualTo(orderId);
        assertThat(BinaryOrderFrame.readPrice(buffer, 0)).isEqualTo(price);
        assertThat(BinaryOrderFrame.readQuantity(buffer, 0)).isEqualTo(qty);
        assertThat(BinaryOrderFrame.readSide(buffer, 0)).isEqualTo(side);
        assertThat(BinaryOrderFrame.readOrderType(buffer, 0)).isEqualTo(type);
    }
}
