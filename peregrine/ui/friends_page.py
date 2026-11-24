"""Friends: Light Client players you've added. Requests, chat and calls (voice, and your
screen if you share it), all locked end to end. See peregrine/friends.py for how."""

import threading
import time
from datetime import datetime

from PySide6.QtCore import QObject, QPoint, QSize, Qt, QTimer, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QApplication, QFrame, QHBoxLayout, QLabel, QLineEdit, QMenu, QPushButton, QScrollArea,
    QSizePolicy, QVBoxLayout, QWidget,
)

from .. import auth, avatars, friends, wsclient
from . import workers
from .modern import ONLINE, PillButton, big_title, section_label
from .pages import clear, empty_state, scroll
from .theme import Theme, avatar_pixmap, icon

RING_FOR = 60          # a call message older than this (seconds) doesn't ring any more


def ago(ms: int) -> str:
    if not ms:
        return "Offline"
    s = max(0, time.time() - ms / 1000)
    if s < 120:
        return "Seen just now"
    if s < 3600:
        return f"Seen {int(s // 60)} min ago"
    if s < 86400:
        return f"Seen {int(s // 3600)} h ago"
    days = int(s // 86400)
    return "Seen yesterday" if days == 1 else f"Seen {days} days ago"


def clock(ms: int) -> str:
    d = datetime.fromtimestamp(ms / 1000)
    today = datetime.now().date()
    if d.date() == today:
        return d.strftime("%H:%M")
    return d.strftime("%d %b, %H:%M")


def minutes(s: int) -> str:
    return f"{s // 60}:{s % 60:02d}"


class _Faces:
    """Players' faces, fetched once in the background."""

    def __init__(self):
        self.paths, self.waiting = {}, {}

    def get(self, uuid: str, name: str, size: int, label: QLabel):
        label.setPixmap(avatar_pixmap(self.paths.get(uuid), name, size))
        if uuid in self.paths:
            return
        self.waiting.setdefault(uuid, []).append((label, name, size))
        if len(self.waiting[uuid]) == 1:
            workers.run(avatars.skin_path, uuid, done=lambda p, u=uuid: self._got(u, p),
                        failed=lambda _m, u=uuid: self.waiting.pop(u, None))

    def _got(self, uuid, path):
        self.paths[uuid] = path
        for label, name, size in self.waiting.pop(uuid, []):
            try:
                label.setPixmap(avatar_pixmap(path, name, size))
            except RuntimeError:
                pass


FACES = _Faces()


def _icon_button(name: str, tip: str, kind: str = "plain") -> QPushButton:
    b = QPushButton(objectName="callBtn")
    b.setProperty("kind", kind)
    color = "#ffffff" if kind in ("go", "stop") else Theme.p["text"]
    b.setIcon(icon(name, color, 18))
    b.setIconSize(QSize(18, 18))
    b.setFixedSize(36, 36)
    b.setToolTip(tip)
    b.setCursor(Qt.PointingHandCursor)
    return b


STYLE = """
QFrame#friendsList, QFrame#chatPane {{ background: {panel}; border: 1px solid {border}; border-radius: 14px; }}
QPushButton#friendRow {{ background: transparent; border: none; border-radius: 10px; text-align: left; padding: 0; }}
QPushButton#friendRow:hover {{ background: {hover}; }}
QPushButton#friendRow[selected="true"] {{ background: {selected}; }}
QLabel#rowName {{ font-weight: 600; }}
QLabel#unread {{ background: {accent}; color: white; border-radius: 9px; padding: 0 6px; font-size: 11px; font-weight: 700; }}
QLabel#bubbleMine {{ background: {accent}; color: white; border-radius: 12px; padding: 7px 11px; }}
QLabel#bubbleTheirs {{ background: {bubble}; color: {text}; border-radius: 12px; padding: 7px 11px; }}
QLabel#callLine {{ color: {muted}; font-size: 12px; }}
QPushButton#callBtn {{ background: {bubble}; border: none; border-radius: 18px; }}
QPushButton#callBtn:hover {{ background: {hover}; }}
QPushButton#callBtn:disabled {{ background: transparent; }}
QPushButton#callBtn[kind="go"] {{ background: #2fa866; }}
QPushButton#callBtn[kind="stop"] {{ background: #d9534f; }}
QPushButton#callBtn[kind="on"] {{ background: {accent}; }}
QFrame#callBar, QFrame#ringBar {{ background: {bubble}; border-radius: 12px; }}
QPushButton#smallLink {{ background: transparent; border: none; color: {muted}; padding: 2px 6px; }}
QPushButton#smallLink:hover {{ color: {text}; }}
"""


def _style() -> str:
    p = Theme.p
    return STYLE.format(panel=_rgba(p["surface"] if "surface" in p else p["bg"], 0.72), border=p["border"],
                        hover=_rgba(p["text"], 0.07), selected=_rgba(Theme.accent, 0.18), accent=Theme.accent,
                        bubble=_rgba(p["text"], 0.09), text=p["text"], muted=p["muted"])


def _rgba(color: str, a: float) -> str:
    c = color.lstrip("#")
    if len(c) != 6:
        return color
    return f"rgba({int(c[0:2], 16)}, {int(c[2:4], 16)}, {int(c[4:6], 16)}, {a})"


# ---------------------------------------------------------------------- live updates

class Live(QObject):
    """Keeps a connection to the friends service open and passes on what it says."""
    event = Signal(dict)
    state = Signal(bool)

    def __init__(self, client):
        super().__init__()
        self.client = client
        self.stopped = False
        self.ws = None
        threading.Thread(target=self._run, daemon=True).start()

    def _run(self):
        import json
        wait = 2
        while not self.stopped:
            try:
                self.ws = self.client.live()
                self.state.emit(True)
                wait = 2
                pinger = threading.Thread(target=self._ping, args=(self.ws,), daemon=True)
                pinger.start()
                while not self.stopped:
                    kind, data = self.ws.recv()
                    if kind == wsclient.TEXT and data != "pong":
                        try:
                            self.event.emit(json.loads(data))
                        except ValueError:
                            pass
            except Exception:
                pass
            self.state.emit(False)
            if self.stopped:
                return
            time.sleep(wait)
            wait = min(60, wait * 2)

    def _ping(self, ws):
        while not self.stopped and not ws.closed:
            time.sleep(30)
            try:
                ws.send_text("ping")
            except Exception:
                return

    def stop(self):
        self.stopped = True
        if self.ws:
            threading.Thread(target=self.ws.close, daemon=True).start()


# ---------------------------------------------------------------------- pieces

class FriendRow(QPushButton):
    def __init__(self, person: dict, selected: bool):
        super().__init__(objectName="friendRow")
        self.setProperty("selected", "true" if selected else "false")
        self.setCursor(Qt.PointingHandCursor)
        self.setFixedHeight(54)
        h = QHBoxLayout(self)
        h.setContentsMargins(10, 6, 10, 6)
        h.setSpacing(10)
        face = QLabel()
        face.setFixedSize(34, 34)
        FACES.get(person["uuid"], person["name"], 34, face)
        h.addWidget(face)
        col = QVBoxLayout()
        col.setSpacing(1)
        name = QLabel(person["name"], objectName="rowName")
        col.addWidget(name)
        line = QHBoxLayout()
        line.setSpacing(6)
        if person.get("online"):
            dot = QLabel()
            dot.setFixedSize(7, 7)
            dot.setStyleSheet(f"background: {ONLINE}; border-radius: 3px;")
            line.addWidget(dot)
        status = QLabel("Online" if person.get("online") else ago(person.get("seen", 0)), objectName="faint")
        line.addWidget(status)
        line.addStretch()
        col.addLayout(line)
        h.addLayout(col, 1)
        if person.get("unread"):
            n = QLabel(str(min(99, person["unread"])), objectName="unread")
            n.setFixedHeight(18)
            n.setAlignment(Qt.AlignCenter)
            h.addWidget(n)
        for w in (face, name, status):
            w.setAttribute(Qt.WA_TransparentForMouseEvents)


class RequestRow(QFrame):
    def __init__(self, person: dict, incoming: bool, page):
        super().__init__()
        h = QHBoxLayout(self)
        h.setContentsMargins(10, 4, 6, 4)
        h.setSpacing(10)
        face = QLabel()
        face.setFixedSize(28, 28)
        FACES.get(person["uuid"], person["name"], 28, face)
        h.addWidget(face)
        col = QVBoxLayout()
        col.setSpacing(0)
        col.addWidget(QLabel(person["name"], objectName="rowName"))
        col.addWidget(QLabel("Wants to be friends" if incoming else "Request sent", objectName="faint"))
        h.addLayout(col, 1)
        if incoming:
            yes = _icon_button("check", "Accept", "go")
            yes.setFixedSize(30, 30)
            yes.setStyleSheet("border-radius: 15px;")
            yes.clicked.connect(lambda: page.act("accept", person))
            no = _icon_button("x", "Decline")
            no.setFixedSize(30, 30)
            no.setStyleSheet("border-radius: 15px;")
            no.clicked.connect(lambda: page.act("decline", person))
            h.addWidget(yes)
            h.addWidget(no)
        else:
            undo = QPushButton("Cancel", objectName="smallLink")
            undo.setCursor(Qt.PointingHandCursor)
            undo.clicked.connect(lambda: page.act("cancel", person))
            h.addWidget(undo)


class ScreenViewer(QWidget):
    """A friend's shared screen, in its own window."""

    def __init__(self):
        super().__init__(None, Qt.Window)
        self.setWindowTitle("Shared screen")
        self.resize(960, 560)
        v = QVBoxLayout(self)
        v.setContentsMargins(0, 0, 0, 0)
        self.picture = QLabel(alignment=Qt.AlignCenter)
        self.picture.setStyleSheet("background: #000;")
        self.picture.setSizePolicy(QSizePolicy.Ignored, QSizePolicy.Ignored)
        v.addWidget(self.picture)
        self.last = None

    def show_frame(self, img, who):
        self.setWindowTitle(f"{who}'s screen")
        self.last = QPixmap.fromImage(img)
        self._fit()
        if not self.isVisible():
            self.show()

    def _fit(self):
        if self.last:
            self.picture.setPixmap(self.last.scaled(self.picture.size(), Qt.KeepAspectRatio, Qt.SmoothTransformation))

    def resizeEvent(self, e):
        self._fit()
        super().resizeEvent(e)


# ---------------------------------------------------------------------- the page

class FriendsPage(QWidget):
    counts = Signal(int, int)       # online, total (for the top bar)
    sign_in = Signal()

    def __init__(self):
        super().__init__(objectName="page")
        self.client = None
        self.live = None
        self.data = {"friends": [], "incoming": [], "outgoing": []}
        self.error = ""
        self.loading = False
        self.selected = None          # uuid of the open chat
        self.chats = {}               # uuid -> list of messages (unlocked)
        self.call = None
        self.ringing = None           # {"friend", "room", "key", "screen"}
        self.viewer = None
        self.handled_calls = set()    # rooms already rung or ended
        self.setStyleSheet(_style())

        v = QVBoxLayout(self)
        v.setContentsMargins(56, 30, 56, 20)
        v.setSpacing(14)
        head = QHBoxLayout()
        col = QVBoxLayout()
        col.setSpacing(8)
        col.addWidget(big_title("Friends"))
        self.sub = QLabel("", objectName="muted")
        col.addWidget(self.sub)
        head.addLayout(col)
        head.addStretch()
        v.addLayout(head)

        self.body = QHBoxLayout()
        self.body.setSpacing(14)
        v.addLayout(self.body, 1)

        # left: add, requests, friends
        self.left = QFrame(objectName="friendsList")
        self.left.setFixedWidth(300)
        lv = QVBoxLayout(self.left)
        lv.setContentsMargins(12, 12, 12, 12)
        lv.setSpacing(10)
        add = QHBoxLayout()
        add.setSpacing(8)
        self.add_box = QLineEdit(placeholderText="Add by Minecraft name")
        self.add_box.returnPressed.connect(self.add_friend)
        add.addWidget(self.add_box, 1)
        plus = _icon_button("user_plus", "Send a friend request", "on")
        plus.clicked.connect(self.add_friend)
        add.addWidget(plus)
        lv.addLayout(add)
        self.add_note = QLabel("", objectName="faint")
        self.add_note.setWordWrap(True)
        self.add_note.hide()
        lv.addWidget(self.add_note)
        holder = QWidget()
        self.list = QVBoxLayout(holder)
        self.list.setContentsMargins(0, 0, 0, 0)
        self.list.setSpacing(2)
        lv.addWidget(scroll(holder), 1)
        self.body.addWidget(self.left)

        # right: the chat
        self.chat = QFrame(objectName="chatPane")
        self.cv = QVBoxLayout(self.chat)
        self.cv.setContentsMargins(16, 12, 16, 12)
        self.cv.setSpacing(10)
        self.body.addWidget(self.chat, 1)

        self.ticker = QTimer(self, interval=1000, timeout=self._tick)
        self.refresher = QTimer(self, interval=90_000, timeout=self.load)
        self.refresher.start()
        QTimer.singleShot(1200, self.load)

    # ------------------------------------------------------------ account and data

    def account(self):
        a = auth.active_account()
        return a if a and a.get("type") == "msa" else None

    def _ensure_client(self):
        a = self.account()
        if a is None:
            self._drop_client()
            return None
        if self.client is None or self.client.uuid != a["uuid"].replace("-", "").lower():
            self._drop_client()
            try:
                self.client = friends.Client(a)
            except friends.FriendsError as e:
                self.error = str(e)
                return None
        return self.client

    def _drop_client(self):
        if self.live:
            self.live.stop()
            self.live = None
        if self.call:
            self.call.hang_up("signed out")
        self.client = None
        self.chats.clear()
        self.selected = None

    def load(self):
        c = self._ensure_client()
        if c is None:
            self.data = {"friends": [], "incoming": [], "outgoing": []}
            self.render()
            self.counts.emit(-1, 0)
            return
        if self.loading:
            return
        self.loading = True

        def work():
            data = c.me()
            return data

        workers.run(work, done=self._loaded, failed=self._failed)

    def _loaded(self, data):
        self.loading = False
        self.error = ""
        self.data = data
        friends.save_snapshot(data.get("friends", []))
        self.render()
        people = data.get("friends", [])
        self.counts.emit(sum(1 for p in people if p.get("online")), len(people))
        if self.live is None and self.client:
            self.live = Live(self.client)
            self.live.event.connect(self._event)
        # then the real names from Mojang, quietly
        c = self.client
        workers.run(lambda: c.refresh_names(data), done=lambda d: self._names_in(d), failed=lambda _m: None)

    def _names_in(self, data):
        if data is self.data:
            self.render()

    def _failed(self, msg):
        self.loading = False
        self.error = msg
        self.render()
        self.counts.emit(-1, 0)

    def friend(self, uuid):
        for p in self.data.get("friends", []):
            if p["uuid"] == uuid:
                return p
        return None

    # ------------------------------------------------------------ live events

    def _event(self, e):
        t = e.get("type")
        if t in ("friends", "request"):
            if t == "request":
                self._notify(f"{friends.cached_name(e.get('uuid', ''), e.get('name', ''))} sent you a friend request")
            self.load()
        elif t == "presence":
            p = self.friend(e.get("uuid"))
            if p:
                p["online"] = 1 if e.get("online") else 0
                p["seen"] = int(time.time() * 1000)
                self.render_list()
                people = self.data.get("friends", [])
                self.counts.emit(sum(1 for q in people if q.get("online")), len(people))
        elif t == "keys" and self.client:
            self.client.forget_keys(e.get("uuid"))
        elif t == "message":
            self._fetch_new(e.get("peer"))
            if not e.get("mine") and e.get("peer") != self.selected:
                p = self.friend(e.get("peer"))
                if p:
                    p["unread"] = p.get("unread", 0) + 1
                    self.render_list()

    def _fetch_new(self, uuid):
        c = self.client
        if not c or not uuid:
            return
        after = max([m["id"] for m in self.chats.get(uuid, [])] or [0])
        workers.run(c.messages, uuid, after, done=lambda msgs, u=uuid: self._new_messages(u, msgs, True),
                    failed=lambda _m: None)

    def _new_messages(self, uuid, msgs, live):
        have = {m["id"] for m in self.chats.get(uuid, [])}
        fresh = [m for m in msgs if m["id"] not in have]
        self.chats.setdefault(uuid, []).extend(fresh)
        self.chats[uuid].sort(key=lambda m: m["id"])
        for m in fresh:
            p = m.get("payload") or {}
            if p.get("t") == "call" and not m["mine"] and live:
                if time.time() - m["at"] / 1000 < RING_FOR and p.get("room") not in self.handled_calls:
                    self._ring(uuid, p)
                self.handled_calls.add(p.get("room"))
            elif p.get("t") == "ended":
                self.handled_calls.add(p.get("room"))
                if self.ringing and self.ringing["room"] == p.get("room"):
                    self.ringing = None
                    self.render_chat()
                if self.call and self.call.room == p.get("room") and self.call.state == "calling":
                    self.call.hang_up("declined")
            elif p.get("t") == "text" and not m["mine"] and live:
                if uuid != self.selected or not self.isVisible():
                    self._notify(f"{self._name(uuid)}: {p.get('b', '')[:80]}")
        if uuid == self.selected:
            self.render_messages()
            if fresh and self.client:
                last = max(m["id"] for m in self.chats[uuid])
                workers.run(self.client.read, uuid, last, failed=lambda _m: None)

    def _name(self, uuid):
        p = self.friend(uuid)
        return p["name"] if p else friends.cached_name(uuid)

    def _notify(self, text):
        w = self.window()
        tray = getattr(w, "tray", None)
        if tray is not None and not w.isActiveWindow():
            try:
                tray.showMessage("Light Client", text)
            except Exception:
                pass
        QApplication.alert(w, 4000)

    # ------------------------------------------------------------ actions

    def add_friend(self):
        name = self.add_box.text().strip()
        c = self.client
        if not name or not c:
            return
        self.add_note.setText(f"Sending a request to {name}…")
        self.add_note.show()

        def ok(r):
            self.add_box.clear()
            if r.get("friends"):
                self.add_note.setText(f"You and {r.get('name', name)} are friends now.")
            else:
                self.add_note.setText(f"Request sent to {r.get('name', name)}. They'll see it in Friends once "
                                      "they're on Light Client 2.2.1 or newer.")
            self.load()

        workers.run(c.add, name, done=ok, failed=lambda m: self.add_note.setText(m))

    def act(self, what, person):
        c = self.client
        if not c:
            return
        fn = {"accept": c.accept, "decline": c.decline, "cancel": c.cancel}[what]
        workers.run(fn, person["uuid"], done=lambda _r: self.load(), failed=lambda m: self.sub.setText(m))

    def open_chat(self, uuid):
        self.selected = uuid
        p = self.friend(uuid)
        if p:
            p["unread"] = 0
        self.render_list()
        self.render_chat()
        c = self.client
        if c and uuid not in self.chats:
            workers.run(c.messages, uuid, 0, 80, done=lambda msgs, u=uuid: self._new_messages(u, msgs, False),
                        failed=lambda m: self._chat_error(m))
        elif c and self.chats.get(uuid):
            workers.run(c.read, uuid, self.chats[uuid][-1]["id"], failed=lambda _m: None)

    def _chat_error(self, msg):
        self.sub.setText(msg)

    def send_text(self):
        text = self.input.text().strip()
        uuid, c = self.selected, self.client
        if not text or not uuid or not c:
            return
        self.input.clear()
        pending = {"id": 10 ** 12 + len(self.chats.get(uuid, [])), "mine": True, "at": int(time.time() * 1000),
                   "payload": {"t": "text", "b": text}, "pending": True}
        self.chats.setdefault(uuid, []).append(pending)
        self.render_messages()

        def sent(r):
            try:
                self.chats[uuid].remove(pending)
            except ValueError:
                pass
            self._fetch_new(uuid)

        def failed(m):
            pending["failed"] = m
            self.render_messages()

        workers.run(c.text, uuid, text, done=sent, failed=failed)

    def friend_menu(self, uuid, button):
        menu = QMenu(self)
        menu.addAction("Safety code…", lambda: self.show_code(uuid))
        menu.addSeparator()
        menu.addAction("Remove friend", lambda: self.remove(uuid))
        menu.exec(button.mapToGlobal(QPoint(0, button.height())))

    def show_code(self, uuid):
        from .sheet import tell
        c = self.client

        def done(code):
            tell(self, f"Safety code with {self._name(uuid)}", f"{code}\n\nIf {self._name(uuid)} sees the same "
                 "numbers on their screen, your messages and calls can only be read by the two of you. It "
                 "changes when either of you signs in on another PC.", "info")
        workers.run(c.code, uuid, done=done, failed=lambda m: tell(self, "Couldn't get the code", m, "error"))

    def remove(self, uuid):
        from .sheet import ask
        name = self._name(uuid)
        if not ask(self, f"Remove {name}?", "You'll stop being friends and your chat with them is deleted for both "
                   "of you.", "Remove", kind="danger"):
            return
        c = self.client
        self.chats.pop(uuid, None)
        if self.selected == uuid:
            self.selected = None
        workers.run(c.remove, uuid, done=lambda _r: self.load(), failed=lambda m: self.sub.setText(m))

    # ------------------------------------------------------------ calls

    def start_call(self, uuid, screen=False):
        if self.call or not self.client:
            return
        p = self.friend(uuid)
        if not p:
            return
        c = self.client

        def rang(r):
            room, key = r
            self.handled_calls.add(room)
            self._begin_call(p, room, key, True, screen)

        workers.run(c.ring, uuid, screen, done=rang, failed=lambda m: self.sub.setText(m))

    def _begin_call(self, person, room, key, outgoing, screen=False):
        from .call import Call
        self.call = Call(self.client, person, room, key, outgoing, self)
        self.call.state_changed.connect(self._call_state)
        self.call.problem.connect(lambda m: self.sub.setText(m))
        self.call.screen_frame.connect(self._screen_frame)
        self.call.sharing_changed.connect(self._sharing_changed)
        self.call.share_when_live = screen
        self.call.start()
        self.ticker.start()
        self.render_chat()

    def _ring(self, uuid, payload):
        if self.call:   # already on a call: tell them it didn't go through
            workers.run(self.client.hang_up, uuid, payload.get("room", ""), 0, True, failed=lambda _m: None)
            return
        p = self.friend(uuid)
        if not p:
            return
        import base64
        self.ringing = {"friend": p, "room": payload["room"], "key": base64.b64decode(payload["key"]),
                        "screen": payload.get("screen")}
        self._notify(f"{p['name']} is calling you")
        if self.selected != uuid:
            self.open_chat(uuid)
        else:
            self.render_chat()
        QTimer.singleShot(RING_FOR * 1000, lambda r=payload["room"]: self._stop_ringing(r))

    def _stop_ringing(self, room):
        if self.ringing and self.ringing["room"] == room:
            self.ringing = None
            self.render_chat()

    def answer(self):
        r, self.ringing = self.ringing, None
        if r:
            self._begin_call(r["friend"], r["room"], r["key"], False)

    def decline(self):
        r, self.ringing = self.ringing, None
        if r and self.client:
            workers.run(self.client.hang_up, r["friend"]["uuid"], r["room"], 0, False, failed=lambda _m: None)
        self.render_chat()

    def _call_state(self, state):
        call = self.call
        if state == "live" and getattr(call, "share_when_live", False):
            call.start_screen()
        if state == "ended" and call:
            seconds = getattr(call, "ended_seconds", 0)
            reason = getattr(call, "ended_reason", "")
            missed = reason == "no answer"
            if reason in ("you", "no answer"):   # whoever ended it writes it in the chat
                workers.run(self.client.hang_up, call.friend["uuid"], call.room, seconds, missed, failed=lambda _m: None)
            self.call = None
            self.ticker.stop()
            if self.viewer:
                self.viewer.close()
                self.viewer = None
        self.render_chat()

    def _screen_frame(self, img, who):
        if self.viewer is None:
            self.viewer = ScreenViewer()
        self.viewer.show_frame(img, who)

    def _sharing_changed(self, who, sharing):
        if not sharing and self.viewer:
            self.viewer.close()
            self.viewer = None

    def _tick(self):
        if self.call and hasattr(self, "call_time"):
            try:
                if self.call.state == "live":
                    self.call_time.setText(f"On a call with {self.call.friend['name']} · {minutes(self.call.duration())}")
            except RuntimeError:
                pass

    def toggle_mute(self):
        if self.call:
            self.call.set_muted(not self.call.muted)
            self.render_chat()

    def toggle_screen(self):
        if not self.call:
            return
        if self.call.sharing:
            self.call.stop_screen()
        else:
            self.call.start_screen()
        self.render_chat()

    def hang_up(self):
        if self.call:
            self.call.hang_up("you")

    # ------------------------------------------------------------ drawing

    def render(self):
        self.setStyleSheet(_style())
        signed_in = self.account() is not None
        people = self.data.get("friends", [])
        online = sum(1 for p in people if p.get("online"))
        if not signed_in:
            self.sub.setText("Sign in with your Microsoft account to add friends.")
        elif self.error:
            self.sub.setText(self.error)
        elif people:
            self.sub.setText(f"{online} online · {len(people)} friend{'' if len(people) == 1 else 's'}")
        else:
            self.sub.setText("Add Light Client players by their Minecraft name.")
        self.left.setEnabled(signed_in)
        self.render_list()
        self.render_chat()

    def render_list(self):
        clear(self.list)
        if self.account() is None:
            return
        incoming, outgoing = self.data.get("incoming", []), self.data.get("outgoing", [])
        if incoming or outgoing:
            self.list.addWidget(section_label(f"Requests · {len(incoming) + len(outgoing)}"))
            for p in incoming:
                self.list.addWidget(RequestRow(p, True, self))
            for p in outgoing:
                self.list.addWidget(RequestRow(p, False, self))
            self.list.addSpacing(8)
        people = self.data.get("friends", [])
        on = [p for p in people if p.get("online")]
        off = [p for p in people if not p.get("online")]
        for label, group in ((f"Online · {len(on)}", on), (f"Offline · {len(off)}", off)):
            if not group:
                continue
            self.list.addWidget(section_label(label))
            for p in group:
                row = FriendRow(p, p["uuid"] == self.selected)
                row.clicked.connect(lambda _=False, u=p["uuid"]: self.open_chat(u))
                self.list.addWidget(row)
            self.list.addSpacing(6)
        if not people and not incoming and not outgoing:
            note = QLabel("No friends yet. Type someone's Minecraft name above to send them a request. "
                          "They need Light Client too.", objectName="faint")
            note.setWordWrap(True)
            self.list.addWidget(note)
        self.list.addStretch()

    def render_chat(self):
        clear(self.cv)
        self.msg_list = None
        if self.account() is None:
            self.cv.addWidget(empty_state("users", "Sign in to use friends",
                                          "Friends are other Light Client players. Chat and call them right "
                                          "from the launcher."))
            b = PillButton("Sign in", "s_user", "primary")
            b.clicked.connect(self.sign_in.emit)
            self.cv.addWidget(b, 0, Qt.AlignHCenter)
            self.cv.addStretch()
            return
        p = self.friend(self.selected) if self.selected else None
        if not p:
            self.cv.addWidget(empty_state("users", "Pick a friend", "Your chats show up here. Messages and calls "
                                          "are encrypted so only you and your friend can read or hear them."))
            self.cv.addStretch()
            return

        # header
        top = QHBoxLayout()
        top.setSpacing(10)
        face = QLabel()
        face.setFixedSize(40, 40)
        FACES.get(p["uuid"], p["name"], 40, face)
        top.addWidget(face)
        col = QVBoxLayout()
        col.setSpacing(0)
        col.addWidget(QLabel(p["name"], objectName="h2"))
        col.addWidget(QLabel("Online" if p.get("online") else ago(p.get("seen", 0)), objectName="faint"))
        top.addLayout(col, 1)
        busy = self.call is not None
        call_btn = _icon_button("phone", "Voice call")
        call_btn.setEnabled(not busy)
        call_btn.clicked.connect(lambda: self.start_call(p["uuid"]))
        share_btn = _icon_button("screen", "Call and share your screen")
        share_btn.setEnabled(not busy)
        share_btn.clicked.connect(lambda: self.start_call(p["uuid"], True))
        more = _icon_button("dots", "More")
        more.clicked.connect(lambda: self.friend_menu(p["uuid"], more))
        for b in (call_btn, share_btn, more):
            top.addWidget(b)
        self.cv.addLayout(top)

        # ringing, or the call you're on
        if self.ringing and self.ringing["friend"]["uuid"] == p["uuid"]:
            bar = QFrame(objectName="ringBar")
            h = QHBoxLayout(bar)
            h.setContentsMargins(14, 8, 10, 8)
            what = "and wants to share their screen" if self.ringing.get("screen") else ""
            h.addWidget(QLabel(f"{p['name']} is calling {what}".strip(), objectName="rowName"), 1)
            yes = _icon_button("phone", "Answer", "go")
            yes.clicked.connect(self.answer)
            no = _icon_button("phone_off", "Decline", "stop")
            no.clicked.connect(self.decline)
            h.addWidget(yes)
            h.addWidget(no)
            self.cv.addWidget(bar)
        if self.call and self.call.friend["uuid"] == p["uuid"]:
            bar = QFrame(objectName="callBar")
            h = QHBoxLayout(bar)
            h.setContentsMargins(14, 8, 10, 8)
            text = {"calling": f"Calling {p['name']}…", "joining": "Connecting…"}.get(
                self.call.state, f"On a call with {p['name']} · {minutes(self.call.duration())}")
            self.call_time = QLabel(text, objectName="rowName")
            h.addWidget(self.call_time, 1)
            mute = _icon_button("mic_off" if self.call.muted else "mic", "Unmute" if self.call.muted else "Mute",
                                "on" if self.call.muted else "plain")
            mute.clicked.connect(self.toggle_mute)
            share = _icon_button("screen", "Stop sharing" if self.call.sharing else "Share your screen",
                                 "on" if self.call.sharing else "plain")
            share.setEnabled(self.call.state == "live")
            share.clicked.connect(self.toggle_screen)
            end = _icon_button("phone_off", "Hang up", "stop")
            end.clicked.connect(self.hang_up)
            for b in (mute, share, end):
                h.addWidget(b)
            self.cv.addWidget(bar)

        # messages
        holder = QWidget()
        self.msg_list = QVBoxLayout(holder)
        self.msg_list.setContentsMargins(0, 4, 6, 4)
        self.msg_list.setSpacing(4)
        self.msg_area = scroll(holder)
        self.cv.addWidget(self.msg_area, 1)
        self.render_messages()

        # typing
        row = QHBoxLayout()
        row.setSpacing(8)
        self.input = QLineEdit(placeholderText=f"Message {p['name']}")
        self.input.setMaxLength(4000)
        self.input.returnPressed.connect(self.send_text)
        row.addWidget(self.input, 1)
        send = _icon_button("send", "Send", "on")
        send.clicked.connect(self.send_text)
        row.addWidget(send)
        self.cv.addLayout(row)
        QTimer.singleShot(0, self.input.setFocus)

    def render_messages(self):
        if self.msg_list is None:
            return
        try:
            clear(self.msg_list)
        except RuntimeError:
            return
        msgs = self.chats.get(self.selected, [])
        if not msgs:
            hint = QLabel("No messages yet. Say hi!", objectName="faint")
            hint.setAlignment(Qt.AlignCenter)
            self.msg_list.addStretch()
            self.msg_list.addWidget(hint)
            self.msg_list.addStretch()
            return
        self.msg_list.addStretch()
        last_day = None
        for m in msgs:
            day = datetime.fromtimestamp(m["at"] / 1000).date()
            if day != last_day:
                last_day = day
                label = "Today" if day == datetime.now().date() else day.strftime("%A %d %B")
                d = QLabel(label, objectName="callLine")
                d.setAlignment(Qt.AlignCenter)
                self.msg_list.addSpacing(6)
                self.msg_list.addWidget(d)
            self.msg_list.addWidget(self._message(m))
        QTimer.singleShot(0, self._to_bottom)

    def _to_bottom(self):
        try:
            bar = self.msg_area.verticalScrollBar()
            bar.setValue(bar.maximum())
        except (RuntimeError, AttributeError):
            pass

    def _message(self, m):
        p = m.get("payload")
        row = QWidget()
        h = QHBoxLayout(row)
        h.setContentsMargins(0, 0, 0, 0)
        if p is None:
            line = QLabel("A message from before this PC was added. Open it on the PC it was sent to.",
                          objectName="callLine")
            line.setAlignment(Qt.AlignCenter)
            h.addWidget(line, 1)
            return row
        t = p.get("t")
        if t in ("call", "ended"):
            if t == "call":
                if m["mine"]:
                    return QWidget()   # the call's end says how it went
                text = "Incoming call"
            elif p.get("missed"):
                text = "Missed call" if not m["mine"] else "No answer"
            elif p.get("s"):
                text = f"Call · {minutes(p['s'])}"
            else:
                text = "Call declined" if not m["mine"] else "Call ended"
            line = QLabel(f"{text} · {clock(m['at'])}", objectName="callLine")
            line.setAlignment(Qt.AlignCenter)
            h.addWidget(line, 1)
            return row
        if t != "text":
            return QWidget()
        bubble = QLabel(p.get("b", ""), objectName="bubbleMine" if m["mine"] else "bubbleTheirs")
        bubble.setWordWrap(True)
        bubble.setTextInteractionFlags(Qt.TextSelectableByMouse)
        bubble.setMaximumWidth(460)
        bubble.ensurePolished()
        widest = max((bubble.fontMetrics().horizontalAdvance(line) for line in p.get("b", "").split("\n")), default=0)
        bubble.setMinimumWidth(min(460, widest + 30))
        tip = clock(m["at"])
        if m.get("failed"):
            tip = f"Not sent: {m['failed']}"
            bubble.setStyleSheet("background: #8a3b3b;")
        elif m.get("pending"):
            tip = "Sending…"
        bubble.setToolTip(tip)
        if m["mine"]:
            h.addStretch()
            h.addWidget(bubble)
        else:
            h.addWidget(bubble)
            h.addStretch()
        return row
