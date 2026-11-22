"""What's new: the latest changes, shown once after the launcher updates (and from Settings → Updates)."""
from PySide6.QtWidgets import QFrame, QHBoxLayout, QLabel, QScrollArea, QVBoxLayout, QWidget

from .. import VERSION, config
from .sheet import Sheet
from .theme import Theme

# Newest first: (version, title, what it does). Keep in step with the game's What's new page.
NOTES = [
    ("2.2.0", "Light Client", "A new name, a new logo, and a new main menu in game."),
    ("2.2.0", "Light Engine", "One switch in the Right Shift menu's Performance page sets the best FPS settings for "
                              "Potato, Balanced, Max FPS or Quality. Switching it off puts your own settings back."),
    ("2.2.0", "Saturation", "Make the whole world more vivid, or greyer."),
    ("2.2.0", "Lyrics", "Words to the song you're playing, in time with it, on screen or floating in 3D in the world."),
    ("2.2.0", "Holiday themes", "Christmas, New Year, Valentine's, Easter, Halloween and Thanksgiving recolour the "
                                "launcher and the game's menus, with snow, fireworks, falling hearts or real autumn leaves. "
                                "Each starts on the 1st of its month; pick one or turn them off."),
    ("2.2.0", "Music in the pause menu", "The song playing with its cover art, and back, play / pause and skip buttons."),
    ("2.2.0", "New skies", "Real-looking stars, a real sun and a detailed moon, and much lighter on FPS."),
    ("2.2.0", "Server icons", "The pause menu shows the server's own icon."),
    ("2.2.0", "A cleaner launcher", "Blocky buttons like Minecraft's, a calm still background, a simpler "
                                    "New instance screen, and pages that fit any window size."),
    ("2.2.0", "Faster game start", "The FPS pack now includes ModernFix, so the game opens and reloads much faster."),
    ("2.2.0", "Santa hat and bunny slippers", "Add them to any skin from the Locker."),
    ("2.2.0", "Screenshots and clips", "Saved in your Pictures and Videos folders, in a Light Client folder, "
                                       "on Windows and Linux."),
    ("2.1.3", "Pie chart", "Minecraft's own profiler pie chart as a HUD item you can move and size."),
    ("2.1.3", "Sliders", "Dragging a slider follows your mouse again."),
    ("2.1.2", "Sharper shared capes", "Other players see your cape twice as sharp, with every frame."),
]


def notes_widget() -> QWidget:
    """The notes as cards, newest first, in a see-through scroll area."""
    body = QWidget()
    body.setObjectName("newsBody")
    body.setStyleSheet("QWidget#newsBody { background: transparent; }")
    col = QVBoxLayout(body)
    col.setContentsMargins(0, 0, 6, 0)
    col.setSpacing(8)
    last = None
    for version, title, text in NOTES:
        if version != last:
            last = version
            col.addWidget(QLabel(f"Version {version}", objectName="faint"))
        card = QFrame(objectName="card")
        h = QHBoxLayout(card)
        h.setContentsMargins(12, 10, 12, 10)
        h.setSpacing(10)
        dot = QLabel("\u25CF")
        dot.setStyleSheet(f"color: {Theme.accent}; background: transparent;")
        h.addWidget(dot, 0)
        words = QVBoxLayout()
        words.setSpacing(2)
        t1 = QLabel(title)
        t1.setStyleSheet("font-weight: 600; background: transparent;")
        d = QLabel(text, objectName="muted")
        d.setWordWrap(True)
        words.addWidget(t1)
        words.addWidget(d)
        h.addLayout(words, 1)
        col.addWidget(card)
    col.addStretch()
    scroll = QScrollArea()
    scroll.setWidgetResizable(True)
    scroll.setFrameShape(QFrame.NoFrame)
    scroll.setStyleSheet("QScrollArea { background: transparent; } QScrollArea > QWidget > QWidget { background: transparent; }")
    scroll.viewport().setAutoFillBackground(False)
    scroll.setWidget(body)
    return scroll


class WhatsNewDialog(Sheet):
    def __init__(self, parent):
        super().__init__(parent)
        self.setWindowTitle("What's new")
        v = QVBoxLayout(self)
        v.setContentsMargins(24, 22, 24, 20)
        v.setSpacing(10)
        v.addWidget(QLabel("What's new", objectName="sheetTitle"))
        v.addWidget(QLabel(f"Light Client {VERSION}", objectName="sheetText"))
        scroll = notes_widget()
        scroll.setMinimumSize(460, 360)
        v.addWidget(scroll, 1)
        from .modern import PillButton
        row = QHBoxLayout()
        row.addStretch()
        ok = PillButton("Got it", "check", "primary")
        ok.clicked.connect(self.accept)
        row.addWidget(ok)
        self.set_default(ok)
        v.addLayout(row)


def show_if_new(win) -> None:
    """Once after each update: the notes for this version."""
    cfg = config.load()
    seen = cfg.get("seen_news", "")
    if seen == VERSION:
        return
    cfg["seen_news"] = VERSION
    config.save(cfg)
    if not seen:
        from .. import paths
        try:
            had = any(paths.INSTANCES.iterdir())
        except OSError:
            had = False
        if not had:
            return   # a fresh install: nothing has changed for them yet
    WhatsNewDialog(win).open()
