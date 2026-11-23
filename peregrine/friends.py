"""Light Client friends: friend requests, chat and calls with other Light Client players.

People are found by their Minecraft name. The service (profiles/friends.js) only ever gets
locked messages. Each launcher makes its own key pair the first time (the private half never
leaves this PC) and every message is locked once for each of your friend's launchers and once
for each of your own other launchers. Calls use a fresh key that's sent the same way.

A message is locked with X25519 (a one-off key pair per copy), HKDF-SHA256 and
ChaCha20-Poly1305. The safety code for a friend is made from both of your launchers' public
keys: if it reads the same on both screens, nobody in between swapped a key.
"""

import base64
import hashlib
import json
import os
import platform
import re
import threading
import time
import uuid as uuidlib

from . import PROFILES_URL, net, paths

SESSION_FILE = "session.json"
DEVICE_FILE = "device.json"
NAMES_FILE = "names.json"
INFO = b"light-client-friends-v1"


class FriendsError(Exception):
    pass


def available() -> bool:
    return bool(PROFILES_URL)


def _folder():
    f = paths.DATA / "friends"
    f.mkdir(parents=True, exist_ok=True)
    return f


def _read(name: str, default=None):
    try:
        return json.loads((_folder() / name).read_text())
    except (OSError, ValueError):
        return default


def _write(name: str, data, private: bool = False) -> None:
    f = _folder() / name
    tmp = f.with_suffix(".tmp")
    tmp.write_text(json.dumps(data))
    if private:
        try:
            os.chmod(tmp, 0o600)
        except OSError:
            pass
    tmp.replace(f)


def _b64(b: bytes) -> str:
    return base64.b64encode(b).decode()


def _unb64(s: str) -> bytes:
    return base64.b64decode(s)


# ---------------------------------------------------------------------- locking messages

def _crypto():
    try:
        from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey
        from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305
        from cryptography.hazmat.primitives.hashes import SHA256
        from cryptography.hazmat.primitives.kdf.hkdf import HKDF
        from cryptography.hazmat.primitives import serialization
    except ImportError:
        raise FriendsError("Friends need a newer Light Client (a part for encryption is missing). Update Light Client.")
    return X25519PrivateKey, X25519PublicKey, ChaCha20Poly1305, SHA256, HKDF, serialization


def _raw(pub) -> bytes:
    *_, serialization = _crypto()
    return pub.public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)


def device() -> dict:
    """This launcher's id and key pair, made the first time."""
    d = _read(DEVICE_FILE)
    if d and d.get("id") and d.get("priv"):
        return d
    X25519PrivateKey, *_ , serialization = _crypto()
    key = X25519PrivateKey.generate()
    priv = key.private_bytes(serialization.Encoding.Raw, serialization.PrivateFormat.Raw, serialization.NoEncryption())
    d = {"id": uuidlib.uuid4().hex[:24], "priv": _b64(priv), "pub": _b64(_raw(key.public_key())),
         "label": (platform.node() or "PC")[:32]}
    _write(DEVICE_FILE, d, private=True)
    return d


def _derive(shared: bytes, eph: bytes, to: bytes) -> bytes:
    _, _, _, SHA256, HKDF, _ = _crypto()
    return HKDF(algorithm=SHA256(), length=32, salt=None, info=INFO + eph + to).derive(shared)


def seal(data: bytes, pub_b64: str) -> str:
    """Lock data so only the holder of pub's private key can read it."""
    X25519PrivateKey, X25519PublicKey, ChaCha20Poly1305, *_ = _crypto()
    to = _unb64(pub_b64)
    eph = X25519PrivateKey.generate()
    eph_pub = _raw(eph.public_key())
    key = _derive(eph.exchange(X25519PublicKey.from_public_bytes(to)), eph_pub, to)
    nonce = os.urandom(12)
    return _b64(eph_pub + nonce + ChaCha20Poly1305(key).encrypt(nonce, data, None))


def unseal(box: str, dev: dict = None) -> bytes:
    X25519PrivateKey, X25519PublicKey, ChaCha20Poly1305, *_ = _crypto()
    dev = dev or device()
    raw = _unb64(box)
    eph_pub, nonce, ct = raw[:32], raw[32:44], raw[44:]
    me = X25519PrivateKey.from_private_bytes(_unb64(dev["priv"]))
    key = _derive(me.exchange(X25519PublicKey.from_public_bytes(eph_pub)), eph_pub, _unb64(dev["pub"]))
    return ChaCha20Poly1305(key).decrypt(nonce, ct, None)


class CallLock:
    """Locks and unlocks call frames with the call's own key."""

    def __init__(self, key: bytes):
        ChaCha20Poly1305 = _crypto()[2]
        self.aead = ChaCha20Poly1305(key)

    def lock(self, data: bytes) -> bytes:
        nonce = os.urandom(12)
        return nonce + self.aead.encrypt(nonce, data, None)

    def unlock(self, data: bytes) -> bytes:
        return self.aead.decrypt(data[:12], data[12:], None)


