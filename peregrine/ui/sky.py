"""The launcher's background: a still, quiet backdrop behind the whole window.

A soft vertical gradient in the theme's colours, a faint glow of the accent low in one
corner, and fine grain that hides the banding smooth gradients get on 8-bit screens. Around
the holidays (Christmas, New Year, Valentine's, Easter, Halloween, Thanksgiving) it takes on
that holiday's colours and pixel-art sprites, the same ones as the game's main menu.
Drawn once per size or theme and reused, so it costs nothing while the launcher is open.
"""

import math
import random

from PySide6.QtCore import QPointF, QRectF, Qt
from PySide6.QtGui import QColor, QImage, QPainter, QPixmap, QRadialGradient
from PySide6.QtWidgets import QWidget

from .theme import Theme


def _easter(y: int):
    import datetime
    a, b, c = y % 19, y // 100, y % 100
    d, e, f = b // 4, b % 4, (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = c // 4, c % 4
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    n = h + l - 7 * m + 114
    return datetime.date(y, n // 31, n % 31 + 1)


def season(today=None) -> str:
    """The holiday theme for a date (the same dates as the game's main menu): "christmas",
    "new_year", "valentines", "easter", "halloween", "thanksgiving", or "". Each starts on the
    1st of its holiday's month: Christmas December 1st-30th, New Year December 31st-January 7th,
    Valentine's February 1st-14th, Easter from the 1st of its month to Easter Monday, Halloween
    all October, Thanksgiving November 1st to Thanksgiving Day."""
    import datetime
    d = today or datetime.date.today()
    m, day = d.month, d.day
    if (m == 12 and day == 31) or (m == 1 and day <= 7):
        return "new_year"
    if m == 12:
        return "christmas"
    if m == 2 and day <= 14:
        return "valentines"
    e = _easter(d.year)
    if m == e.month and d <= e + datetime.timedelta(days=1):
        return "easter"
    if m == 10:
        return "halloween"
    first = datetime.date(d.year, 11, 1)
    thanks = first + datetime.timedelta(days=(3 - first.weekday()) % 7 + 21)   # the 4th Thursday
    if m == 11 and d <= thanks:
        return "thanksgiving"
    return ""


# The launcher's accent colour during each holiday (the game's menus use the same).
HOLIDAY_ACCENTS = {
    "christmas": "#d8343c",
    "new_year": "#e8b84a",
    "valentines": "#e0304e",
    "easter": "#ee8ab6",
    "halloween": "#ff7a1a",
    "thanksgiving": "#d9752b",
}


def holiday_accent(cfg) -> str:
    """The accent to show: the holiday's while holiday themes are on, otherwise yours."""
    if cfg.get("seasonal", False):
        return HOLIDAY_ACCENTS.get(season(), cfg["accent"])
    return cfg["accent"]


def _pictures() -> dict:
    """The real heart, leaf and spark pictures (shared with the game's holiday themes)."""
    import sys
    from pathlib import Path
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent.parent)) / "assets" / "holiday"
    out = {}
    for f in base.glob("*.png") if base.is_dir() else []:
        pm = QPixmap(str(f))
        if not pm.isNull():
            out[f.stem] = pm
    return out


# Pixel sprites shared with the game's holiday themes: '.' is empty, letters are colours.
SPRITES = {
    "snow": ["...a...", ".a.a.a.", "..aaa..", "aaaaaaa", "..aaa..", ".a.a.a.", "...a..."],
    "heart": [".aa.aa.", "abaaaaa", "aaaaaaa", ".aaaaa.", "..aaa..", "...a..."],
    "egg": [".aaa.", "aaaaa", "bbbbb", "acaca", "bbbbb", "aaaaa", ".aaa."],
    "bat": ["a...a...a", "aa.aaa.aa", "aaaaaaaaa", "...a.a..."],
    "pumpkin": ["....b....", "..aaaaa..", ".aaaaaaa.", "aacaaacaa", "aaaaaaaaa", "aacacacaa", ".aaaaaaa.", "..aaaaa.."],
    "leaf": ["...a...", ".a.a.a.", "aaaaaaa", ".aaaaa.", "aaaaaaa", "...b...", "...b..."],
    "spark": ["..a..", ".....", "a.a.a", ".....", "..a.."],
}
# What each theme scatters, and its colours (the first is the corner glow).
THEMES = {
    "christmas": ("snow", ["#f4f7ff"], "#d8343c"),
    "new_year": ("spark", ["#f2c14e", "#b98cff", "#7fd7ff"], "#e8b84a"),
    "valentines": ("heart", ["#e0304e", "#ff6f96", "#c4122f"], "#e0304e"),
    "easter": ("egg", ["#f7b5d2", "#a9d8ff", "#fff1a8", "#d9c2ff"], "#8ed081"),
    "halloween": ("bat", ["#17121d"], "#ff8a1f"),
    "thanksgiving": ("leaf", ["#e07b2e", "#b5432a", "#e8b04a"], "#e07b2e"),
}


def _sprite(p: QPainter, rows, x: float, y: float, px: float, colors):
    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            i = ord(ch) - ord("a")
            if ch != "." and 0 <= i < len(colors):
                p.fillRect(QRectF(x + c * px, y + r * px, px, px), colors[i])


