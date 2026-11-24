"""What's new: the latest changes, shown once after the launcher updates (and from Settings → Updates)."""
from PySide6.QtWidgets import QFrame, QHBoxLayout, QLabel, QScrollArea, QVBoxLayout, QWidget

from .. import VERSION, config
from .sheet import Sheet
from .theme import Theme

# Newest first: (version, title, what it does). Keep in step with the game's What's new page.
NOTES = [
    ("2.2.4", "More 1.7", "1.7 animations now has the old sneak (the camera only dips a little, straight away) and sword "
              "blocking: hold right click with a sword."),
    ("2.2.4", "No fog means no fog", "Fog off now clears the fog at the edge of your view too, not just the sky."),
    ("2.2.4", "Fly stop", "When you let go of the keys while flying you stop, instead of sliding on. On by default; "
              "turn it off in the Camera tab."),
    ("2.2.4", "More modules on older versions", "Auto reconnect, click sounds, chat tweaks and clean overlays now work on "
              "1.21.1 to 1.21.11 and 26.2+, not just 26.1."),
    ("2.2.3", "Pause menu on every version", "The pause menu buttons work again on 1.21.x and 26.2 and newer, not just 26.1."),
    ("2.2.2", "Pause menu clicks", "Buttons in the pause menu work again if the mouse moves a little while you click. "
              "To move a button, hold it for a moment and drag."),
    ("2.2.1", "Friends", "Add other Light Client players by their Minecraft name, chat, and call them with voice "
              "and screen sharing. Everything is encrypted so only the two of you can read or hear it."),
    ("2.2.1", "Tidier mod menu", "Modules in tabs (PvP, HUD, World and more), an Only on button, a count of what's on, "
              "and a scrollbar you can drag. Pick roomy or compact rows and turn animations off in Settings."),
    ("2.2.1", "Settings in tabs", "The launcher's settings are split into Appearance, Game, Performance and so on."),
    ("2.2.1", "New Lyrics", "Lyrics are now 3D words in the world: around you, overhead, or on the floor. Pick the font, colours and animations."),
    ("2.2.1", "Move the chat and scoreboard", "Drag and resize both in Edit HUD."),
    ("2.2.1", "GPU players", "Players are drawn on the GPU too (GPU mobs), skin layers included."),
    ("2.2.0", "Light Client", "New name, logo and main menu."),
    ("2.2.0", "GPU mobs", "Mobs drawn on the graphics card for more FPS (experimental)."),
    ("2.2.0", "Faster with lots of items", "Far items, XP, item frames and displays are skipped."),
    ("2.2.0", "Light Engine", "One-click FPS presets: Potato, Balanced, Max FPS, Quality."),
    ("2.2.0", "Saturation", "Make the world more vivid or greyer."),
    ("2.2.0", "Lyrics", "Synced lyrics on screen or in 3D, with word-by-word fill."),
    ("2.2.0", "Dropdown menus", "Pick options from a list."),
    ("2.2.0", "Holiday themes", "Seasonal colors with snow, fireworks, hearts or leaves."),
    ("2.2.0", "Music in the pause menu", "Current song with cover art and controls."),
    ("2.2.0", "New skies", "Realistic stars, sun and moon, lighter on FPS."),
    ("2.2.0", "Server icons", "Real server icons in the HUD and pause menu."),
    ("2.2.0", "Cleaner launcher", "Minecraft-style buttons and a still background."),
    ("2.2.0", "Faster game start", "ModernFix added to the FPS pack."),
    ("2.2.0", "Santa hat and bunny slippers", "In the Locker."),
    ("2.2.0", "Screenshots and clips", "Saved to Pictures and Videos."),
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