def safety_code(mine: list, theirs: list) -> str:
    """Twelve digits from both sides' launcher keys, in groups of four."""
    keys = sorted(set(mine) | set(theirs))
    h = hashlib.sha256(INFO + b"".join(_unb64(k) for k in keys)).digest()
    n = int.from_bytes(h[:8], "big") % 10 ** 12
    s = f"{n:012d}"
    return f"{s[:4]} {s[4:8]} {s[8:]}"


# ---------------------------------------------------------------------- names

_names = None
_names_lock = threading.Lock()


def name_of(uuid: str, claimed: str = "") -> str:
    """The name Mojang has for an account (cached a day), or what the service said."""
    global _names
    with _names_lock:
        if _names is None:
            _names = _read(NAMES_FILE, {}) or {}
        hit = _names.get(uuid)
    if hit and time.time() - hit[1] < 86400:
        return hit[0]
    try:
        r = net.session.get(f"https://sessionserver.mojang.com/session/minecraft/profile/{uuid}", timeout=10)
        if r.status_code == 200:
            name = r.json().get("name") or claimed
            with _names_lock:
                _names[uuid] = [name, time.time()]
                _write(NAMES_FILE, _names)
            return name
    except Exception:
        pass
    return hit[0] if hit else (claimed or "Player")


def cached_name(uuid: str, claimed: str = "") -> str:
    """Like name_of, without asking Mojang (for drawing a list straight away)."""
    with _names_lock:
        hit = (_names or {}).get(uuid)
    return hit[0] if hit else (claimed or "Player")


# ---------------------------------------------------------------------- the service

