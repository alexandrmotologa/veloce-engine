package com.engine.veloce.domain.book;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PriceLevelTest {

    private PriceLevel level;

    @BeforeEach
    void setUp() {
        level = new PriceLevel();
        level.init(10050, 0);
    }

    @Test
    void shouldAppendAndMaintainFifoOrder() {
        OrderEntry o1 = new OrderEntry();
        o1.init(1, 10050, 10, 1000, Side.BID, OrderType.LIMIT, null, 0);

        OrderEntry o2 = new OrderEntry();
        o2.init(2, 10050, 20, 1001, Side.BID, OrderType.LIMIT, null, 1);

        level.append(o1);
        level.append(o2);

        assertThat(level.getOrderCount()).isEqualTo(2);
        assertThat(level.getTotalVolume()).isEqualTo(30);
        assertThat(level.getHead()).isSameAs(o1);
        assertThat(level.getTail()).isSameAs(o2);

        OrderEntry polled = level.poll();
        assertThat(polled).isSameAs(o1);
        assertThat(level.getOrderCount()).isEqualTo(1);
        assertThat(level.getTotalVolume()).isEqualTo(20);
        assertThat(level.getHead()).isSameAs(o2);
        assertThat(level.getTail()).isSameAs(o2);
    }

    @Test
    void shouldRemoveMiddleNodeInConstantTime() {
        OrderEntry o1 = new OrderEntry();
        o1.init(1, 10050, 10, 1000, Side.BID, OrderType.LIMIT, null, 0);
        OrderEntry o2 = new OrderEntry();
        o2.init(2, 10050, 20, 1001, Side.BID, OrderType.LIMIT, null, 1);
        OrderEntry o3 = new OrderEntry();
        o3.init(3, 10050, 30, 1002, Side.BID, OrderType.LIMIT, null, 2);

        level.append(o1);
        level.append(o2);
        level.append(o3);

        assertThat(level.getOrderCount()).isEqualTo(3);
        assertThat(level.getTotalVolume()).isEqualTo(60);

        // Remove middle node o2
        level.remove(o2);

        assertThat(level.getOrderCount()).isEqualTo(2);
        assertThat(level.getTotalVolume()).isEqualTo(40);
        assertThat(level.getHead()).isSameAs(o1);
        assertThat(level.getTail()).isSameAs(o3);
        assertThat(o1.getNext()).isSameAs(o3);
        assertThat(o3.getPrev()).isSameAs(o1);
        assertThat(o2.getParentLevel()).isNull();
    }
}