class Sky(QWidget):
    """The window's backdrop. It never moves: set_enabled and set_paused are kept so the
    window can call them, but there is nothing to start or stop."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setAttribute(Qt.WA_OpaquePaintEvent)  # every pixel is painted
        self.season = ""
        self._pm = None
        self._key = None

    def set_enabled(self, _on: bool):
        self.update()

    def set_paused(self, _paused: bool):
        pass

    def _backdrop(self) -> QPixmap:
        dpr = self.devicePixelRatioF()
        key = (self.width(), self.height(), dpr, Theme.name, Theme.accent, self.season)
        if key == self._key and self._pm is not None:
            return self._pm
        w, h = max(1, self.width()), max(1, self.height())
        pm = QPixmap(int(w * dpr), int(h * dpr))
        pm.setDevicePixelRatio(dpr)
        p = QPainter(pm)
        dark = Theme.name != "light"
        bg = QColor(Theme.p["bg"])
        p.fillRect(0, 0, w, h, bg)
        if self.season in THEMES:
            p.setOpacity(0.55)
            self._holiday(p, w, h, dark)
        p.end()
        self._pm, self._key = pm, key
        return pm

    def _holiday(self, p: QPainter, w: int, h: int, dark: bool):
        """The holiday theme, still: a warm glow in its colour and its sprites scattered faintly."""
        kind, colors, glow_hex = THEMES[self.season]
        glow = QColor(glow_hex)
        glow.setAlpha(34 if dark else 24)
        r = QRadialGradient(QPointF(w * 0.08, h * 1.02), max(w, h) * 0.6)
        r.setColorAt(0, glow)
        glow.setAlpha(0)
        r.setColorAt(1, glow)
        p.fillRect(0, 0, w, h, r)
        if self.season == "christmas":   # red in one corner, pine green in the other
            green = QColor("#2e9e57")
            green.setAlpha(30 if dark else 20)
            r2 = QRadialGradient(QPointF(w * 0.95, -h * 0.05), max(w, h) * 0.55)
            r2.setColorAt(0, green)
            green.setAlpha(0)
            r2.setColorAt(1, green)
            p.fillRect(0, 0, w, h, r2)
        rng = random.Random(sum(map(ord, self.season)))
        rows = SPRITES[kind]
        pics = _pictures()
        count = max(10, w * h // 26000)
        p.setRenderHint(QPainter.SmoothPixmapTransform)
        if kind == "spark" and "spark" in pics:   # New Year: a few still fireworks, rings of glowing sparks
            for _ in range(max(3, w * h // 260000)):
                cx, cy, rad = rng.uniform(60, w - 60), rng.uniform(40, h * 0.6), rng.uniform(28, 60)
                col = QColor(rng.choice(colors))
                n = rng.randint(18, 26)
                for i in range(n):
                    a = math.tau * i / n
                    for k, (f, size, op) in enumerate(((1.0, 7, 0.32), (0.72, 5, 0.18), (0.45, 4, 0.1))):
                        x, y = cx + math.cos(a) * rad * f, cy + math.sin(a) * rad * f + (rad * 0.12 * f * f)
                        self._tinted(p, pics["spark"], col, x - size / 2, y - size / 2, size, 0, op * (1.0 if dark else 0.8))
            return
        names = {"heart": ["heart_red", "heart_red", "heart_deep", "heart_pink"],
                 "leaf": ["maple_red", "maple_orange", "oak_gold", "oak_brown"]}.get(kind)
        for i in range(count):
            x, y = rng.uniform(0, w - 30), rng.uniform(0, h - 30)
            alpha = rng.uniform(0.10, 0.26) if dark else rng.uniform(0.08, 0.18)
            if names and all(n in pics for n in names):   # a real heart or leaf, turned a little
                pm = pics[rng.choice(names)]
                size = rng.uniform(16, 38)
                p.save()
                p.setOpacity(alpha * 1.4)
                p.translate(x + size / 2, y + size / 2)
                p.rotate(rng.uniform(-25, 25) if kind == "heart" else rng.uniform(0, 360))
                p.drawPixmap(QRectF(-size / 2, -size / 2, size, size), pm, QRectF(pm.rect()))
                p.restore()
                continue
            px = rng.choice((2, 2, 3, 3, 4))
            cols = []
            for c in (colors if kind != "egg" else rng.sample(colors, 3)):
                q = QColor(c)
                if kind == "bat" and dark:
                    q = QColor("#a07ac8")   # bats show as purple shapes on the dark theme
                q.setAlphaF(alpha)
                cols.append(q)
            if kind in ("leaf", "heart", "spark"):
                cols = [cols[rng.randrange(len(cols))]] + [QColor(0, 0, 0, int(alpha * 255 * 0.6))]
                if kind == "heart":
                    cols[1] = QColor(255, 255, 255, int(alpha * 255))
            _sprite(p, rows, x, y, px, cols)
        if self.season == "halloween":
            for x in (14, w - 44):
                _sprite(p, SPRITES["pumpkin"], x, h - 36, 3.4, [QColor(255, 138, 31, 150), QColor(79, 138, 58, 150), QColor(42, 34, 51, 180)])

    @staticmethod
    def _tinted(p: QPainter, pm: QPixmap, color: QColor, x: float, y: float, size: float, rot: float, opacity: float):
        """A white picture (the spark) drawn in a colour."""
        img = QImage(pm.size(), QImage.Format_ARGB32_Premultiplied)
        img.fill(Qt.transparent)
        q = QPainter(img)
        q.drawPixmap(0, 0, pm)
        q.setCompositionMode(QPainter.CompositionMode_SourceIn)
        q.fillRect(img.rect(), color)
        q.end()
        p.save()
        p.setOpacity(opacity)
        p.drawImage(QRectF(x, y, size, size), img)
        p.restore()

    def paintEvent(self, _):
        p = QPainter(self)
        p.drawPixmap(0, 0, self._backdrop())
        p.end()