class Client:
    """Talks to the friends service for one Minecraft account. Every method blocks: call
    them off the UI thread."""

    def __init__(self, account: dict):
        if not available():
            raise FriendsError("Friends aren't switched on in this version of Light Client.")
        self.account = account
        self.uuid = account["uuid"].replace("-", "").lower()
        self.dev = device()
        self._keys = {}
        self._lock = threading.Lock()

    def url(self, path: str) -> str:
        return PROFILES_URL.rstrip("/") + path

    def ws_url(self, path: str) -> str:
        return re.sub(r"^http", "ws", self.url(path))

    # ---- signing in

    def token(self, force: bool = False) -> str:
        with self._lock:
            s = _read(SESSION_FILE, {}) or {}
            if not force and s.get("uuid") == self.uuid and s.get("expires", 0) > time.time() + 3600:
                return s["token"]
            from . import auth, config, profiles
            account = auth.refresh(self.account, config.load().get("client_id", ""))
            auth.add_account(account)
            self.account = account
            try:
                proof = profiles._prove(account)
            except RuntimeError as e:
                raise FriendsError(str(e))
            r = net.session.post(self.url("/v2/session"), json=dict(proof, name=account["name"]), timeout=30)
            if r.status_code == 501:
                raise FriendsError("Friends aren't set up on the Light Client service yet.")
            if r.status_code != 200:
                raise FriendsError(_error(r, f"Couldn't sign in to friends ({r.status_code})"))
            body = r.json()
            _write(SESSION_FILE, {"uuid": self.uuid, "token": body["token"],
                                  "expires": time.time() + body.get("days", 7) * 86400 - 600}, private=True)
            token = body["token"]
        d = self.dev
        self._call("PUT", "/v2/device", token=token, json={"id": d["id"], "pub": d["pub"], "label": d.get("label", "")})
        return token

    def headers(self, token: str = None) -> dict:
        return {"Authorization": "Bearer " + (token or self.token()), "X-Device": self.dev["id"]}

    def _call(self, method: str, path: str, token: str = None, **kw):
        r = net.session.request(method, self.url(path), headers=self.headers(token), timeout=30, **kw)
        if r.status_code == 401 and token is None:
            r = net.session.request(method, self.url(path), headers=self.headers(self.token(force=True)), timeout=30, **kw)
        if r.status_code >= 400:
            raise FriendsError(_error(r, f"The friends service said no ({r.status_code})"))
        return r.json()

    # ---- friends and requests

    def me(self) -> dict:
        data = self._call("GET", "/v2/me")
        for group in ("friends", "incoming", "outgoing"):
            for p in data.get(group, []):
                p["name"] = cached_name(p["uuid"], p.get("name", ""))
        return data

    def refresh_names(self, data: dict) -> dict:
        for group in ("friends", "incoming", "outgoing"):
            for p in data.get(group, []):
                p["name"] = name_of(p["uuid"], p.get("name", ""))
        return data

    def add(self, name: str) -> dict:
        from . import profiles
        try:
            uuid, spelled = profiles.player_id(name)
        except LookupError as e:
            raise FriendsError(str(e))
        except RuntimeError as e:
            raise FriendsError(str(e))
        if uuid == self.uuid:
            raise FriendsError("That's you.")
        return dict(self._call("POST", "/v2/request", json={"uuid": uuid, "name": spelled}), name=spelled)

    def accept(self, uuid: str):
        return self._call("POST", "/v2/accept", json={"uuid": uuid})

    def decline(self, uuid: str):
        return self._call("POST", "/v2/decline", json={"uuid": uuid})

    def cancel(self, uuid: str):
        return self._call("POST", "/v2/cancel", json={"uuid": uuid})

    def remove(self, uuid: str):
        self._keys.pop(uuid, None)
        return self._call("POST", "/v2/remove", json={"uuid": uuid})

    def keys(self, uuid: str, fresh: bool = False) -> list:
        if fresh or uuid not in self._keys:
            self._keys[uuid] = self._call("GET", f"/v2/keys/{uuid}").get("devices", [])
        return self._keys[uuid]

    def forget_keys(self, uuid: str = None) -> None:
        if uuid:
            self._keys.pop(uuid, None)
        else:
            self._keys.clear()

    def my_devices(self) -> list:
        return self.me().get("devices", [])

    def code(self, uuid: str) -> str:
        return safety_code([d["pub"] for d in self.my_devices()], [d["pub"] for d in self.keys(uuid, fresh=True)])

    # ---- messages

    def send(self, uuid: str, payload: dict) -> dict:
        """Lock payload for every launcher of theirs and of yours, and send it."""
        data = json.dumps(payload, separators=(",", ":")).encode()
        theirs = self.keys(uuid)
        if not theirs:
            theirs = self.keys(uuid, fresh=True)
        if not theirs:
            raise FriendsError("They haven't opened Light Client since adding you, so there's no way to lock a "
                               "message for them yet.")
        box = {d["id"]: seal(data, d["pub"]) for d in theirs}
        for d in self.my_devices():
            box[d["id"]] = seal(data, d["pub"])
        cid = uuidlib.uuid4().hex[:16]
        return dict(self._call("POST", "/v2/send", json={"to": uuid, "cid": cid, "box": box}), cid=cid)

    def text(self, uuid: str, text: str) -> dict:
        return self.send(uuid, {"t": "text", "b": text[:4000]})

    def messages(self, uuid: str, after: int = 0, before: int = 0, limit: int = 60) -> list:
        q = f"?after={after}&limit={limit}" + (f"&before={before}" if before else "")
        out = []
        for m in self._call("GET", f"/v2/messages/{uuid}{q}").get("messages", []):
            payload = None
            if m.get("ct"):
                try:
                    payload = json.loads(unseal(m["ct"], self.dev))
                except Exception:
                    payload = None
            m["payload"] = payload   # None: sent before this PC was added, or not for it
            out.append(m)
        return out

    def read(self, uuid: str, up_to: int):
        return self._call("POST", "/v2/read", json={"uuid": uuid, "upTo": up_to})

    # ---- live updates and calls

    def live(self):
        from .wsclient import WebSocket
        return WebSocket(self.ws_url(f"/v2/live?d={self.dev['id']}"), self.headers()).connect()

    def join_call(self, room: str):
        from .wsclient import WebSocket
        return WebSocket(self.ws_url(f"/v2/call/{room}"), self.headers()).connect()

    def ring(self, uuid: str, video: bool = False) -> tuple:
        """Start a call: a new room and key, sent to them locked. Returns (room, key)."""
        room = base64.urlsafe_b64encode(os.urandom(18)).decode().rstrip("=")
        key = os.urandom(32)
        self.send(uuid, {"t": "call", "room": room, "key": _b64(key), "screen": bool(video)})
        return room, key

    def hang_up(self, uuid: str, room: str, seconds: int = 0, missed: bool = False):
        return self.send(uuid, {"t": "ended", "room": room, "s": int(seconds), "missed": bool(missed)})


def _error(r, fallback: str) -> str:
    try:
        return r.json().get("error") or fallback
    except ValueError:
        return fallback


def save_snapshot(people: list) -> None:
    """Your friends and who's online, for the game's Friends page (the launcher keeps it
    fresh while it runs, the game reads it)."""
    keep = [{"gamertag": p.get("name", ""), "online": bool(p.get("online")),
             "status": "Online" if p.get("online") else "", "minecraft": bool(p.get("online")),
             "picture": f"https://mc-heads.net/avatar/{p.get('uuid', '')}/64"} for p in people]
    f = paths.DATA / "client" / "friends.json"
    try:
        f.parent.mkdir(parents=True, exist_ok=True)
        tmp = f.with_suffix(".tmp")
        tmp.write_text(json.dumps({"updated": int(time.time()), "friends": keep}))
        tmp.replace(f)
    except OSError:
        pass
