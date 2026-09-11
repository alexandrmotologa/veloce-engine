package com.engine.veloce.engine;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the LMAX Disruptor lock-free RingBuffer pipeline for inbound order events.
 */
public final class OrderDisruptorRing {

    public static final int DEFAULT_RING_SIZE = 1024 * 1024; // 1,048,576 slots (2^20)

    private final Disruptor<OrderEvent> disruptor;
    private final RingBuffer<OrderEvent> ringBuffer;
    private final OrderEventHandler eventHandler;
    private volatile boolean running;

    public OrderDisruptorRing(OrderEventHandler eventHandler,
                              WaitStrategyType waitStrategyType,
                              int ringBufferSize,
                              ProducerType producerType) {
        this.eventHandler = eventHandler;

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "veloce-matcher-core-" + count.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            }
        };

        this.disruptor = new Disruptor<>(
                OrderEvent.FACTORY,
                ringBufferSize,
                threadFactory,
                producerType,
                waitStrategyType.createStrategy()
        );

        this.disruptor.handleEventsWith(eventHandler);
        this.ringBuffer = disruptor.getRingBuffer();
    }

    public synchronized void start() {
        if (!running) {
            disruptor.start();
            running = true;
        }
    }

    public synchronized void shutdown() {
        if (running) {
            disruptor.shutdown();
            running = false;
        }
    }

    /**
     * Publishes a new order command to the ring buffer in O(1) lock-free time.
     */
    public void publishNewOrder(long orderId,
                                Side side,
                                OrderType orderType,
                                long price,
                                long quantity,
                                long timestampNs) {
        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            event.setNewOrder(orderId, side, orderType, price, quantity, timestampNs);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * Publishes an order cancellation to the ring buffer.
     */
    public void publishCancel(long orderId, long timestampNs) {
        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            event.setCancel(orderId, timestampNs);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * Publishes an order quantity reduction to the ring buffer.
     */
    public void publishReduce(long orderId, long newQty, long timestampNs) {
        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            event.setReduce(orderId, newQty, timestampNs);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    public RingBuffer<OrderEvent> getRingBuffer() {
        return ringBuffer;
    }

    public OrderEventHandler getEventHandler() {
        return eventHandler;
    }

    public boolean isRunning() {
        return running;
    }
}
