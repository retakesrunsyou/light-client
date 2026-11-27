"""The in-game Friends page talks to the launcher through files in <data>/client/.

The launcher already holds the friends, the locked chats and the calls, so the game doesn't
repeat any of it. It writes what the game shows and reads what the game asks for:

  friends.json        who your friends are, who's online, unread counts (friends.save_snapshot)
  chat/<uuid>.json    the latest messages with a friend: [{"mine", "kind", "text", "at"}]
  call.json           the call right now: {"state", "uuid", "name", "incoming", "seconds", "muted"}
  outbox/*.json       what the game asks for: {"op": "text" | "open" | "call" | "answer" | "decline"
                      | "hangup" | "mute", "uuid": ..., "text": ...}
"""

import json
import time

from PySide6.QtCore import QObject, QTimer, Signal

from .. import paths

KEEP = 60                # messages kept per chat
MAX_TEXT = 1000


def folder():
    f = paths.DATA / "client"
    try:
        f.mkdir(parents=True, exist_ok=True)
    except OSError:
        pass
    return f


def _write(path, data) -> None:
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(data))
        tmp.replace(path)
    except OSError:
        pass


def lines(msgs: list) -> list:
    """What the game shows of a chat: text and call lines, oldest first."""
    out = []
    for m in sorted(msgs, key=lambda m: m.get("id", 0))[-KEEP * 2:]:
        p = m.get("payload")
        if not p:
            continue
        t = p.get("t")
        mine = bool(m.get("mine"))
        if t == "text":
            out.append({"mine": mine, "kind": "text", "text": str(p.get("b", ""))[:MAX_TEXT], "at": m.get("at", 0)})
        elif t == "call" and not mine:
            out.append({"mine": mine, "kind": "call", "text": "Incoming call", "at": m.get("at", 0)})
        elif t == "ended":
            if p.get("missed"):
                text = "Missed call" if not mine else "No answer"
            elif p.get("s"):
                text = f"Call · {int(p['s']) // 60}:{int(p['s']) % 60:02d}"
            else:
                text = "Call declined" if not mine else "Call ended"
            out.append({"mine": mine, "kind": "call", "text": text, "at": m.get("at", 0)})
    return out[-KEEP:]


class GameBridge(QObject):
    request = Signal(dict)     # something the game asked for

    def __init__(self, parent=None):
        super().__init__(parent)
        self.timer = QTimer(self, interval=400, timeout=self._poll)
        self.timer.start()
        self._last_call = None

    def publish_chat(self, uuid: str, msgs: list) -> None:
        _write(folder() / "chat" / f"{uuid}.json", {"updated": int(time.time()), "messages": lines(msgs)})

    def publish_call(self, call: dict) -> None:
        if call != self._last_call:
            self._last_call = dict(call)
            _write(folder() / "call.json", call)

    def _poll(self) -> None:
        box = paths.DATA / "client" / "outbox"
        try:
            files = sorted(f for f in box.iterdir() if f.suffix == ".json")
        except OSError:
            return
        for f in files[:20]:
            try:
                req = json.loads(f.read_text())
            except (OSError, ValueError):
                req = None
            try:
                f.unlink()
            except OSError:
                pass
            if isinstance(req, dict) and isinstance(req.get("op"), str):
                self.request.emit(req)
