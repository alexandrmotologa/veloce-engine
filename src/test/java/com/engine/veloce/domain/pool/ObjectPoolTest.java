package com.engine.veloce.domain.pool;

import com.engine.veloce.domain.book.OrderEntry;
import com.engine.veloce.domain.book.PriceLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectPoolTest {

    @Test
    void shouldAcquireAndRecycleOrderEntries() {
        OrderEntryPool pool = new OrderEntryPool(10);
        assertThat(pool.available()).isEqualTo(10);

        OrderEntry entry1 = pool.acquire();
        assertThat(entry1).isNotNull();
        assertThat(pool.available()).isEqualTo(9);

        OrderEntry entry2 = pool.acquire();
        assertThat(entry2).isNotNull();
        assertThat(entry2).isNotSameAs(entry1);
        assertThat(pool.available()).isEqualTo(8);

        pool.release(entry1);
        assertThat(pool.available()).isEqualTo(9);

        OrderEntry recycled = pool.acquire();
        assertThat(recycled).isSameAs(entry1);
        assertThat(pool.available()).isEqualTo(8);
    }

    @Test
    void shouldHandleExhaustionGracefully() {
        OrderEntryPool pool = new OrderEntryPool(2);
        OrderEntry e1 = pool.acquire();
        OrderEntry e2 = pool.acquire();

        assertThat(e1).isNotNull();
        assertThat(e2).isNotNull();
        assertThat(pool.isExhausted()).isTrue();

        OrderEntry e3 = pool.acquire();
        assertThat(e3).isNull();

        pool.release(e1);
        assertThat(pool.isExhausted()).isFalse();
        OrderEntry e4 = pool.acquire();
        assertThat(e4).isSameAs(e1);
    }

    @Test
    void shouldAcquireAndRecyclePriceLevels() {
        PriceLevelPool pool = new PriceLevelPool(5);
        PriceLevel level = pool.acquire(10050);

        assertThat(level).isNotNull();
        assertThat(level.getPrice()).isEqualTo(10050);
        assertThat(pool.available()).isEqualTo(4);

        pool.release(level);
        assertThat(pool.available()).isEqualTo(5);
    }
}
