# VeloceEngine Architecture Specification

This document details the internal design, memory layout, concurrency model, and data structures used in VeloceEngine.

## Concurrency Model: Single-Writer Pattern

Traditional multi-threaded trading engines use mutual exclusion locks (`synchronized` blocks, `ReentrantLock`) or concurrent collections (`ConcurrentSkipListMap`) to protect order book state across multiple threads. Under high-frequency order arrival, lock acquisition induces thread preemption, operating system context switches, and cache coherency invalidation storms across CPU cores.

VeloceEngine uses the single-writer architecture introduced by the LMAX Disruptor:

1. Inbound network connections, API handlers, and synthetic order generators act as producers.
2. Producers write serialized order commands into a shared, bounded, power-of-two ring buffer (`Disruptor<OrderEvent>` with 1,048,576 slots).
3. A single consumer thread, pinned to a dedicated CPU core, reads orders sequentially from the ring buffer and mutates the `LimitOrderBook`.
4. Because only one thread ever reads or writes the order book data structures, the matching engine executes without locks, atomic primitives, or memory fences on internal book operations.
5. Outbound trade events are published into a secondary ring buffer for asynchronous logging, market data feeds, and user interface rendering.

## Data Structures

### Intrusive Doubly-Linked List (PriceLevel)

Each distinct price point in the book is represented by a `PriceLevel` containing a FIFO queue of `OrderEntry` nodes.

To avoid allocating separate wrapper list nodes, `OrderEntry` implements intrusive pointers:
- `prev`: Reference to the preceding order at the same price point.
- `next`: Reference to the succeeding order at the same price point.
- `parentLevel`: Direct pointer to the containing `PriceLevel`.

Appending an order to the tail of a price level runs in O(1) time. Removing an filled order from the head runs in O(1) time. Removing an arbitrary order upon cancellation or modification runs in O(1) time without list traversal because the node contains direct pointers to its neighbours.

### Fast Price and Order Indices

`LimitOrderBook` maintains two specialized index maps powered by Agrona primitive collections:

- `Long2ObjectHashMap<PriceLevel>`: Maps price (as a fixed-point `long`) to its corresponding `PriceLevel`. Provides O(1) lookup when routing an incoming limit order.
- `Long2ObjectHashMap<OrderEntry>`: Maps `orderId` to its active `OrderEntry` pointer. Allows immediate O(1) cancellation and volume amendment without scanning price levels.

### Top-of-Book Retrieval

Active price levels are organized in intrusive doubly-linked chains:
- Bids are linked in descending order.
- Asks are linked in ascending order.
- `bestBid` and `bestAsk` hold direct pointers to the respective heads of the chains.

When an incoming order crosses the spread, the engine accesses `bestBid` or `bestAsk` in O(1) time. When a level is completely exhausted, the engine updates the head pointer to `bestBid.next` or `bestAsk.next` in O(1) time.

## Zero-Allocation Memory Management

Garbage collection pauses introduce tail latency spikes (jitter) in the order of milliseconds. VeloceEngine achieves zero runtime allocations during continuous matching through pre-allocation:

### Contiguous Object Pools

At startup, `OrderEntryPool` pre-allocates an array of 1,000,000 `OrderEntry` instances:
- An internal pointer array tracks available indices (free list).
- Acquiring an entry increments the index head and returns a pre-existing reference.
- Releasing an entry resets its fields and returns the index to the free list.
- If the pool capacity is exceeded, an error status is returned instead of triggering heap growth.

`TradeEventPool` similarly pre-allocates outbound trade structures.

### Cache-Line Padding

On modern x86-64 processors, cache lines are 64 bytes wide. If independent variables modified by different CPU cores reside on the same 64-byte boundary, hardware cache-coherency protocols force continuous cache invalidations (false sharing).

Critical pointers, sequence counters, and thread-adjacent state use 64-byte padding (using `-XX:-RestrictContended` or explicit dummy `long` fields) to isolate hot cache lines.
