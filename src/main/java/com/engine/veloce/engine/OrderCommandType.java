package com.engine.veloce.engine;

/**
 * Commands handled by the single-writer Disruptor ring buffer.
 */
public enum OrderCommandType {
    NEW_ORDER,
    CANCEL_ORDER,
    REDUCE_ORDER,
    SNAPSHOT_REQUEST
}
