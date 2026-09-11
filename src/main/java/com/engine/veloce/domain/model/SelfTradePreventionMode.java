package com.engine.veloce.domain.model;

/**
 * Self-Trade Prevention (STP) behavior when an incoming order crosses with
 * a resting order belonging to the same market participant.
 */
public enum SelfTradePreventionMode {
    NONE,
    CANCEL_NEWEST,
    CANCEL_OLDEST,
    DECREMENT_AND_CANCEL
}
