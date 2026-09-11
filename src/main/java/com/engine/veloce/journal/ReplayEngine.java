package com.engine.veloce.journal;

import com.engine.veloce.domain.book.LimitOrderBook;
import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.protocol.BinaryOrderFrame;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Replays past order sequences from a Write-Ahead Log to deterministically reconstruct book state.
 */
public final class ReplayEngine {

    @FunctionalInterface
    public interface ReplayConsumer {
        void onReplay(long sequence,
                      long orderId,
                      Side side,
                      OrderType type,
                      long price,
                      long quantity,
                      long timestampNs);
    }

    /**
     * Replays all valid records from the specified WAL file into the consumer callback.
     *
     * @return number of records successfully recovered and replayed
     */
    public static long replay(File file, ReplayConsumer consumer) throws Exception {
        if (!file.exists() || file.length() < WriteAheadLog.RECORD_SIZE) {
            return 0;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {
            long fileSize = file.length();
            MappedByteBuffer mmap = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            try {
                UnsafeBuffer buffer = new UnsafeBuffer(mmap);

                int pos = 0;
                long replayedCount = 0;

                while (pos + WriteAheadLog.RECORD_SIZE <= fileSize) {
                    int magic = buffer.getInt(pos);
                    if (magic != WriteAheadLog.MAGIC) {
                        // End of valid log stream
                        break;
                    }

                    int length = buffer.getInt(pos + 4);
                    long sequence = buffer.getLong(pos + 8);

                    int frameOffset = pos + WriteAheadLog.ENTRY_HEADER_SIZE;
                    long timestampNs = BinaryOrderFrame.readTimestampNs(buffer, frameOffset);
                    long orderId = BinaryOrderFrame.readOrderId(buffer, frameOffset);
                    long price = BinaryOrderFrame.readPrice(buffer, frameOffset);
                    int qty = BinaryOrderFrame.readQuantity(buffer, frameOffset);
                    Side side = BinaryOrderFrame.readSide(buffer, frameOffset);
                    OrderType type = BinaryOrderFrame.readOrderType(buffer, frameOffset);

                    consumer.onReplay(sequence, orderId, side, type, price, qty, timestampNs);
                    replayedCount++;
                    pos += WriteAheadLog.RECORD_SIZE;
                }

                return replayedCount;
            } finally {
                org.agrona.IoUtil.unmap(mmap);
            }
        }
    }

    /**
     * Replays directly into a LimitOrderBook.
     */
    public static long replayIntoBook(File file, LimitOrderBook book) throws Exception {
        return replay(file, (seq, orderId, side, type, price, qty, timestampNs) ->
                book.processOrder(orderId, side, type, price, qty, timestampNs)
        );
    }
}
