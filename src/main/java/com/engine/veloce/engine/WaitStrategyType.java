package com.engine.veloce.engine;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;

/**
 * Supported Disruptor wait strategies.
 */
public enum WaitStrategyType {
    BUSY_SPIN,
    YIELDING,
    SLEEPING,
    BLOCKING;

    public WaitStrategy createStrategy() {
        return switch (this) {
            case BUSY_SPIN -> new BusySpinWaitStrategy();
            case YIELDING -> new YieldingWaitStrategy();
            case SLEEPING -> new SleepingWaitStrategy();
            case BLOCKING -> new BlockingWaitStrategy();
        };
    }
}
