# VeloceEngine Python SDK

High-throughput client library and trading bot examples for communicating with the VeloceEngine matching core.

## Features

- **Binary TCP Transport**: Streams 32-byte native binary order frames directly to the Java NIO non-blocking server.
- **Microsecond Latency**: Direct binary packing without JSON overhead or intermediary proxies.
- **REST & SSE Integration**: Convenience methods to query L2 book snapshots and stream executions.

## Installation

No external dependencies are required. The SDK relies solely on Python 3 standard libraries (`socket`, `struct`, `urllib`).

```bash
python --version  # Requires Python 3.8+
```

## Quickstart

```python
from veloce_client import VeloceClient, Side, OrderType

# Connect to VeloceEngine running locally
with VeloceClient(host="127.0.0.1", tcp_port=9881, http_port=8080) as client:
    # Submit limit buy order
    client.submit_order(
        order_id=1001,
        side=Side.BID,
        order_type=OrderType.LIMIT,
        price=150.25,
        quantity=100
    )

    # Query latest L2 order book snapshot
    snapshot = client.get_order_book_snapshot()
    print("Best Bid:", snapshot["bestBid"])
    print("Best Ask:", snapshot["bestAsk"])
```

## Running the Example Market Maker Bot

1. Start VeloceEngine:
```bash
mvn exec:java -Dexec.mainClass=com.engine.veloce.cli.VeloceCli -- -s AAPL
```

2. Run the algorithmic trading bot:
```bash
python example_trading_bot.py
```
