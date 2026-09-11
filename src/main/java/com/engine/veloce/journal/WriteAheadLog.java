package com.engine.veloce.journal;

import com.engine.veloce.domain.model.OrderType;
import com.engine.veloce.domain.model.Side;
import com.engine.veloce.protocol.BinaryOrderFrame;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Ultra-low-latency Write-Ahead Log (WAL) backed by a memory-mapped file.
 * Appends binary order events sequentially with zero heap allocations.
 */
public final class WriteAheadLog implements AutoCloseable {

    public static final int MAGIC = 0x564C4345; // "VLCE"
    public static final int ENTRY_HEADER_SIZE = 16; // magic (4) + length (4) + sequence (8)
    public static final int RECORD_SIZE = ENTRY_HEADER_SIZE + BinaryOrderFrame.FRAME_LENGTH; // 48 bytes

    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final MappedByteBuffer mmap;
    private final UnsafeBuffer buffer;
    private final long capacity;

    private int writePosition = 0;
    private long sequenceNumber = 0;

    public WriteAheadLog(File file, long capacityBytes) throws Exception {
        this.capacity = capacityBytes;
        boolean isNew = !file.exists() || file.length() == 0;
        this.raf = new RandomAccessFile(file, "rw");
        if (isNew) {
            this.raf.setLength(capacityBytes);
        }
        this.channel = raf.getChannel();
        this.mmap = channel.map(FileChannel.MapMode.READ_WRITE, 0, capacityBytes);
        this.buffer = new UnsafeBuffer(mmap);
    }

    /**
     * Appends an order event to the WAL in O(1) time.
     *
     * @return sequence number assigned to this entry
     */
    public synchronized long appendOrder(long orderId,
                                        Side side,
                                        OrderType type,
                                        long price,
                                        long quantity,
                                        long timestampNs) {
        if (writePosition + RECORD_SIZE > capacity) {
            throw new IllegalStateException("Write-Ahead Log capacity exceeded");
        }

        long seq = ++sequenceNumber;
        int pos = writePosition;

        // Write Header
        buffer.putInt(pos, MAGIC);
        buffer.putInt(pos + 4, BinaryOrderFrame.FRAME_LENGTH);
        buffer.putLong(pos + 8, seq);

        // Write Frame
        BinaryOrderFrame.encode(buffer, pos + ENTRY_HEADER_SIZE, timestampNs, orderId, price, (int) quantity, side, type);

        writePosition += RECORD_SIZE;
        return seq;
    }

    public void flush() {
        mmap.force();
    }

    public int getWritePosition() {
        return writePosition;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public synchronized void close() throws Exception {
        flush();
        org.agrona.IoUtil.unmap(mmap);
        channel.close();
        raf.close();
    }
}
