package com.engine.veloce.protocol;

import com.engine.veloce.domain.model.Side;

/**
 * Zero-allocation callback interface for parsed NASDAQ ITCH 5.0 messages.
 */
public interface ItchMessageHandler {

    void onAddOrder(long timestampNs,
                    long orderReference,
                    Side side,
                    int shares,
                    long price,
                    byte[] stockSymbol,
                    int symbolOffset,
                    int symbolLength);

    void onOrderExecuted(long timestampNs,
                         long orderReference,
                         int executedShares,
                         long matchNumber);

    void onOrderCancel(long timestampNs,
                       long orderReference,
                       int canceledShares);

    void onOrderDelete(long timestampNs,
                       long orderReference);
}
