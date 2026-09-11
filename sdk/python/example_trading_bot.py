"""
Example Algorithmic Market Making Bot for VeloceEngine.

Demonstrates high-frequency automated quotation and liquidity provisioning
over low-latency TCP sockets using the VeloceEngine Python SDK.
"""

import time
import random
from veloce_client import VeloceClient, Side, OrderType


def run_market_maker(iterations: int = 50, batch_size: int = 20):
    print("=" * 65)
    print("  VeloceEngine High-Frequency Trading Bot Demo")
    print("=" * 65)

    client = VeloceClient(host="127.0.0.1", tcp_port=9881, http_port=8080)
    
    try:
        print("[*] Connecting to VeloceEngine NIO socket on port 9881...")
        client.connect()
        print("[+] TCP connection established with TCP_NODELAY.")
    except ConnectionRefusedError:
        print("[-] Could not connect to VeloceEngine. Make sure the server is running:")
        print("    mvn exec:java -Dexec.mainClass=com.engine.veloce.cli.VeloceCli -- -s AAPL")
        return

    mid_price = 150.00
    order_id_counter = 100_000

    print(f"[*] Dispatching {iterations} batches of {batch_size} orders each...")
    start_time = time.perf_counter()
    total_orders = 0

    for i in range(iterations):
        batch = []
        for _ in range(batch_size):
            order_id_counter += 1
            # Slightly jitter mid price
            spread_offset = round(random.uniform(0.05, 0.50), 2)
            qty = random.randint(10, 100)

            if random.random() < 0.5:
                # Place BID
                price = round(mid_price - spread_offset, 2)
                side = Side.BID
            else:
                # Place ASK
                price = round(mid_price + spread_offset, 2)
                side = Side.ASK

            # 10% chance of aggressive crossing order
            if random.random() < 0.10:
                crossing_price = mid_price if side == Side.BID else mid_price
                batch.append((order_id_counter, side, OrderType.LIMIT, crossing_price, qty))
            else:
                batch.append((order_id_counter, side, OrderType.LIMIT, price, qty))

        client.submit_orders_batch(batch)
        total_orders += len(batch)
        time.sleep(0.01)  # small pacing between bursts

    elapsed = time.perf_counter() - start_time
    rate = total_orders / elapsed if elapsed > 0 else 0

    print(f"[+] Completed! Sent {total_orders} orders in {elapsed * 1000:.2f} ms ({rate:,.0f} orders/sec)")

    # Fetch snapshot from Web Gateway
    try:
        print("[*] Querying L2 depth snapshot via REST...")
        snapshot = client.get_order_book_snapshot()
        print(f"    Symbol: {snapshot.get('symbol')}")
        print(f"    Best Bid: ${snapshot.get('bestBid', 0):.2f}")
        print(f"    Best Ask: ${snapshot.get('bestAsk', 0):.2f}")
        print(f"    Spread: ${snapshot.get('spread', 0):.2f}")
        print(f"    Processed Orders: {snapshot.get('processedOrders', 0):,}")
        print(f"    Total Trades: {snapshot.get('totalTrades', 0):,}")
    except Exception as e:
        print(f"[-] Could not query HTTP snapshot: {e}")

    client.close()
    print("[*] Socket connection closed.")


if __name__ == "__main__":
    run_market_maker()
