"""Custom controls: a sliding toggle switch and a segmented picker."""

from PySide6.QtCore import (Property, QEasingCurve, QPropertyAnimation, QRectF, QSize, Qt,
                            QTimer, QVariantAnimation, Signal)
from PySide6.QtGui import QColor, QPainter, QPen
from PySide6.QtWidgets import QAbstractButton, QButtonGroup, QHBoxLayout, QLabel, QPushButton, QVBoxLayout, QWidget

from .theme import Theme


class Toggle(QAbstractButton):
    """A small round switch: grey when off, accent when on, with the knob turning dark ink."""

    W, H, PAD = 36, 20, 3

    def __init__(self, checked: bool = False, parent=None):
        super().__init__(parent)
        self.setCheckable(True)
        self.setChecked(checked)
        self.setCursor(Qt.PointingHandCursor)
        self.setFocusPolicy(Qt.StrongFocus)
        self._pos = 1.0 if checked else 0.0
        self._anim = QPropertyAnimation(self, b"knob", self)
        self._anim.setDuration(140)
        self._anim.setEasingCurve(QEasingCurve.OutCubic)
        self.toggled.connect(self._slide)

    def sizeHint(self):
        return QSize(self.W, self.H)

    def _get(self):
        return self._pos

    def _set(self, v):
        self._pos = v
        self.update()

    knob = Property(float, _get, _set)

    def _slide(self, on):
        self._anim.stop()
        self._anim.setStartValue(self._pos)
        self._anim.setEndValue(1.0 if on else 0.0)
        self._anim.start()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        p.setPen(Qt.NoPen)
        off = QColor("#30303c") if Theme.name != "day" else QColor("#c9ccd6")
        on = QColor(Theme.accent)
        t = self._pos
        track = QColor(int(off.red() + (on.red() - off.red()) * t),
                       int(off.green() + (on.green() - off.green()) * t),
                       int(off.blue() + (on.blue() - off.blue()) * t))
        if not self.isEnabled():
            track.setAlpha(110)
        p.setBrush(track)
        rad = self.H / 2
        p.drawRoundedRect(QRectF(0, 0, self.W, self.H), rad, rad)
        k = self.H - 2 * self.PAD
        x = self.PAD + (self.W - 2 * self.PAD - k) * t
        knob_off, knob_on = QColor("#d6d6e0"), QColor("#0b0b0f")
        p.setBrush(QColor(int(knob_off.red() + (knob_on.red() - knob_off.red()) * t),
                          int(knob_off.green() + (knob_on.green() - knob_off.green()) * t),
                          int(knob_off.blue() + (knob_on.blue() - knob_off.blue()) * t)))
        p.drawEllipse(QRectF(x, self.PAD, k, k))
        if self.hasFocus():
            p.setBrush(Qt.NoBrush)
            p.setPen(QPen(QColor(Theme.accent), 1))
            p.drawRoundedRect(QRectF(0.5, 0.5, self.W - 1, self.H - 1), rad, rad)
        p.end()

    def hitButton(self, pos):
        return self.rect().contains(pos)


class Segmented(QWidget):
    """A row of joined buttons where exactly one is selected."""
    changed = Signal(str)

    def __init__(self, options: list, current: str = None, parent=None):
        super().__init__(parent)
        self.setObjectName("segmented")
        self.setAttribute(Qt.WA_StyledBackground, True)   # its own backing, so the sky doesn't show through
        h = QHBoxLayout(self)
        h.setContentsMargins(3, 3, 3, 3)
        h.setSpacing(2)
        self.group = QButtonGroup(self)
        self.buttons = {}
        for key, label in options:
            b = QPushButton(label, objectName="segment", checkable=True)
            b.setCursor(Qt.PointingHandCursor)
            b.clicked.connect(lambda _=False, k=key: self.changed.emit(k))
            self.group.addButton(b)
            self.buttons[key] = b
            h.addWidget(b)
        self._pill = QRectF()
        self._pill_anim = QVariantAnimation(self, duration=180, easingCurve=QEasingCurve.OutCubic)
        self._pill_anim.valueChanged.connect(self._pill_step)
        self.group.buttonToggled.connect(lambda _b, on: on and self._move_pill(True))
        self.set(current or options[0][0])

    def _target(self) -> QRectF:
        b = next((b for b in self.buttons.values() if b.isChecked()), None)
        return QRectF(b.geometry()) if b is not None else QRectF()

    def _pill_step(self, v):
        self._pill = v
        self.update()

    def _move_pill(self, animate):
        t = self._target()
        self._pill_anim.stop()
        if animate and self.isVisible() and not self._pill.isNull() and not t.isNull():
            self._pill_anim.setStartValue(self._pill)
            self._pill_anim.setEndValue(t)
            self._pill_anim.start()
        else:
            self._pill = t
            self.update()

    def resizeEvent(self, e):
        super().resizeEvent(e)
        self._move_pill(False)

    def showEvent(self, e):
        super().showEvent(e)
        QTimer.singleShot(0, lambda: self._move_pill(False))

    def paintEvent(self, e):
        super().paintEvent(e)       # the styled background, then the pill under the labels
        if self._pill.isNull():
            return
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        p.setPen(Qt.NoPen)
        p.setBrush(QColor("#2c2c36") if Theme.name != "day" else QColor("#ffffff"))
        p.drawRoundedRect(self._pill, 4, 4)
        p.end()

    def set(self, key):
        if key in self.buttons:
            self.buttons[key].setChecked(True)

    def value(self) -> str:
        return next((k for k, b in self.buttons.items() if b.isChecked()), "")

    def set_enabled(self, key, enabled):
        self.buttons[key].setEnabled(enabled)


class ChoiceCards(QWidget):
    """Big pickable cards (title and a line under it), one chosen: the same calls as Segmented."""
    changed = Signal(str)

    def __init__(self, options: list, current: str = None, parent=None):
        """options: (key, title, line under it)."""
        super().__init__(parent)
        h = QHBoxLayout(self)
        h.setContentsMargins(0, 0, 0, 0)
        h.setSpacing(10)
        self.group = QButtonGroup(self)
        self.buttons = {}
        for key, title, sub in options:
            b = QPushButton(objectName="choiceCard", checkable=True)
            b.setCursor(Qt.PointingHandCursor)
            b.setMinimumHeight(62)
            inner = QVBoxLayout(b)
            inner.setContentsMargins(14, 10, 14, 10)
            inner.setSpacing(2)
            t1 = QLabel(title, objectName="choiceTitle")
            t2 = QLabel(sub, objectName="choiceSub")
            t2.setWordWrap(True)
            for lab in (t1, t2):
                lab.setAttribute(Qt.WA_TransparentForMouseEvents, True)
                inner.addWidget(lab)
            b.clicked.connect(lambda _=False, k=key: self.changed.emit(k))
            self.group.addButton(b)
            self.buttons[key] = b
            h.addWidget(b, 1)
        self.set(current or options[0][0])

    def set(self, key):
        if key in self.buttons:
            self.buttons[key].setChecked(True)

    def value(self) -> str:
        return next((k for k, b in self.buttons.items() if b.isChecked()), "")

    def set_enabled(self, key, enabled):
        self.buttons[key].setEnabled(enabled)
