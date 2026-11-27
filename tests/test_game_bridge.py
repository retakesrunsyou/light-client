import json
import os
import tempfile
import unittest
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

try:
    from PySide6.QtCore import QCoreApplication, QEventLoop, QTimer
    HAVE_QT = True
except ImportError:   # pragma: no cover
    HAVE_QT = False


@unittest.skipUnless(HAVE_QT, "needs PySide6")
class GameBridgeTest(unittest.TestCase):
    def setUp(self):
        from peregrine import paths
        self.tmp = tempfile.TemporaryDirectory()
        self.old = paths.DATA
        paths.DATA = Path(self.tmp.name)
        self.app = QCoreApplication.instance() or QCoreApplication([])

    def tearDown(self):
        from peregrine import paths
        paths.DATA = self.old
        self.tmp.cleanup()

    def test_lines_keep_text_and_calls(self):
        from peregrine.ui.game_bridge import lines
        msgs = [
            {"id": 2, "mine": False, "at": 5, "payload": {"t": "text", "b": "hi"}},
            {"id": 1, "mine": True, "at": 4, "payload": {"t": "text", "b": "hello"}},
            {"id": 3, "mine": False, "at": 6, "payload": {"t": "ended", "s": 75}},
            {"id": 4, "mine": False, "at": 7, "payload": None},
        ]
        out = lines(msgs)
        self.assertEqual([m["text"] for m in out], ["hello", "hi", "Call · 1:15"])

    def test_requests_from_the_game_arrive(self):
        from peregrine.ui.game_bridge import GameBridge
        bridge = GameBridge()
        got = []
        bridge.request.connect(got.append)
        box = Path(self.tmp.name) / "client" / "outbox"
        box.mkdir(parents=True)
        (box / "1.json").write_text(json.dumps({"op": "text", "uuid": "abc", "text": "yo"}))
        (box / "2.tmp").write_text("{}")          # half written: left alone
        (box / "3.json").write_text("not json")  # dropped
        loop = QEventLoop()
        QTimer.singleShot(1200, loop.quit)
        loop.exec()
        self.assertEqual(got, [{"op": "text", "uuid": "abc", "text": "yo"}])
        self.assertFalse((box / "1.json").exists())
        self.assertTrue((box / "2.tmp").exists())

    def test_call_state_is_written_once(self):
        from peregrine.ui.game_bridge import GameBridge
        bridge = GameBridge()
        state = {"state": "live", "uuid": "u", "name": "n", "incoming": False, "seconds": 3, "muted": False}
        bridge.publish_call(state)
        f = Path(self.tmp.name) / "client" / "call.json"
        self.assertEqual(json.loads(f.read_text())["state"], "live")


if __name__ == "__main__":
    unittest.main()
