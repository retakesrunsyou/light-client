"""Draws the Light Client capes.

Static capes are 2048x1024 (a 64x32 cape grid at 32 pixels a unit). Moving capes are 1024x512 at
about 30 frames a second, because every frame is its own texture. Run from the repo root:

    python3 tools/make_capes.py [name ...]

Needs numpy and Pillow. Writes into client/core/src/main/resources/assets/peregrine-client/textures/cape/.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "client", "core", "src", "main", "resources",
                   "assets", "peregrine-client", "textures", "cape")
PW, PH = 10, 16                      # outside panel size in cape units
MARK = [[(10, 7), (17, 7), (12, 21), (6, 25)], [(14, 21), (18, 18), (27, 18), (23, 25), (9, 25)]]


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=np.float32)


def ramp(t, stops):
    """Colour along stops [(pos, '#hex'), ...] for t in 0..1 (array)."""
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,), np.float32)
    ps = [p for p, _ in stops]
    cs = [hexc(c) for _, c in stops]
    for ch in range(3):
        out[..., ch] = np.interp(t, ps, [c[ch] for c in cs])
    return out


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def mark_mask(w, h, scale, size=0.62):
    """The logo, anti-aliased, as a w*h mask (the panel is w*h pixels)."""
    ss = 4
    im = Image.new("L", (w * ss, h * ss), 0)
    d = ImageDraw.Draw(im)
    bw = 21.0
    k = size * w * ss / bw
    ox = w * ss / 2 - (6 + bw / 2) * k
    oy = h * ss * 0.40 - (7 + 9) * k
    for poly in MARK:
        d.polygon([(ox + x * k, oy + y * k) for x, y in poly], fill=255)
    return np.asarray(im.resize((w, h), Image.LANCZOS), np.float32) / 255


def grid(w, h):
    v, u = np.mgrid[0:h, 0:w].astype(np.float32)
    return (u + 0.5) / w, (v + 0.5) / h


# ------------------------------------------------------------------ designs
# Each design is f(u, v, t, S) -> rgb (h, w, 3) for a panel; u, v run 0..1, t in 0..1 loops.

def static_design(top, bottom, accent, mark=("#ffffff", "#c9ccd4"), sheen=0.10):
    def f(u, v, t, S):
        base = ramp(v * 0.85 + (1 - u) * 0.15, [(0, top), (1, bottom)])
        # a soft diagonal sheen, then a vignette at the edges for depth
        s = np.exp(-(((u - 0.15) + v * 0.55 - 0.45) / 0.22) ** 2)[..., None] * sheen
        base = base + s * hexc(accent)
        edge = np.minimum.reduce([u, 1 - u, v, 1 - v])
        base = base * (0.86 + 0.14 * smooth(edge / 0.25))[..., None]
        m = mark_mask(u.shape[1], u.shape[0], S)
        a, b = hexc(mark[0]), hexc(mark[1])
        mk = (a[None, None] * (1 - v[..., None] * 0.9 + 0.0) + b[None, None] * (v[..., None] * 0.9)).clip(0, 1)
        return base * (1 - m[..., None]) + mk * m[..., None]
    return f


def aurora(u, v, t, S):
    bg = ramp(v, [(0, "#070b1a"), (1, "#0c1630")])
    ph = 2 * math.pi * t
    out = bg.copy()
    for i, (col, f0, amp, off) in enumerate([("#2de3a6", 1.0, 0.07, 0.30), ("#5aa8ff", 1.0, 0.09, 0.48),
                                              ("#b07bff", 2.0, 0.05, 0.64)]):
        c = off + amp * np.sin(2 * math.pi * f0 * u * 1.5 + ph * (1 + i % 2) + i * 1.7) + 0.04 * np.sin(5 * u + ph)
        band = np.exp(-((v - c) / (0.11 + 0.02 * i)) ** 2)
        band *= 0.55 + 0.45 * np.sin(2 * math.pi * 2 * u + ph + i) ** 2
        out = out + band[..., None] * hexc(col) * 0.85
    return np.clip(out, 0, 1)


def starry(u, v, t, S):
    out = ramp(v, [(0, "#060a1f"), (0.6, "#101c4a"), (1, "#1c2f6e")])
    rng = np.random.RandomState(7)
    h, w = u.shape
    for k in range(70):
        x, y = rng.rand() * w, rng.rand() * h
        ph, sz = rng.rand(), 0.6 + rng.rand() * 1.6
        a = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(2 * math.pi * (t + ph)))
        r = sz * w / 160
        yy, xx = np.ogrid[:h, :w]
        d2 = ((xx - x) ** 2 + (yy - y) ** 2) / (r * r)
        out = out + (np.exp(-d2 * 1.4) * a)[..., None] * hexc("#fff6d6")
    return np.clip(out, 0, 1)


def prism(u, v, t, S):
    hue = (u * 0.6 + v * 0.5 + t) % 1.0
    rgb = np.zeros(u.shape + (3,), np.float32)
    r = np.clip(np.abs(hue * 6 - 3) - 1, 0, 1)
    g = np.clip(2 - np.abs(hue * 6 - 2), 0, 1)
    b = np.clip(2 - np.abs(hue * 6 - 4), 0, 1)
    rgb[..., 0], rgb[..., 1], rgb[..., 2] = r, g, b
    dark = ramp(v, [(0, "#23262e"), (1, "#14161b")])
    mix = (0.16 + 0.12 * np.sin(2 * math.pi * (u * 2 - v + t)) ** 2)[..., None]
    return np.clip(dark * (1 - mix) + rgb * mix + 0.0, 0, 1)


def lightspeed(u, v, t, S):
    out = ramp(v, [(0, "#05060c"), (1, "#0d1226")])
    rng = np.random.RandomState(3)
    h, w = u.shape
    for k in range(46):
        x = rng.rand()
        speed = int(1 + rng.randint(0, 3))
        ln = 0.10 + rng.rand() * 0.25
        y0 = rng.rand()
        wid = (0.006 + rng.rand() * 0.012)
        y = (y0 + t * speed) % 1.0
        dy = (y - v) % 1.0
        trail = np.where(dy < ln, (1 - dy / ln) ** 1.6, 0.0)
        core = np.exp(-((u - x) / wid) ** 2)
        out = out + (trail * core)[..., None] * hexc("#cfe3ff") * 0.9
    return np.clip(out, 0, 1)


def fragments(u, v, t, S):
    out = ramp(v, [(0, "#16181d"), (1, "#0d0e12")])
    rng = np.random.RandomState(11)
    h, w = u.shape
    asp = PH / PW
    for k in range(26):
        x = rng.rand()
        y0 = rng.rand()
        sp = 1 + rng.randint(0, 2)
        sz = 0.025 + rng.rand() * 0.05
        y = (y0 - t * sp) % 1.0
        rot = rng.rand() * math.pi
        for dy in (-1, 0, 1):
            du = (u - x)
            dv = (v - (y + dy)) * asp
            c, s = math.cos(rot + t * 2 * math.pi), math.sin(rot + t * 2 * math.pi)
            a, b = du * c - dv * s, du * s + dv * c
            d = np.abs(a) / sz + np.abs(b) / (sz * 1.8)
            shard = smooth((1 - d) * 4)
            glow = np.exp(-d * 1.6) * 0.35
            tw = 0.7 + 0.3 * math.sin(2 * math.pi * (t * 1 + k * 0.13))
            col = hexc("#ffffff") * 0.9 + hexc("#bcd6ff") * 0.1
            out = out + (shard * tw)[..., None] * col + (glow * tw)[..., None] * hexc("#9fb8ff")
    m = mark_mask(w, h, S, 0.5) * 0.10
    out = out + m[..., None]
    return np.clip(out, 0, 1)


STATIC = {
    "light":    static_design("#f4f5f8", "#9aa0ad", "#ffffff", ("#1a1c22", "#3a3e49"), 0.35),
    "violet":   static_design("#6e47d8", "#1d1245", "#c8b3ff"),
    "ember":    static_design("#ff8a3d", "#5a0f0a", "#ffd9a8"),
    "pumpkin":  static_design("#f59a23", "#7a2e07", "#fff0b8", ("#2b1708", "#4a2410")),
    "frost":    static_design("#cfeeff", "#3a7fb8", "#ffffff", ("#ffffff", "#e8f6ff")),
    "dawn":     static_design("#ffc8a8", "#b0508c", "#fff3df"),
    "mint":     static_design("#c2f5dd", "#1f8a72", "#ffffff", ("#0f3a30", "#1c6b58")),
    "rose":     static_design("#ffb6cf", "#9a2a5b", "#ffe9f1"),
    "midnight": static_design("#2b3350", "#07090f", "#8aa0ff", ("#e8ecff", "#8f9ccc")),
    "sunbeam":  static_design("#fff1a8", "#e88a14", "#ffffff", ("#5c3500", "#8a5206"), 0.4),
    "obsidian": static_design("#3a3b42", "#08080a", "#c0c4d4", ("#f2f3f7", "#9a9eac"), 0.18),
}
# name -> (design, frames, ms a frame)
MOVING = {
    "starry": (starry, 32, 33), "prism": (prism, 32, 33), "lightspeed": (lightspeed, 32, 33),
    "aurora": (aurora, 32, 33), "fragments": (fragments, 40, 33),
}


# ------------------------------------------------------------------ compose
def paint(design, S, t):
    """The whole cape image (64S x 32S) with every face drawn from the design."""
    W, H = 64 * S, 32 * S
    img = np.zeros((H, W, 4), np.float32)

    def panel(x, y, w, h, flip=False, dim=1.0, wing=False):
        pw, ph = w * S, h * S
        u, v = grid(pw, ph)
        if wing:
            v = v * (16 / 20) + 0.0
        rgb = design(u, v, t, S)
        # clean edge: a thin light rim and a darker fold just inside it
        e = np.minimum.reduce([u * w, (1 - u) * w, v * h, (1 - v) * h])
        rim = np.exp(-(e / 0.10) ** 2)[..., None] * 0.22
        fold = np.exp(-(((e - 0.28) / 0.16) ** 2))[..., None] * -0.07
        rgb = np.clip(rgb * (1 + fold) + rim * (1 - rgb * 0.6), 0, 1) * dim
        if flip:
            rgb = rgb[:, ::-1]
        img[y * S:y * S + ph, x * S:x * S + pw, :3] = rgb
        img[y * S:y * S + ph, x * S:x * S + pw, 3] = 1

    panel(1, 1, 10, 16)                # outside (back of the cape)
    panel(12, 1, 10, 16, True, 0.72)   # inside
    panel(24, 2, 10, 20, wing=True)    # elytra wings
    panel(36, 2, 10, 20, True, 1.0, wing=True)
    # top, bottom and the thin sides: the design's edge colour, a touch lighter
    u, v = grid(10 * S, S)
    top = design(u, v * 0 + 0.02, t, S)
    bot = design(u, v * 0 + 0.98, t, S)
    img[0:S, 1 * S:11 * S, :3] = np.clip(top * 1.1, 0, 1); img[0:S, 1 * S:11 * S, 3] = 1
    img[0:S, 11 * S:21 * S, :3] = np.clip(bot * 0.8, 0, 1); img[0:S, 11 * S:21 * S, 3] = 1
    for x in (0, 11):
        u, v = grid(S, 16 * S)
        side = design(np.full_like(u, 0.5 if x else 0.0), v, t, S)
        img[S:17 * S, x * S:(x + 1) * S, :3] = np.clip(side * 0.85, 0, 1)
        img[S:17 * S, x * S:(x + 1) * S, 3] = 1
    for x in (22,):
        u, v = grid(S, 16 * S)
        side = design(np.full_like(u, 1.0), v, t, S)
        img[S:17 * S, x * S:(x + 1) * S, :3] = np.clip(side * 0.85, 0, 1)
        img[S:17 * S, x * S:(x + 1) * S, 3] = 1
    return Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")


def save(im, path):
    im.save(path, optimize=True)


def main(names):
    os.makedirs(OUT, exist_ok=True)
    for name, d in STATIC.items():
        if names and name not in names:
            continue
        save(paint(d, 32, 0), os.path.join(OUT, name + ".png"))
        print("made", name)
    for name, (d, n, ms) in MOVING.items():
        if names and name not in names:
            continue
        for i in range(n):
            save(paint(d, 16, i / n), os.path.join(OUT, "%s_%d.png" % (name, i)))
        print("made", name, n, "frames at", ms, "ms")


if __name__ == "__main__":
    main(sys.argv[1:])
