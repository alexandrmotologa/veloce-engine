package com.engine.veloce.network;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.engine.MatchingEngine;
import com.engine.veloce.protocol.BinaryOrderFrame;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Encapsulates a connected TCP client socket with pre-allocated direct buffers
 * to handle TCP stream fragmentation with zero heap allocations.
 */
public final class ClientConnection {

    private final SocketChannel channel;
    private final MatchingEngine engine;
    private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(8192);
    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(readBuffer);

    public ClientConnection(SocketChannel channel, MatchingEngine engine) {
        this.channel = channel;
        this.engine = engine;
    }

    /**
     * Reads available bytes from the socket channel and processes complete 32-byte binary frames.
     *
     * @return true if connection remains open, false if client disconnected (EOF)
     */
    public boolean readAndProcess() throws IOException {
        int bytesRead = channel.read(readBuffer);
        if (bytesRead == -1) {
            return false; // Client closed connection
        }

        readBuffer.flip();
        int available = readBuffer.remaining();
        int offset = 0;

        // Process all complete 32-byte binary order frames
        while (available >= BinaryOrderFrame.FRAME_LENGTH) {
            long timestampNs = BinaryOrderFrame.readTimestampNs(unsafeBuffer, offset);
            long orderId = BinaryOrderFrame.readOrderId(unsafeBuffer, offset);
            long price = BinaryOrderFrame.readPrice(unsafeBuffer, offset);
            int qty = BinaryOrderFrame.readQuantity(unsafeBuffer, offset);
            Side side = BinaryOrderFrame.readSide(unsafeBuffer, offset);
            OrderType type = BinaryOrderFrame.readOrderType(unsafeBuffer, offset);

            engine.submitOrder(orderId, side, type, price, qty, timestampNs);

            offset += BinaryOrderFrame.FRAME_LENGTH;
            available -= BinaryOrderFrame.FRAME_LENGTH;
        }

        readBuffer.position(offset);
        readBuffer.compact();
        return true;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException ignored) {}
    }
}
