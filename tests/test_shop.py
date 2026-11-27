import tempfile
import unittest
from pathlib import Path
from unittest import mock

from peregrine import shop


class ShopTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        for name, val in (("DIR", self.tmp), ("WALLET", self.tmp / "wallet.json"), ("OWNED", self.tmp / "owned.json")):
            p = mock.patch.object(shop, name, val)
            p.start()
            self.addCleanup(p.stop)
        p = mock.patch.object(shop, "_wear_now", return_value="")
        p.start()
        self.addCleanup(p.stop)

    def test_starter_then_daily(self):
        self.assertEqual(shop.load("2026-01-01")["fragments"], shop.STARTER)
        self.assertEqual(shop.load("2026-01-01")["fragments"], shop.STARTER)
        self.assertEqual(shop.load("2026-01-02")["fragments"], shop.STARTER + shop.DAILY)

    def test_buy(self):
        shop.load()
        self.assertFalse(shop.owns("violet"))
        self.assertEqual(shop.buy("violet"), "")
        self.assertTrue(shop.owns("violet"))
        self.assertIn("already", shop.buy("violet"))
        self.assertIn("more Light Fragments", shop.buy("aurora"))
        self.assertIn("violet", shop.OWNED.read_text())

    def test_free(self):
        self.assertTrue(shop.owns("light"))
        self.assertTrue(shop.owns("custom"))
