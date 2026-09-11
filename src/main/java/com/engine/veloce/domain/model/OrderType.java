package com.engine.veloce.domain.model;

/**
 * Order execution types.
 */
public enum OrderType {
    LIMIT((byte) 1),
    MARKET((byte) 2),
    IOC((byte) 3),        // Immediate-or-Cancel
    FOK((byte) 4),        // Fill-or-Kill
    POST_ONLY((byte) 5);  // Maker-only (reject if crosses spread)

    private final byte code;

    OrderType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static OrderType fromCode(byte code) {
        return switch (code) {
            case 1 -> LIMIT;
            case 2 -> MARKET;
            case 3 -> IOC;
            case 4 -> FOK;
            case 5 -> POST_ONLY;
            default -> throw new IllegalArgumentException("Unknown order type code: " + code);
        };
    }
}
