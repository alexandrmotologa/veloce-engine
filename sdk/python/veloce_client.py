"""
VeloceEngine High-Speed Python Client SDK

Provides an ultra-low-latency TCP socket client streaming 32-byte native binary order frames
directly into the VeloceEngine Java NIO matching core, along with REST/SSE client utilities.
"""

import socket
import struct
import time
import json
import urllib.request
from enum import IntEnum
from typing import Optional, List, Tuple, Dict, Any


class Side(IntEnum):
    BID = 1
    ASK = 2


class OrderType(IntEnum):
    LIMIT = 1
    MARKET = 2
    IOC = 3
    FOK = 4
    POST_ONLY = 5


# Native binary frame format:
# timestampNs (int64), orderId (int64), price (int64 fixed-point x10,000),
# quantity (int32), side (uint8), orderType (uint8), padding (int16)
FRAME_FORMAT = '<qqqiBBh'
FRAME_SIZE = struct.calcsize(FRAME_FORMAT)  # 32 bytes


class VeloceClient:
    """
    High-performance TCP and HTTP client for VeloceEngine.
    """

    def __init__(self, host: str = "127.0.0.1", tcp_port: int = 9881, http_port: int = 8080):
        self.host = host
        self.tcp_port = tcp_port
        self.http_port = http_port
        self.sock: Optional[socket.socket] = None

    def connect(self) -> "VeloceClient":
        """Establishes TCP connection with TCP_NODELAY enabled."""
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.sock.connect((self.host, self.tcp_port))
        return self

    def close(self) -> None:
        """Closes the active TCP connection."""
        if self.sock:
            try:
                self.sock.close()
            except OSError:
                pass
            self.sock = None

    def __enter__(self) -> "VeloceClient":
        return self.connect()

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.close()

    def submit_order(
        self,
        order_id: int,
        side: Side,
        order_type: OrderType,
        price: float,
        quantity: int,
        timestamp_ns: Optional[int] = None,
    ) -> None:
        """
        Encodes and immediately dispatches a 32-byte binary order frame over TCP.
        
        :param order_id: Unique 64-bit integer identifier
        :param side: Side.BID or Side.ASK
        :param order_type: OrderType (LIMIT, MARKET, IOC, FOK, POST_ONLY)
        :param price: Price as floating-point dollars (automatically scaled by 10,000)
        :param quantity: Integer quantity of contracts / shares
        :param timestamp_ns: Nanosecond timestamp; auto-populated if None
        """
        if not self.sock:
            raise RuntimeError("Client is not connected. Call connect() first.")

        if timestamp_ns is None:
            timestamp_ns = time.time_ns()

        scaled_price = int(round(price * 10000))
        frame = struct.pack(
            FRAME_FORMAT,
            timestamp_ns,
            order_id,
            scaled_price,
            quantity,
            int(side),
            int(order_type),
            0,  # padding
        )
        self.sock.sendall(frame)

    def submit_orders_batch(self, orders: List[Tuple[int, Side, OrderType, float, int]]) -> None:
        """
        Packs and sends a batch of orders in a single socket sendall call to minimize syscall overhead.
        Each order tuple: (order_id, side, order_type, price, quantity)
        """
        if not self.sock:
            raise RuntimeError("Client is not connected. Call connect() first.")

        now = time.time_ns()
        buffer = bytearray()
        for order_id, side, order_type, price, quantity in orders:
            scaled_price = int(round(price * 10000))
            buffer.extend(
                struct.pack(
                    FRAME_FORMAT,
                    now,
                    order_id,
                    scaled_price,
                    quantity,
                    int(side),
                    int(order_type),
                    0,
                )
            )
        self.sock.sendall(buffer)

    # HTTP / REST API Helpers
    def get_order_book_snapshot(self) -> Dict[str, Any]:
        """Fetches the latest L2 order book snapshot from the embedded WebGateway."""
        url = f"http://{self.host}:{self.http_port}/api/snapshot"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=3) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def submit_http_order(self, side: str, order_type: str, price: float, qty: int) -> Dict[str, Any]:
        """Submits an order through the REST HTTP endpoint."""
        url = f"http://{self.host}:{self.http_port}/api/order"
        payload = json.dumps({
            "side": side,
            "type": order_type,
            "price": price,
            "qty": qty
        }).encode("utf-8")

        req = urllib.request.Request(
            url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=3) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def cancel_http_order(self, order_id: int) -> Dict[str, Any]:
        """Cancels an order through the REST HTTP endpoint."""
        url = f"http://{self.host}:{self.http_port}/api/cancel"
        payload = json.dumps({"orderId": order_id}).encode("utf-8")

        req = urllib.request.Request(
            url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=3) as resp:
            return json.loads(resp.read().decode("utf-8"))
