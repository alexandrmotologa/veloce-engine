package com.engine.veloce.protocol;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;

/**
 * Zero-allocation callback interface for parsed FIX 4.4 messages.
 */
public interface FixMessageHandler {

    void onNewOrderSingle(long clOrdId,
                          Side side,
                          OrderType orderType,
                          long price,
                          long qty);

    void onOrderCancelRequest(long clOrdId,
                              long origClOrdId);
}
