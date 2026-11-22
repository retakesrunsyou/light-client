"""The launcher's background: a still, quiet backdrop behind the whole window.

A soft vertical gradient in the theme's colours, a faint glow of the accent low in one
corner, and fine grain that hides the banding smooth gradients get on 8-bit screens. Around
the holidays (Christmas, New Year, Valentine's, Easter, Halloween, Thanksgiving) it takes on
that holiday's colours and pixel-art sprites, the same ones as the game's main menu.
Drawn once per size or theme and reused, so it costs nothing while the launcher is open.
"""

import random

from PySide6.QtCore import QPointF, QRectF, Qt
from PySide6.QtGui import QColor, QImage, QLinearGradient, QPainter, QPixmap, QRadialGradient
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
    "new_year", "valentines", "easter", "halloween", "thanksgiving", or ""."""
    import datetime
    d = today or datetime.date.today()
    m, day = d.month, d.day
    if (m == 12 and day >= 27) or (m == 1 and day <= 2):
        return "new_year"
    if m == 12:
        return "christmas"
    if m == 2 and 7 <= day <= 15:
        return "valentines"
    e = _easter(d.year)
    if e - datetime.timedelta(days=6) <= d <= e + datetime.timedelta(days=1):
        return "easter"
    if (m == 10 and day >= 15) or (m == 11 and day == 1):
        return "halloween"
    first = datetime.date(d.year, 11, 1)
    thanks = first + datetime.timedelta(days=(3 - first.weekday()) % 7 + 21)   # the 4th Thursday
    if m == 11 and day >= 10 and d <= thanks + datetime.timedelta(days=1):
        return "thanksgiving"
    return ""


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
    "christmas": ("snow", ["#f4f7ff"], "#e0474c"),
    "new_year": ("spark", ["#f2c14e", "#b98cff", "#7fd7ff"], "#f2c14e"),
    "valentines": ("heart", ["#ff5c8a", "#ffb3c9", "#e0304e"], "#ff5c8a"),
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

    @staticmethod
    def _grain() -> QPixmap:
        rng = random.Random(3)
        img = QImage(96, 96, QImage.Format_ARGB32_Premultiplied)
        img.fill(Qt.transparent)
        for y in range(96):
            for x in range(96):
                v = rng.randint(0, 255)
                img.setPixelColor(x, y, QColor(v, v, v, rng.randint(0, 5)))
        return QPixmap.fromImage(img)

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
        g = QLinearGradient(0, 0, 0, h)
        if dark:
            g.setColorAt(0, bg.darker(135))
            g.setColorAt(1, bg)
        else:
            g.setColorAt(0, bg.lighter(104))
            g.setColorAt(1, bg.darker(103))
        p.fillRect(0, 0, w, h, g)
        glow = QColor(Theme.accent)
        glow.setAlpha(26 if dark else 18)
        r = QRadialGradient(QPointF(w * 0.9, h * 1.05), max(w, h) * 0.7)
        r.setColorAt(0, glow)
        glow.setAlpha(0)
        r.setColorAt(1, glow)
        p.fillRect(0, 0, w, h, r)
        if self.season in THEMES:
            self._holiday(p, w, h, dark)
        p.drawTiledPixmap(0, 0, w, h, self._grain())
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
        rng = random.Random(sum(map(ord, self.season)))
        rows = SPRITES[kind]
        count = max(10, w * h // 26000)
        for i in range(count):
            px = rng.choice((2, 2, 3, 3, 4))
            x, y = rng.uniform(0, w - 30), rng.uniform(0, h - 30)
            alpha = rng.uniform(0.07, 0.18) if dark else rng.uniform(0.06, 0.13)
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
        if self.season == "christmas":   # fairy lights along the top
            bulbs = ["#e0474c", "#f2c14e", "#3daa5c", "#5aa8ff"]
            p.fillRect(QRectF(0, 7, w, 1), QColor(255, 255, 255, 22))
            for i, x in enumerate(range(18, w, 46)):
                c = QColor(bulbs[i % 4])
                halo = QRadialGradient(QPointF(x + 2, 14), 12)
                c.setAlpha(70)
                halo.setColorAt(0, c)
                c2 = QColor(c)
                c2.setAlpha(0)
                halo.setColorAt(1, c2)
                p.fillRect(QRectF(x - 10, 2, 24, 24), halo)
                c.setAlpha(220)
                p.fillRect(QRectF(x, 8, 4, 8), c)
        if self.season == "halloween":
            for x in (14, w - 44):
                _sprite(p, SPRITES["pumpkin"], x, h - 36, 3.4, [QColor(255, 138, 31, 150), QColor(79, 138, 58, 150), QColor(42, 34, 51, 180)])

    def paintEvent(self, _):
        p = QPainter(self)
        p.drawPixmap(0, 0, self._backdrop())
        p.end()
