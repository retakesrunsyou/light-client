"""The launcher's background: a still, quiet backdrop behind the whole window.

A soft vertical gradient in the theme's colours, a faint glow of the accent low in one
corner, and fine grain that hides the banding smooth gradients get on 8-bit screens.
Drawn once per size or theme and reused, so it costs nothing while the launcher is open.
"""

import random

from PySide6.QtCore import QPointF, Qt
from PySide6.QtGui import QColor, QImage, QLinearGradient, QPainter, QPixmap, QRadialGradient
from PySide6.QtWidgets import QWidget

from .theme import Theme


def season(today=None) -> str:
    """"halloween" through October, "winter" from December to early January, else ""."""
    import datetime
    d = today or datetime.date.today()
    if d.month == 10:
        return "halloween"
    if d.month == 12 or (d.month == 1 and d.day <= 6):
        return "winter"
    return ""


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
        key = (self.width(), self.height(), dpr, Theme.name, Theme.accent)
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
        p.drawTiledPixmap(0, 0, w, h, self._grain())
        p.end()
        self._pm, self._key = pm, key
        return pm

    def paintEvent(self, _):
        p = QPainter(self)
        p.drawPixmap(0, 0, self._backdrop())
        p.end()
