"""A small WebSocket client (RFC 6455), enough for friends and calls.

Qt's WebSocket module isn't in every install of the launcher, and nothing else here needs
an extra package for this, so it's done by hand: one TLS socket, frames in and out.
Sending is safe from any thread; receiving is meant for one thread that loops on recv().
"""

import base64
import os
import socket
import ssl
import struct
import threading
from urllib.parse import urlsplit

TEXT, BINARY, CLOSE, PING, PONG = 0x1, 0x2, 0x8, 0x9, 0xA
MAX_MESSAGE = 4 * 1024 * 1024


class Closed(Exception):
    pass


def _context() -> ssl.SSLContext:
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        return ssl.create_default_context()


def _mask(data: bytes, key: bytes) -> bytes:
    n = len(data)
    if not n:
        return b""
    pad = (key * (n // 4 + 1))[:n]
    return (int.from_bytes(data, "big") ^ int.from_bytes(pad, "big")).to_bytes(n, "big")


class WebSocket:
    def __init__(self, url: str, headers: dict = None, timeout: float = 20):
        self.url = url
        self.headers = headers or {}
        self.timeout = timeout
        self.sock = None
        self._send_lock = threading.Lock()
        self._buf = b""
        self.closed = False

    def connect(self) -> "WebSocket":
        u = urlsplit(self.url)
        secure = u.scheme == "wss"
        port = u.port or (443 if secure else 80)
        raw = socket.create_connection((u.hostname, port), timeout=self.timeout)
        self.sock = _context().wrap_socket(raw, server_hostname=u.hostname) if secure else raw
        key = base64.b64encode(os.urandom(16)).decode()
        path = (u.path or "/") + (("?" + u.query) if u.query else "")
        lines = [f"GET {path} HTTP/1.1", f"Host: {u.hostname}", "Upgrade: websocket", "Connection: Upgrade",
                 f"Sec-WebSocket-Key: {key}", "Sec-WebSocket-Version: 13", "User-Agent: LightClient"]
        lines += [f"{k}: {v}" for k, v in self.headers.items()]
        self.sock.sendall(("\r\n".join(lines) + "\r\n\r\n").encode())
        head = b""
        while b"\r\n\r\n" not in head:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise Closed("The service closed the connection")
            head += chunk
            if len(head) > 65536:
                raise Closed("Odd answer from the service")
        head, self._buf = head.split(b"\r\n\r\n", 1)
        status = head.split(b"\r\n", 1)[0].decode(errors="replace")
        if " 101 " not in status + " ":
            code = status.split(" ")[1] if " " in status else "?"
            raise Closed({"401": "Sign in again", "409": "That call is full"}.get(code, f"The service said no ({code})"))
        self.sock.settimeout(None)
        return self

    # ---- sending

    def _send(self, opcode: int, data: bytes) -> None:
        if self.closed:
            raise Closed("closed")
        n = len(data)
        head = bytes([0x80 | opcode])
        if n < 126:
            head += bytes([0x80 | n])
        elif n < 65536:
            head += bytes([0x80 | 126]) + struct.pack(">H", n)
        else:
            head += bytes([0x80 | 127]) + struct.pack(">Q", n)
        key = os.urandom(4)
        frame = head + key + _mask(data, key)
        with self._send_lock:
            try:
                self.sock.sendall(frame)
            except OSError as e:
                self.closed = True
                raise Closed(str(e))

    def send_text(self, text: str) -> None:
        self._send(TEXT, text.encode())

    def send_binary(self, data: bytes) -> None:
        self._send(BINARY, data)

    # ---- receiving

    def _read(self, n: int) -> bytes:
        while len(self._buf) < n:
            try:
                chunk = self.sock.recv(max(65536, n - len(self._buf)))
            except OSError as e:
                raise Closed(str(e))
            if not chunk:
                raise Closed("connection closed")
            self._buf += chunk
        out, self._buf = self._buf[:n], self._buf[n:]
        return out

    def recv(self):
        """The next whole message: (TEXT, str) or (BINARY, bytes). Raises Closed."""
        parts, kind = [], None
        while True:
            b1, b2 = self._read(2)
            fin, opcode = b1 & 0x80, b1 & 0x0F
            n = b2 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._read(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._read(8))[0]
            if n > MAX_MESSAGE:
                self.close()
                raise Closed("message too big")
            key = self._read(4) if b2 & 0x80 else None
            data = self._read(n)
            if key:
                data = _mask(data, key)
            if opcode == PING:
                self._send(PONG, data)
                continue
            if opcode == PONG:
                continue
            if opcode == CLOSE:
                self.closed = True
                code = struct.unpack(">H", data[:2])[0] if len(data) >= 2 else 1000
                raise Closed(f"closed ({code})")
            if opcode in (TEXT, BINARY):
                kind, parts = opcode, [data]
            else:   # continuation
                parts.append(data)
            if fin:
                body = b"".join(parts)
                return (TEXT, body.decode(errors="replace")) if kind == TEXT else (BINARY, body)

    def close(self) -> None:
        if not self.closed:
            try:
                self._send(CLOSE, struct.pack(">H", 1000))
            except Closed:
                pass
        self.closed = True
        try:
            self.sock.close()
        except Exception:
            pass
