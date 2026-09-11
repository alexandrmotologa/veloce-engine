# veloce-engine

VeloceEngine is an in-memory limit order book and matching engine written in Java 21. It processes orders using price-time (FIFO) priority with zero heap allocation on the hot path.

The design relies on a single-writer architecture powered by an LMAX Disruptor ring buffer, pre-allocated object pools, and Agrona primitive collections.

## Core Features

- Price-time priority matching for Limit, Market, Immediate-Or-Cancel (IOC), Fill-Or-Kill (FOK), and Post-Only orders.
- Zero runtime heap allocation during order submission, matching, and cancellation.
- Pre-allocated object pools for order nodes, price levels, and trade events.
- Lock-free single-writer core running on an LMAX Disruptor 4.x ring buffer with 1,048,576 slots.
- Fixed-point pricing using 64-bit integers to eliminate IEEE 754 floating-point inaccuracies.
- Constant-time O(1) order cancellation using intrusive doubly-linked list nodes and primitive hash maps.
- Binary NASDAQ ITCH 5.0 and FIX 4.4 decoders operating directly on off-heap byte buffers.
- Terminal user interface with live market depth ladder, trade tape, and HdrHistogram latency percentiles.

## Architecture

```
                    Inbound FIX / ITCH / Binary Wire
                                   │
                                   ▼
                   ┌───────────────────────────────┐
                   │   LMAX Disruptor Ring Buffer  │
                   │   (1,048,576 Pre-Allocated)   │
                   └───────────────┬───────────────┘
                                   │
                                   ▼
        ┌─────────────────────────────────────────────────────┐
        │        Single-Writer Pinned Consumer Thread         │
        │                                                     │
        │  ┌─────────────────┐       ┌─────────────────────┐  │
        │  │ Matching Engine │ <===> │   Limit Order Book  │  │
        │  └────────┬────────┘       │  - Bids (Desc Long) │  │
        │           │                │  - Asks (Asc Long)  │  │
        │           ▼                └─────────────────────┘  │
        │  ┌─────────────────┐                                │
        │  │  Trade Events   │                                │
        │  └─────────────────┘                                │
        └──────────────────────────┬──────────────────────────┘
                                   │
                                   ▼
                    Outbound Trade Stream & TUI
```

### Mechanical Sympathy and Zero Allocation

Matching engines on modern x86-64 hardware are primarily bounded by memory latency and cache misses. VeloceEngine addresses this through:

1. **Pre-allocated pools**: All `OrderEntry` nodes and `PriceLevel` structures are allocated at startup in contiguous arrays. Releasing an order returns it to an index-backed free list without generating GC garbage.
2. **Primitive collections**: Active order lookups and price points use Agrona `Long2ObjectHashMap`, avoiding Java `Long` wrapper objects.
3. **Cache-line isolation**: Mutable sequence numbers and pointers use 64-byte padding to prevent CPU cache-line bouncing (false sharing) between producer and consumer threads.
4. **Intrusive links**: Pointers for price levels and order queues live directly inside the order records, avoiding separate node wrapper instances.

## Repository Layout

```
src/main/java/com/engine/veloce/
├── domain/
│   ├── book/          # LimitOrderBook, PriceLevel, and OrderEntry
│   ├── model/         # Side, OrderType, and OrderStatus enums
│   ├── pool/          # Zero-allocation ObjectPool and OrderEntryPool
│   └── trade/         # TradeEvent and BookSnapshot
├── engine/            # MatchingEngine, OrderDisruptorRing, and OrderEventHandler
├── protocol/          # ItchParser, FixCodec, and BinaryOrderFrame
├── telemetry/         # LatencyRecorder and HdrHistogram wrappers
└── tui/               # Terminal market depth ladder and interactive CLI
```

## Building and Testing

Requirements:
- Java Development Kit (JDK) 21 or later
- Apache Maven 3.9+

Compile and run all unit tests:

```bash
mvn clean test
```

Run the zero-allocation assertion test:

```bash
mvn test -Dtest=ZeroGcAllocationTest
```

Package the executable fat JAR:

```bash
mvn clean package -DskipTests
```

## Running the Engine

Start the interactive terminal interface with synthetic market flow:

```bash
java -XX:-RestrictContended -jar target/veloce-engine-1.0.0-SNAPSHOT.jar run --synthetic-load
```

Available options:

```text
Usage: veloce-engine run [-hV] [--synthetic-load] [--rate=<ordersPerSec>]
                         [--symbol=<symbol>] [--wait-strategy=<strategy>]

Runs the matching engine with optional terminal market depth display.

  --synthetic-load             Generates random bids and asks for testing.
  --rate=<ordersPerSec>        Synthetic order injection rate (default: 100000).
  --symbol=<symbol>            Instrument identifier (default: AAPL).
  --wait-strategy=<strategy>   Disruptor wait strategy: BUSY_SPIN, YIELDING,
                               or SLEEPING (default: BUSY_SPIN).
  -h, --help                   Show this help message and exit.
  -V, --version                Print version information and exit.
```

## Microbenchmarks

Run the JMH performance harness:

```bash
java -jar target/benchmarks.jar MatchingBenchmark -wi 3 -i 5 -f 1
```

Target metrics on modern x86-64 hardware (pinned thread, Epsilon GC or Generational ZGC):

| Metric | Target |
| :--- | :--- |
| Throughput | > 5,000,000 matches / second |
| Median Latency (p50) | < 1.0 microsecond |
| 99th Percentile Latency (p99) | < 3.5 microseconds |
| 99.9th Percentile Latency (p99.9) | < 6.0 microseconds |
| Hot Path Heap Allocation | 0 bytes / order |

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
