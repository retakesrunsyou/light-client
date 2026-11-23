"""A call with Light Client friends: voice, and your screen if you share it.

Everything that leaves this PC is locked with the call's key first (friends.CallLock), so the
service only passes along bytes it can't read. Frames are [kind][locked data]; the service adds
the sender's seat number after the kind.

Voice is 16 kHz mono, sent in 60 ms pieces squeezed to 8 bits with mu-law (the telephone
standard, half the size and still clear for speech). A shared screen goes out as a JPEG up to
1280 wide, a few times a second.
"""

import json
import queue
import threading
import time
from array import array

from PySide6.QtCore import QBuffer, QByteArray, QIODevice, QObject, QTimer, Qt, Signal
from PySide6.QtGui import QGuiApplication, QImage

from .. import friends, wsclient

AUDIO, SCREEN, CONTROL = 1, 2, 3
RATE = 16000
PIECE = RATE * 60 // 1000 * 2      # bytes of 16-bit audio in 60 ms
SCREEN_FPS = 4
SCREEN_WIDTH = 1280


# ---------------------------------------------------------------------- mu-law

def _ulaw_tables():
    BIAS, CLIP = 0x84, 32635
    enc = bytearray(65536)
    for u in range(65536):
        s = u - 65536 if u >= 32768 else u
        sign = 0x80 if s < 0 else 0
        s = min(abs(s), CLIP) + BIAS
        exp, mask = 7, 0x4000
        while exp > 0 and not s & mask:
            exp -= 1
            mask >>= 1
        mant = (s >> (exp + 3)) & 0x0F
        enc[u] = ~(sign | (exp << 4) | mant) & 0xFF
    lo, hi = bytearray(256), bytearray(256)
    for b in range(256):
        v = ~b & 0xFF
        sign, exp, mant = v & 0x80, (v >> 4) & 7, v & 0x0F
        s = (((mant << 3) + BIAS) << exp) - BIAS
        s = -s if sign else s
        u = s & 0xFFFF
        lo[b], hi[b] = u & 0xFF, u >> 8
    return bytes(enc), bytes(lo), bytes(hi)


_ENC, _LO, _HI = _ulaw_tables()


