# Financial Protocols and Binary Framing

VeloceEngine supports three wire representations: NASDAQ ITCH 5.0, FIX 4.4, and a compact 32-byte native binary frame.

## 1. Native Binary Order Frame (32 Bytes)

For local inter-process communication and synthetic benchmark harnesses, VeloceEngine defines a fixed-size 32-byte binary frame. The layout aligns on 8-byte boundaries to facilitate single-instruction vectorized loads and CPU cache line alignment.

### Memory Layout

| Offset | Length | Type | Field Name | Description |
| :--- | :--- | :--- | :--- | :--- |
| 0 | 8 bytes | `int64` | `timestampNs` | Inbound hardware timestamp in epoch nanoseconds |
| 8 | 8 bytes | `int64` | `orderId` | Unique 64-bit unsigned/signed order identifier |
| 16 | 8 bytes | `int64` | `price` | Fixed-point price (scaled by 10,000; e.g. 1005000 = $100.5000) |
| 24 | 4 bytes | `int32` | `quantity` | Number of shares / contracts (up to 2,147,483,647) |
| 28 | 1 byte | `uint8` | `side` | Side: `1` for BID (Buy), `2` for ASK (Sell) |
| 29 | 1 byte | `uint8` | `orderType` | Type: `1` = LIMIT, `2` = MARKET, `3` = IOC, `4` = FOK, `5` = POST_ONLY |
| 30 | 2 bytes | `uint16` | `padding` | Reserved 16-bit alignment padding for 8-byte boundary |

Total Frame Size: Exactly 32 bytes.

## 2. NASDAQ ITCH 5.0 Binary Protocol

NASDAQ ITCH 5.0 is a direct data feed protocol used by exchanges to publish order book events. VeloceEngine parses binary ITCH messages directly off byte buffers without intermediate heap allocations.

Supported message types:

### Add Order Message (Type 'A')
- Bytes 0: Message Type (`A`)
- Bytes 1-2: Stock Locate
- Bytes 3-4: Tracking Number
- Bytes 5-10: Timestamp (nanoseconds since midnight)
- Bytes 11-18: Order Reference Number (`long`)
- Bytes 19: Buy/Sell Indicator (`B` or `S`)
- Bytes 20-23: Shares (`int`)
- Bytes 24-31: Stock Symbol (8 ASCII characters, right-padded with spaces)
- Bytes 32-35: Price (4-byte fixed-point integer, scaled by 10,000)

### Order Executed Message (Type 'E')
- Bytes 0: Message Type (`E`)
- Bytes 11-18: Order Reference Number (`long`)
- Bytes 19-22: Executed Shares (`int`)
- Bytes 23-30: Match Number (`long`)

### Order Cancel Message (Type 'X')
- Bytes 0: Message Type (`X`)
- Bytes 11-18: Order Reference Number (`long`)
- Bytes 19-22: Canceled Shares (`int`)

### Order Delete Message (Type 'D')
- Bytes 0: Message Type (`D`)
- Bytes 11-18: Order Reference Number (`long`)

## 3. FIX 4.4 Protocol

The Financial Information eXchange (FIX) protocol uses tag-value ASCII streams separated by delimiter `0x01` (`SOH`).

`FixCodec` scans byte buffers linearly and extracts field values directly as integers and longs without instantiating `java.lang.String` or split arrays.

Parsed tags:
- `35`: MsgType (`D` = New Order Single, `F` = Order Cancel Request)
- `11`: ClOrdID (Client Order ID, parsed as 64-bit integer or mapped hash)
- `54`: Side (`1` = Buy, `2` = Sell)
- `38`: OrderQty (Volume, parsed directly as integer)
- `44`: Price (Fixed-point price scaled to integer representation)
- `40`: OrdType (`1` = Market, `2` = Limit)
- `59`: TimeInForce (`0` = Day, `3` = IOC, `4` = FOK)

## 4. Web Gateway HTTP & Server-Sent Events (SSE)

VeloceEngine hosts an embedded HTTP gateway running on Java Virtual Threads for browser monitoring and order routing.

### Endpoints

| Method | Path | Content-Type | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | `text/html` | Serves the interactive trading dashboard |
| `GET` | `/style.css` | `text/css` | Dashboard stylesheet |
| `GET` | `/app.js` | `application/javascript` | Client-side reactive ladder & SSE subscriber |
| `GET` | `/api/snapshot` | `application/json` | Current L2 market depth snapshot (bids, asks, spread) |
| `POST` | `/api/order` | `application/json` | REST order submission (`{"side":"BUY","type":"LIMIT","price":150.0,"qty":50}`) |
| `POST` | `/api/cancel` | `application/json` | REST order cancellation (`{"orderId":1001}`) |
| `GET` | `/api/events` | `text/event-stream` | Real-time SSE stream for `snapshot` (10 FPS) and `trade` events |

## 5. Memory-Mapped Write-Ahead Log (WAL) Format

The journal sequentially logs incoming order frames directly into an OS page-cache-backed memory-mapped file (`MappedByteBuffer`).

Each entry consists of a 16-byte envelope followed by the 32-byte `BinaryOrderFrame`:

| Offset | Length | Type | Field Name | Description |
| :--- | :--- | :--- | :--- | :--- |
| 0 | 4 bytes | `int32` | `magic` | Identifier `0x564C4345` ("VLCE") |
| 4 | 4 bytes | `int32` | `payloadLength` | Payload size in bytes (`32`) |
| 8 | 8 bytes | `int64` | `sequence` | Monotonically increasing sequence number |
| 16 | 32 bytes | `BinaryOrderFrame` | `payload` | The complete binary order frame |

Total entry size: Exactly 48 bytes per logged order. Replaying uses zero heap allocations.
