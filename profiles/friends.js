// Light Client friends: friend requests, chat and calls between Light Client players.
//
// Who you are: the launcher proves its Minecraft account once with the same chat-key check
// the profile service already uses (see proven() in worker.js) and gets a session token back.
//
// What the service can read: who is friends with whom, and when messages were sent. It can't
// read messages or listen to calls. Every launcher makes its own key pair and only the public
// half comes here. A message is locked separately for each of the receiver's launchers (and
// the sender's other launchers, so their history matches), and calls are locked with a key
// that only travels inside such a message. Locked messages are kept for 30 days so history
// shows up on a second PC; after that they're deleted.
//
// Each player gets a Person (a Durable Object with its own small SQLite database) holding
// their friends, requests, launchers and messages, plus the live connection that tells the
// launcher about new messages straight away. A call is a CallRoom that passes locked audio
// and screen frames between the people in it.

const UUID = /^[0-9a-f]{32}$/;
const NAME = /^[A-Za-z0-9_]{3,16}$/;
const DEVICE = /^[A-Za-z0-9_-]{8,40}$/;
const ROOM = /^[A-Za-z0-9_-]{16,64}$/;
const KEEP_DAYS = 30;
const MAX_DEVICES = 6;
const MAX_FRIENDS = 300;
const MAX_MESSAGE = 96 * 1024;      // one locked message, all its copies together
const MAX_FRAME = 1024 * 1024;      // one call frame (a screen picture is the big one)
const MAX_CALL = 8;
const SESSION_DAYS = 7;

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "access-control-allow-origin": "*" },
  });
}

const enc = new TextEncoder();