def ulaw_encode(pcm: bytes) -> bytes:
    samples = array("H")
    samples.frombytes(pcm[: len(pcm) // 2 * 2])
    return bytes(_ENC[s] for s in samples)


def ulaw_decode(data: bytes) -> bytes:
    out = bytearray(len(data) * 2)
    out[0::2] = data.translate(_LO)
    out[1::2] = data.translate(_HI)
    return bytes(out)


# ---------------------------------------------------------------------- the call

class Call(QObject):
    """One call. States: "calling" (waiting for them), "live", "ended"."""
    state_changed = Signal(str)
    members_changed = Signal(list)
    screen_frame = Signal(object, str)     # QImage, who
    sharing_changed = Signal(str, bool)    # who, sharing
    problem = Signal(str)
    _incoming = Signal(int, int, bytes)    # kind, seat, data (from the network thread)
    _text = Signal(str)
    _closed = Signal(str)

    def __init__(self, client, friend: dict, room: str, key: bytes, outgoing: bool, parent=None):
        super().__init__(parent)
        self.client, self.friend, self.room, self.outgoing = client, friend, room, outgoing
        self.lock = friends.CallLock(key)
        self.state = "calling" if outgoing else "joining"
        self.started = 0.0
        self.muted = False
        self.sharing = False
        self.seats = {}          # seat -> {"uuid", "name"}
        self.my_seat = 0
        self.ws = None
        self.out = queue.Queue(maxsize=64)
        self.screen_busy = False
        self.mic = self.mic_io = None
        self.speakers = {}       # seat -> (sink, io)
        self.mic_buf = b""
        self.capture = None
        self.screen_timer = None
        self._incoming.connect(self._on_frame, Qt.QueuedConnection)
        self._text.connect(self._on_text, Qt.QueuedConnection)
        self._closed.connect(self._on_closed, Qt.QueuedConnection)
        self.give_up = QTimer(self, singleShot=True, interval=45_000, timeout=self._no_answer)

    # ---- starting and ending

    def start(self):
        threading.Thread(target=self._run, daemon=True).start()
        threading.Thread(target=self._send_loop, daemon=True).start()
        if self.outgoing:
            self.give_up.start()
        self.state_changed.emit(self.state)

    def _run(self):
        try:
            self.ws = self.client.join_call(self.room)
        except Exception as e:
            self._closed.emit(str(e) or "Couldn't connect the call")
            return
        last_ping = time.time()
        try:
            while True:
                kind, data = self.ws.recv()
                if kind == wsclient.TEXT:
                    self._text.emit(data)
                elif len(data) > 2:
                    try:
                        plain = self.lock.unlock(data[2:])
                    except Exception:
                        continue   # not locked with our key: ignore it
                    self._incoming.emit(data[0], data[1], plain)
                if time.time() - last_ping > 25:
                    self.out.put_nowait(("text", "ping"))
                    last_ping = time.time()
        except wsclient.Closed as e:
            if self.state != "ended":
                self._closed.emit(str(e))

    def _send_loop(self):
        while True:
            item = self.out.get()
            if item is None:
                return
            what, data = item
            ws = self.ws
            if ws is None:
                continue
            try:
                ws.send_text(data) if what == "text" else ws.send_binary(data)
            except wsclient.Closed:
                return
            finally:
                if what == "screen":
                    self.screen_busy = False

    def _send(self, kind: int, data: bytes, what: str = "bin"):
        if self.state == "ended":
            return
        try:
            self.out.put_nowait((what, bytes([kind]) + self.lock.lock(data)))
        except queue.Full:
            if what == "screen":   # the line is slow: drop this piece rather than fall behind
                self.screen_busy = False

    def hang_up(self, reason: str = ""):
        if self.state == "ended":
            return
        was_live = self.state == "live"
        self.state = "ended"
        self.give_up.stop()
        self._stop_audio()
        self.stop_screen()
        try:
            self.out.put_nowait(None)
        except queue.Full:
            pass
        if self.ws:
            threading.Thread(target=self.ws.close, daemon=True).start()
        seconds = int(time.time() - self.started) if was_live else 0
        self.ended_seconds = seconds
        self.ended_reason = reason
        self.state_changed.emit("ended")

    def duration(self) -> int:
        return int(time.time() - self.started) if self.state == "live" else 0

    def _no_answer(self):
        if self.state == "calling":
            self.hang_up("no answer")

    def _on_closed(self, why: str):
        if self.state != "ended":
            self.problem.emit(why)
            self.hang_up("lost")

    # ---- the room

    def _on_text(self, text: str):
        if text == "pong":
            return
        try:
            msg = json.loads(text)
        except ValueError:
            return
        if msg.get("type") == "welcome":
            self.my_seat = msg.get("id", 0)
        elif msg.get("type") == "members":
            self.seats = {m["id"]: m for m in msg.get("members", [])}
            others = [m for s, m in self.seats.items() if s != self.my_seat]
            self.members_changed.emit(others)
            if others and self.state in ("calling", "joining"):
                self.state = "live"
                self.started = time.time()
                self.give_up.stop()
                self._start_audio()
                self.state_changed.emit("live")
                self._control()
            elif not others and self.state == "live":
                self.hang_up("they left")
            for seat in list(self.speakers):
                if seat not in self.seats:
                    sink, _ = self.speakers.pop(seat)
                    sink.stop()

    def _who(self, seat: int) -> str:
        m = self.seats.get(seat)
        return friends.cached_name(m["uuid"], m.get("name", "")) if m else "Someone"

    def _on_frame(self, kind: int, seat: int, data: bytes):
        if kind == AUDIO:
            self._play(seat, data)
        elif kind == SCREEN:
            img = QImage.fromData(data, "JPG")
            if not img.isNull():
                self.screen_frame.emit(img, self._who(seat))
        elif kind == CONTROL:
            try:
                c = json.loads(data)
            except ValueError:
                return
            self.sharing_changed.emit(self._who(seat), bool(c.get("sharing")))

    def _control(self):
        self._send(CONTROL, json.dumps({"muted": self.muted, "sharing": self.sharing}).encode())

    # ---- voice

    def _format(self):
        from PySide6.QtMultimedia import QAudioFormat
        f = QAudioFormat()
        f.setSampleRate(RATE)
        f.setChannelCount(1)
        f.setSampleFormat(QAudioFormat.Int16)
        return f

    def _start_audio(self):
        try:
            from PySide6.QtMultimedia import QAudioSource, QMediaDevices
        except ImportError:
            self.problem.emit("This Light Client can't use the microphone (Qt Multimedia is missing).")
            return
        dev = QMediaDevices.defaultAudioInput()
        if dev.isNull():
            self.problem.emit("No microphone found. They can still hear nothing from you, but you'll hear them.")
            return
        self.mic = QAudioSource(dev, self._format(), self)
        self.mic.setBufferSize(PIECE * 4)
        self.mic_io = self.mic.start()
        if self.mic_io is None:
            self.problem.emit("Couldn't open the microphone.")
            return
        self.mic_io.readyRead.connect(self._mic_ready)

    def _mic_ready(self):
        if not self.mic_io:
            return
        self.mic_buf += bytes(self.mic_io.readAll())
        while len(self.mic_buf) >= PIECE:
            piece, self.mic_buf = self.mic_buf[:PIECE], self.mic_buf[PIECE:]
            if not self.muted:
                self._send(AUDIO, ulaw_encode(piece))

    def _play(self, seat: int, data: bytes):
        if seat not in self.speakers:
            try:
                from PySide6.QtMultimedia import QAudioSink, QMediaDevices
            except ImportError:
                return
            sink = QAudioSink(QMediaDevices.defaultAudioOutput(), self._format(), self)
            sink.setBufferSize(PIECE * 8)   # about half a second of room for an uneven line
            io = sink.start()
            if io is None:
                return
            self.speakers[seat] = (sink, io)
        sink, io = self.speakers[seat]
        pcm = ulaw_decode(data)
        if sink.bytesFree() >= len(pcm):
            io.write(QByteArray(pcm))
        # else: it's running behind; skip this piece so the delay doesn't grow

    def set_muted(self, muted: bool):
        self.muted = muted
        self._control()

    def _stop_audio(self):
        if self.mic:
            self.mic.stop()
        self.mic = self.mic_io = None
        for sink, _ in self.speakers.values():
            sink.stop()
        self.speakers.clear()

    # ---- sharing your screen

    def start_screen(self):
        if self.sharing or self.state != "live":
            return
        self.sharing = True
        started = False
        try:
            from PySide6.QtMultimedia import QMediaCaptureSession, QScreenCapture, QVideoSink
            self.capture = QScreenCapture(self)
            self.capture.setScreen(QGuiApplication.primaryScreen())
            self.session = QMediaCaptureSession(self)
            self.session.setScreenCapture(self.capture)
            self.sink = QVideoSink(self)
            self.session.setVideoSink(self.sink)
            self.last_frame = 0.0
            self.sink.videoFrameChanged.connect(self._video_frame)
            self.capture.errorOccurred.connect(lambda *_: self._fallback_screen())
            self.capture.start()
            started = True
        except Exception:
            started = False
        if not started:
            self._fallback_screen()
        self._control()

    def _fallback_screen(self):
        """Older systems: grab the screen with a timer instead."""
        if self.screen_timer or not self.sharing:
            return
        if self.capture:
            self.capture.stop()
            self.capture = None
        self.screen_timer = QTimer(self, interval=1000 // SCREEN_FPS, timeout=self._grab)
        self.screen_timer.start()

    def _grab(self):
        screen = QGuiApplication.primaryScreen()
        if screen:
            self._send_image(screen.grabWindow(0).toImage())

    def _video_frame(self, frame):
        now = time.time()
        if now - self.last_frame < 1.0 / SCREEN_FPS:
            return
        self.last_frame = now
        self._send_image(frame.toImage())

    def _send_image(self, img: QImage):
        if img.isNull() or self.screen_busy:
            return
        if img.width() > SCREEN_WIDTH:
            img = img.scaledToWidth(SCREEN_WIDTH, Qt.SmoothTransformation)
        buf = QBuffer()
        buf.open(QIODevice.WriteOnly)
        img.save(buf, "JPG", 55)
        data = bytes(buf.data())
        if len(data) < 900_000:
            self.screen_busy = True
            self._send(SCREEN, data, "screen")

    def stop_screen(self):
        was = self.sharing
        self.sharing = False
        if self.capture:
            self.capture.stop()
            self.capture = None
        if self.screen_timer:
            self.screen_timer.stop()
            self.screen_timer = None
        if was and self.state == "live":
            self._control()
