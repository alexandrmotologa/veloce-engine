package com.engine.veloce.domain.model;

/**
 * Order lifecycle status.
 */
public enum OrderStatus {
    NEW((byte) 1),
    PARTIALLY_FILLED((byte) 2),
    FILLED((byte) 3),
    CANCELED((byte) 4),
    REJECTED((byte) 5);

    private final byte code;

    OrderStatus(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static OrderStatus fromCode(byte code) {
        return switch (code) {
            case 1 -> NEW;
            case 2 -> PARTIALLY_FILLED;
            case 3 -> FILLED;
            case 4 -> CANCELED;
            case 5 -> REJECTED;
            default -> throw new IllegalArgumentException("Unknown status code: " + code);
        };
    }
}
