package com.engine.veloce.domain.model;

/**
 * Order side representation.
 */
public enum Side {
    BID((byte) 1),
    ASK((byte) 2);

    private final byte code;

    Side(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public Side opposite() {
        return this == BID ? ASK : BID;
    }

    public static Side fromCode(byte code) {
        return switch (code) {
            case 1 -> BID;
            case 2 -> ASK;
            default -> throw new IllegalArgumentException("Unknown side code: " + code);
        };
    }
}
