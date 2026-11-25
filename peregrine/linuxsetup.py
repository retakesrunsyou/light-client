"""Install the Linux launcher AppImage and register desktop shortcuts."""
import os
import shlex
import shutil
import subprocess
import sys
import tempfile
from ctypes.util import find_library
from pathlib import Path

from . import VERSION, config

IMAGE_NAME = "Light-Client.AppImage"


def default_dir():
    return Path.home() / "Applications"


def installed():
    app = Path(config.load().get("linux_install_dir") or default_dir()) / IMAGE_NAME
    if app.is_file():
        marker = app.with_suffix(".version")
        return {"dir": app.parent, "version": marker.read_text().strip() if marker.is_file() else ""}
    return {}


def command(app):
    args = [str(app)]
    if not os.access("/dev/fuse", os.R_OK | os.W_OK) or not find_library("fuse"):
        args.append("--appimage-extract-and-run")
    return args


def register(app, desktop=False, startup=None):
    data = Path(os.environ.get("XDG_DATA_HOME") or Path.home() / ".local/share")
    icons = data / "icons/hicolor/512x512/apps"
    icon = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parents[1])) / "assets/peregrine-512.png"
    if icon.is_file():
        icons.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(icon, icons / "peregrine.png")
    executable, *args = command(app)
    quoted = '"' + ''.join('\\' + char if char in '\\"$`' else char for char in executable) + '"'
    exec_line = " ".join([quoted.replace("%", "%%"), *map(shlex.quote, args), "%U"])
    entry = ("[Desktop Entry]\nType=Application\nName=Light Client Launcher\n"
             f"Exec={exec_line}\nIcon=peregrine\nStartupWMClass=peregrine\n"
             "StartupNotify=true\nCategories=Game;\nTerminal=false\n")

    def shortcut(folder):
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / "peregrine.desktop"
        path.write_text(entry, encoding="utf-8")
        return path

    shortcut(data / "applications")
    if desktop:
        try:
            location = subprocess.run(["xdg-user-dir", "DESKTOP"], capture_output=True, text=True).stdout.strip()
        except OSError:
            location = ""
        folder = Path(location) if location else Path.home() / "Desktop"
        if folder.is_dir() and folder != Path.home():
            shortcut(folder).chmod(0o755)
    if startup is not None:
        cfg = Path(os.environ.get("XDG_CONFIG_HOME") or Path.home() / ".config")
        if startup:
            shortcut(cfg / "autostart")
        else:
            (cfg / "autostart/peregrine.desktop").unlink(missing_ok=True)


def install(payload, target, version, desktop, startup, progress=lambda *_: None):
    target = target.expanduser().resolve()
    target.mkdir(parents=True, exist_ok=True)
    app = target / IMAGE_NAME
    progress(20, 100, "Installing Light Client")
    with tempfile.NamedTemporaryFile(dir=target, prefix=".light-client-", delete=False) as stage:
        staged = Path(stage.name)
        try:
            with Path(payload).open("rb") as source:
                shutil.copyfileobj(source, stage)
            stage.flush()
            staged.chmod(0o755)
            progress(80, 100, "Adding shortcuts")
            register(app, desktop, startup)
            os.replace(staged, app)
        finally:
            staged.unlink(missing_ok=True)
    app.with_suffix(".version").write_text(version or VERSION)
    config.save(dict(config.load(), desktop_shortcut_made=True, linux_install_dir=str(target)))
    progress(100, 100, "Done")


def launch(target):
    env = dict(os.environ, PYINSTALLER_RESET_ENVIRONMENT="1")
    env.pop("LD_LIBRARY_PATH", None)
    subprocess.Popen(command(target / IMAGE_NAME), env=env, start_new_session=True)
