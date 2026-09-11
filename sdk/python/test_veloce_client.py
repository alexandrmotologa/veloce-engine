import unittest
import struct
from veloce_client import VeloceClient, Side, OrderType, FRAME_FORMAT, FRAME_SIZE


class TestVeloceClient(unittest.TestCase):

    def test_frame_size(self):
        self.assertEqual(FRAME_SIZE, 32)

    def test_frame_encoding(self):
        timestamp = 1718000000123456
        order_id = 9876543210
        price_dollars = 100.50
        scaled_price = 1005000
        qty = 500
        side = Side.BID
        order_type = OrderType.IOC

        frame = struct.pack(
            FRAME_FORMAT,
            timestamp,
            order_id,
            scaled_price,
            qty,
            int(side),
            int(order_type),
            0
        )

        self.assertEqual(len(frame), 32)

        unpacked = struct.unpack(FRAME_FORMAT, frame)
        self.assertEqual(unpacked[0], timestamp)
        self.assertEqual(unpacked[1], order_id)
        self.assertEqual(unpacked[2], scaled_price)
        self.assertEqual(unpacked[3], qty)
        self.assertEqual(unpacked[4], 1)
        self.assertEqual(unpacked[5], 3)
        self.assertEqual(unpacked[6], 0)


if __name__ == '__main__':
    unittest.main()