function b64url(bytes) {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function unb64url(s) {
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/"));
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

// ------------------------------------------------------------------ session tokens

let secretKey = null;

/** The key session tokens are signed with: made once, kept in KV, cached per isolate. */
async function sessionKey(env) {
  if (secretKey) return secretKey;
  let raw = await env.PROFILES.get("friends-session-secret");
  if (!raw) {
    const a = new Uint8Array(32);
    crypto.getRandomValues(a);
    raw = b64url(a);
    await env.PROFILES.put("friends-session-secret", raw);
  }
  secretKey = await crypto.subtle.importKey("raw", unb64url(raw), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
  return secretKey;
}

async function makeToken(env, uuid, name) {
  const body = b64url(enc.encode(JSON.stringify({ u: uuid, n: name, e: Date.now() + SESSION_DAYS * 86400000 })));
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", await sessionKey(env), enc.encode(body)));
  return body + "." + b64url(sig);
}

/** {uuid, name} for a valid token, or null. */
async function readToken(env, token) {
  const [body, sig] = String(token || "").split(".");
  if (!body || !sig) return null;
  try {
    const ok = await crypto.subtle.verify("HMAC", await sessionKey(env), unb64url(sig), enc.encode(body));
    if (!ok) return null;
    const t = JSON.parse(new TextDecoder().decode(unb64url(body)));
    if (!UUID.test(t.u) || !(t.e > Date.now())) return null;
    return { uuid: t.u, name: NAME.test(t.n) ? t.n : "Player" };
  } catch {
    return null;
  }
}

function person(env, uuid) {
  return env.PERSON.get(env.PERSON.idFromName(uuid));
}

/** A request to a Person, from the service itself (never straight from a launcher). */
function inside(path, who, init = {}) {
  const headers = new Headers({ "x-uuid": who.uuid, "x-name": who.name });
  if (init.device) headers.set("x-device", init.device);
  if (init.upgrade) headers.set("Upgrade", "websocket");
  return new Request("https://person" + path, { method: init.method || "GET", headers, body: init.body });
}

// ------------------------------------------------------------------ the front door

export async function handleFriends(request, env, path, proven) {
  if (!env.PERSON || !env.CALL) return json({ error: "Friends aren't set up on this service yet. Deploy it again." }, 501);
  const url = new URL(request.url);

  if (path === "/v2/session" && request.method === "POST") {
    let body;
    try { body = await request.json(); } catch { return json({ error: "bad request" }, 400); }
    const uuid = await proven(env, body);
    if (uuid instanceof Response) return uuid;
    // Mojang can't be asked from here, so the name is the launcher's word for it. Launchers
    // show friends by the name Mojang gives for their account id, not this one.
    const name = NAME.test(body.name || "") ? body.name : "Player";
    const token = await makeToken(env, uuid, name);
    await person(env, uuid).fetch(inside("/hello", { uuid, name }, { method: "POST" }));
    return json({ token, uuid, name, days: SESSION_DAYS });
  }

  const auth = request.headers.get("authorization") || "";
  const who = await readToken(env, auth.startsWith("Bearer ") ? auth.slice(7) : url.searchParams.get("t"));
  if (!who) return json({ error: "Sign in again" }, 401);
  const device = request.headers.get("x-device") || url.searchParams.get("d") || "";

  const call = path.match(/^\/v2\/call\/([A-Za-z0-9_-]+)$/);
  if (call) {
    if (request.headers.get("Upgrade") !== "websocket") return json({ error: "a WebSocket, please" }, 426);
    if (!ROOM.test(call[1])) return json({ error: "bad call" }, 400);
    const headers = new Headers({ Upgrade: "websocket", "x-uuid": who.uuid, "x-name": who.name });
    return env.CALL.get(env.CALL.idFromName(call[1])).fetch(new Request("https://call/", { headers }));
  }
  if (path === "/v2/live") {
    if (request.headers.get("Upgrade") !== "websocket") return json({ error: "a WebSocket, please" }, 426);
    if (!DEVICE.test(device)) return json({ error: "bad device" }, 400);
    return person(env, who.uuid).fetch(inside("/live", who, { device, upgrade: true }));
  }
  if (!path.startsWith("/v2/")) return json({ error: "not found" }, 404);
  const body = request.method === "GET" ? undefined : await request.text();
  if (body && body.length > MAX_MESSAGE + 4096) return json({ error: "That's too big to send" }, 413);
  return person(env, who.uuid).fetch(inside(path.slice(3) + url.search, who, { method: request.method, body, device }));
}

// ------------------------------------------------------------------ one player

export class Person {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.sql = ctx.storage.sql;
    ctx.blockConcurrencyWhile(async () => {
      this.sql.exec(`
        CREATE TABLE IF NOT EXISTS me (k TEXT PRIMARY KEY, v TEXT);
        CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, pub TEXT NOT NULL, label TEXT, seen INTEGER);
        CREATE TABLE IF NOT EXISTS friends (uuid TEXT PRIMARY KEY, name TEXT, since INTEGER, online INTEGER DEFAULT 0,
                                            seen INTEGER DEFAULT 0, read INTEGER DEFAULT 0);
        CREATE TABLE IF NOT EXISTS requests (uuid TEXT PRIMARY KEY, name TEXT, incoming INTEGER, at INTEGER);
        CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, peer TEXT, mine INTEGER, at INTEGER,
                                             cid TEXT, box TEXT);
        CREATE INDEX IF NOT EXISTS messages_peer ON messages (peer, id);
      `);
    });
  }

  one(q, ...args) {
    const rows = this.sql.exec(q, ...args).toArray();
    return rows.length ? rows[0] : null;
  }

  all(q, ...args) {
    return this.sql.exec(q, ...args).toArray();
  }

  myName() {
    const r = this.one("SELECT v FROM me WHERE k = 'name'");
    return r ? r.v : "Player";
  }

  /** Tell every open launcher of this player something happened. */
  push(event) {
    const text = JSON.stringify(event);
    for (const ws of this.ctx.getWebSockets()) {
      try { ws.send(text); } catch {}
    }
  }

  other(uuid) {
    return person(this.env, uuid);
  }

  /** Ask another player's Person, as this player. */
  async tell(uuid, path, data) {
    const me = { uuid: this.uuid, name: this.myName() };
    try {
      const r = await this.other(uuid).fetch(inside(path, me, { method: "POST", body: JSON.stringify(data || {}) }));
      return r.ok ? await r.json() : null;
    } catch {
      return null;
    }
  }

  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname;
    const caller = request.headers.get("x-uuid");
    if (!path.startsWith("/in/")) this.uuid = caller;   // a launcher request: always this player
    const name = request.headers.get("x-name") || "Player";
    const device = request.headers.get("x-device") || "";
    let body = {};
    if (request.method !== "GET" && path !== "/live") {
      try { body = JSON.parse((await request.text()) || "{}"); } catch { return json({ error: "bad request" }, 400); }
    }
    try {
      if (path.startsWith("/in/")) return await this.fromFriend(path, caller, name, body);
      return await this.fromLauncher(request, path, url, name, device, body);
    } catch (e) {
      return json({ error: "Something went wrong on the service" }, 500);
    }
  }

  // ---- what this player's own launcher asks

  async fromLauncher(request, path, url, name, device, body) {
    const now = Date.now();
    if (path === "/hello") {
      this.sql.exec("INSERT OR REPLACE INTO me (k, v) VALUES ('name', ?), ('uuid', ?)", name, this.uuid);
      return json({ ok: true });
    }
    if (path === "/live") {
      const pair = new WebSocketPair();
      this.ctx.acceptWebSocket(pair[1]);
      pair[1].serializeAttachment({ device });
      if (this.ctx.getWebSockets().length === 1) await this.presence(true);
      this.sql.exec("UPDATE devices SET seen = ? WHERE id = ?", now, device);
      return new Response(null, { status: 101, webSocket: pair[0] });
    }
    if (path === "/me" && request.method === "GET") {
      const friends = this.all(`SELECT f.uuid, f.name, f.online, f.seen, f.since,
            (SELECT COUNT(*) FROM messages m WHERE m.peer = f.uuid AND m.mine = 0 AND m.id > f.read) AS unread,
            (SELECT MAX(at) FROM messages m WHERE m.peer = f.uuid) AS last
          FROM friends f ORDER BY last DESC, f.name`);
      return json({
        uuid: this.uuid,
        name: this.myName(),
        devices: this.all("SELECT id, pub, label, seen FROM devices ORDER BY seen DESC"),
        friends,
        incoming: this.all("SELECT uuid, name, at FROM requests WHERE incoming = 1 ORDER BY at DESC"),
        outgoing: this.all("SELECT uuid, name, at FROM requests WHERE incoming = 0 ORDER BY at DESC"),
      });
    }
    if (path === "/device" && request.method === "PUT") {
      if (!DEVICE.test(body.id || "") || !/^[A-Za-z0-9+/=_-]{40,64}$/.test(body.pub || ""))
        return json({ error: "bad device" }, 400);
      const label = String(body.label || "").slice(0, 40);
      const had = this.one("SELECT pub FROM devices WHERE id = ?", body.id);
      this.sql.exec("INSERT OR REPLACE INTO devices (id, pub, label, seen) VALUES (?, ?, ?, ?)", body.id, body.pub, label, now);
      const extra = this.all("SELECT id FROM devices ORDER BY seen DESC LIMIT -1 OFFSET ?", MAX_DEVICES);
      for (const d of extra) this.sql.exec("DELETE FROM devices WHERE id = ?", d.id);
      if (!had || had.pub !== body.pub) {   // friends need the new key before they write again
        for (const f of this.all("SELECT uuid FROM friends")) this.tell(f.uuid, "/in/keys", {});
        this.push({ type: "keys", uuid: this.uuid });
      }
      return json({ ok: true });
    }
    const dev = path.match(/^\/device\/([A-Za-z0-9_-]+)$/);
    if (dev && request.method === "DELETE") {
      this.sql.exec("DELETE FROM devices WHERE id = ?", dev[1]);
      for (const f of this.all("SELECT uuid FROM friends")) this.tell(f.uuid, "/in/keys", {});
      return json({ ok: true });
    }
    if (path === "/request" && request.method === "POST") {
      const to = String(body.uuid || "").replace(/-/g, "").toLowerCase();
      const toName = NAME.test(body.name || "") ? body.name : "Player";
      if (!UUID.test(to)) return json({ error: "No Minecraft account has that name" }, 400);
      if (to === this.uuid) return json({ error: "That's you" }, 400);
      if (this.one("SELECT 1 AS x FROM friends WHERE uuid = ?", to)) return json({ error: `You're already friends with ${toName}` }, 409);
      if (this.one("SELECT COUNT(*) AS n FROM friends").n >= MAX_FRIENDS) return json({ error: "That's as many friends as fit" }, 409);
      if (this.one("SELECT 1 AS x FROM requests WHERE uuid = ? AND incoming = 1", to)) {
        return this.accept(to, toName);   // they already asked: that's a yes
      }
      const r = await this.tell(to, "/in/request", {});
      if (!r) return json({ error: "Couldn't send that. Try again." }, 502);
      this.sql.exec("INSERT OR REPLACE INTO requests (uuid, name, incoming, at) VALUES (?, ?, 0, ?)", to, toName, now);
      this.push({ type: "friends" });
      return json({ ok: true, sent: true });
    }
    if (path === "/accept" && request.method === "POST") {
      const r = this.one("SELECT name FROM requests WHERE uuid = ? AND incoming = 1", body.uuid || "");
      if (!r) return json({ error: "That request is gone" }, 404);
      return this.accept(body.uuid, r.name);
    }
    if ((path === "/decline" || path === "/cancel") && request.method === "POST") {
      const incoming = path === "/decline" ? 1 : 0;
      this.sql.exec("DELETE FROM requests WHERE uuid = ? AND incoming = ?", body.uuid || "", incoming);
      await this.tell(body.uuid, incoming ? "/in/declined" : "/in/cancelled", {});
      this.push({ type: "friends" });
      return json({ ok: true });
    }
    if (path === "/remove" && request.method === "POST") {
      this.sql.exec("DELETE FROM friends WHERE uuid = ?", body.uuid || "");
      this.sql.exec("DELETE FROM messages WHERE peer = ?", body.uuid || "");
      await this.tell(body.uuid, "/in/removed", {});
      this.push({ type: "friends" });
      return json({ ok: true });
    }
    const keys = path.match(/^\/keys\/([0-9a-f]{32})$/);
    if (keys) {
      if (!this.one("SELECT 1 AS x FROM friends WHERE uuid = ?", keys[1])) return json({ error: "Not your friend" }, 403);
      const r = await this.tell(keys[1], "/in/devices", {});
      return json({ devices: r ? r.devices : [] });
    }
    if (path === "/send" && request.method === "POST") {
      const to = String(body.to || "");
      if (!this.one("SELECT 1 AS x FROM friends WHERE uuid = ?", to)) return json({ error: "You can only message friends" }, 403);
      const box = body.box && typeof body.box === "object" ? body.box : {};
      const size = JSON.stringify(box).length;
      if (!size || size > MAX_MESSAGE) return json({ error: "That message is too big" }, 413);
      const cid = String(body.cid || "").slice(0, 40);
      const mineIds = new Set(this.all("SELECT id FROM devices").map((d) => d.id));
      const forMe = {}, forThem = {};
      for (const [k, v] of Object.entries(box)) {
        if (typeof v !== "string") continue;
        (mineIds.has(k) ? forMe : forThem)[k] = v;
      }
      const r = await this.tell(to, "/in/message", { cid, box: forThem, at: now });
      if (!r) return json({ error: "Couldn't reach them. Try again." }, 502);
      this.sql.exec("INSERT INTO messages (peer, mine, at, cid, box) VALUES (?, 1, ?, ?, ?)", to, now, cid, JSON.stringify(forMe));
      const id = this.one("SELECT last_insert_rowid() AS id").id;
      this.sql.exec("UPDATE friends SET read = ? WHERE uuid = ?", id, to);
      this.push({ type: "message", peer: to, id, mine: true });
      this.prune();
      return json({ ok: true, id, at: now, delivered: r.devices || 0 });
    }
    const msgs = path.match(/^\/messages\/([0-9a-f]{32})$/);
    if (msgs && request.method === "GET") {
      const after = Number(url.searchParams.get("after") || 0);
      const before = Number(url.searchParams.get("before") || 0) || Number.MAX_SAFE_INTEGER;
      const limit = Math.min(200, Math.max(1, Number(url.searchParams.get("limit") || 60)));
      const rows = this.all(`SELECT id, mine, at, cid, box FROM messages WHERE peer = ? AND id > ? AND id < ?
                             ORDER BY id DESC LIMIT ?`, msgs[1], after, before, limit);
      const out = [];
      for (const r of rows.reverse()) {
        let box = {};
        try { box = JSON.parse(r.box); } catch {}
        out.push({ id: r.id, mine: !!r.mine, at: r.at, cid: r.cid, ct: box[device] || null });
      }
      return json({ messages: out });
    }
    if (path === "/read" && request.method === "POST") {
      this.sql.exec("UPDATE friends SET read = MAX(read, ?) WHERE uuid = ?", Number(body.upTo || 0), body.uuid || "");
      return json({ ok: true });
    }
    return json({ error: "not found" }, 404);
  }

  accept(uuid, name) {
    return (async () => {
      const r = await this.tell(uuid, "/in/accepted", {});
      if (!r) return json({ error: "Couldn't reach them. Try again." }, 502);
      this.sql.exec("DELETE FROM requests WHERE uuid = ?", uuid);
      this.sql.exec("INSERT OR REPLACE INTO friends (uuid, name, since, online, seen) VALUES (?, ?, ?, ?, ?)",
        uuid, name, Date.now(), r.online ? 1 : 0, r.seen || 0);
      this.push({ type: "friends" });
      if (this.ctx.getWebSockets().length) this.tell(uuid, "/in/presence", { online: true });
      return json({ ok: true, friends: true });
    })();
  }

  /** Online or gone: every friend's Person hears about it. */
  async presence(online) {
    const now = Date.now();
    await Promise.all(this.all("SELECT uuid FROM friends").map((f) => this.tell(f.uuid, "/in/presence", { online, at: now })));
  }

  prune() {
    this.sql.exec("DELETE FROM messages WHERE at < ?", Date.now() - KEEP_DAYS * 86400000);
  }

  // ---- what other players' Persons tell this one (from = who's telling)

  async fromFriend(path, from, fromName, body) {
    // The uuid header here is the sender; this Person's own uuid is in its table.
    const me = this.one("SELECT v FROM me WHERE k = 'uuid'");
    const now = Date.now();
    const friend = this.one("SELECT uuid FROM friends WHERE uuid = ?", from);
    // A request can wait for someone who hasn't opened a launcher with friends yet: it's here
    // when they do. Everything else needs them signed in at least once.
    if (!me && path !== "/in/request") return json({ error: "not a Light Client friends user yet" }, 404);
    if (me) this.uuid = me.v;
    if (path === "/in/request") {
      if (friend) return json({ ok: true });
      if (this.one("SELECT COUNT(*) AS n FROM requests WHERE incoming = 1").n >= 100) return json({ ok: true });
      this.sql.exec("INSERT OR REPLACE INTO requests (uuid, name, incoming, at) VALUES (?, ?, 1, ?)", from, fromName, now);
      this.push({ type: "request", uuid: from, name: fromName });
      return json({ ok: true });
    }
    if (path === "/in/accepted") {
      const asked = this.one("SELECT 1 AS x FROM requests WHERE uuid = ? AND incoming = 0", from);
      if (!asked && !friend) return json({ error: "no request" }, 403);
      this.sql.exec("DELETE FROM requests WHERE uuid = ?", from);
      const online = this.ctx.getWebSockets().length > 0;
      this.sql.exec("INSERT OR REPLACE INTO friends (uuid, name, since, online, seen) VALUES (?, ?, ?, 1, ?)", from, fromName, now, now);
      this.push({ type: "friends", accepted: from, name: fromName });
      return json({ ok: true, online, seen: online ? now : 0 });
    }
    if (path === "/in/declined" || path === "/in/cancelled") {
      this.sql.exec("DELETE FROM requests WHERE uuid = ?", from);
      this.push({ type: "friends" });
      return json({ ok: true });
    }
    if (path === "/in/removed") {
      this.sql.exec("DELETE FROM friends WHERE uuid = ?", from);
      this.sql.exec("DELETE FROM messages WHERE peer = ?", from);
      this.push({ type: "friends" });
      return json({ ok: true });
    }
    if (!friend) return json({ error: "not friends" }, 403);
    if (path === "/in/devices") {
      return json({ devices: this.all("SELECT id, pub, label FROM devices ORDER BY seen DESC") });
    }
    if (path === "/in/keys") {
      this.push({ type: "keys", uuid: from });
      return json({ ok: true });
    }
    if (path === "/in/presence") {
      this.sql.exec("UPDATE friends SET online = ?, seen = ?, name = ? WHERE uuid = ?", body.online ? 1 : 0, now, fromName, from);
      this.push({ type: "presence", uuid: from, online: !!body.online });
      return json({ ok: true });
    }
    if (path === "/in/message") {
      const box = body.box && typeof body.box === "object" ? body.box : {};
      this.sql.exec("INSERT INTO messages (peer, mine, at, cid, box) VALUES (?, 0, ?, ?, ?)",
        from, Number(body.at) || now, String(body.cid || "").slice(0, 40), JSON.stringify(box));
      const id = this.one("SELECT last_insert_rowid() AS id").id;
      this.push({ type: "message", peer: from, id, mine: false, name: fromName });
      this.prune();
      return json({ ok: true, devices: Object.keys(box).length });
    }
    return json({ error: "not found" }, 404);
  }

  async webSocketMessage(ws, msg) {
    if (msg === "ping") ws.send("pong");
  }

  async webSocketClose(ws) {
    await this.gone(ws);
  }

  async webSocketError(ws) {
    await this.gone(ws);
  }

  async gone(ws) {
    const left = this.ctx.getWebSockets().filter((w) => w !== ws && w.readyState === 1);
    if (left.length === 0) {
      const me = this.one("SELECT v FROM me WHERE k = 'uuid'");
      if (me) {
        this.uuid = me.v;
        await this.presence(false);
      }
    }
  }
}

// ------------------------------------------------------------------ one call

export class CallRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  members() {
    const out = [];
    for (const ws of this.ctx.getWebSockets()) {
      const m = ws.deserializeAttachment();
      if (m && m.uuid) out.push(m);
    }
    return out;
  }

  send(msg, except) {
    for (const ws of this.ctx.getWebSockets()) {
      const m = ws.deserializeAttachment();
      if (ws === except || !m || !m.uuid) continue;
      try { ws.send(msg); } catch {}
    }
  }

  sendMembers() {
    this.send(JSON.stringify({ type: "members", members: this.members() }));
  }

  async fetch(request) {
    const uuid = request.headers.get("x-uuid");
    const name = request.headers.get("x-name") || "Player";
    const others = this.members().filter((m) => m.uuid !== uuid);
    if (others.length >= MAX_CALL) return json({ error: "This call is full" }, 409);
    for (const ws of this.ctx.getWebSockets()) {   // the same player joining again: drop the old one
      const m = ws.deserializeAttachment();
      if (m && m.uuid === uuid) { try { ws.close(4003, "joined again"); } catch {} }
    }
    const used = new Set(others.map((m) => m.id));
    let id = 1;
    while (used.has(id)) id++;
    const pair = new WebSocketPair();
    this.ctx.acceptWebSocket(pair[1]);
    pair[1].serializeAttachment({ id, uuid, name });
    pair[1].send(JSON.stringify({ type: "welcome", id }));
    this.sendMembers();
    return new Response(null, { status: 101, webSocket: pair[0] });
  }

  async webSocketMessage(ws, msg) {
    const me = ws.deserializeAttachment() || {};
    if (!me.uuid) return;
    if (typeof msg === "string") {
      if (msg === "ping") ws.send("pong");
      return;
    }
    // [kind][locked data] -> everyone else gets [kind][sender id][locked data]
    const data = new Uint8Array(msg);
    if (data.length < 2 || data.length > MAX_FRAME) return;
    const out = new Uint8Array(data.length + 1);
    out[0] = data[0];
    out[1] = me.id;
    out.set(data.subarray(1), 2);
    this.send(out.buffer, ws);
  }

  async webSocketClose(ws) {
    ws.serializeAttachment({});
    this.sendMembers();
  }

  async webSocketError(ws) {
    ws.serializeAttachment({});
    this.sendMembers();
  }
}
