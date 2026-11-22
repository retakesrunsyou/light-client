"""Where Peregrine keeps things: the XDG folders on Linux, AppData on Windows."""

import os
import sys
from pathlib import Path

WINDOWS = sys.platform == "win32"


def _xdg(var: str, fallback: str) -> Path:
    value = os.environ.get(var)
    return Path(value) if value else Path.home() / fallback


if WINDOWS:
    # Games and downloads in Local (not synced between PCs), settings in Roaming.
    DATA = _xdg("LOCALAPPDATA", "AppData/Local") / "Peregrine"
    CONFIG = _xdg("APPDATA", "AppData/Roaming") / "Peregrine"
else:
    DATA = _xdg("XDG_DATA_HOME", ".local/share") / "peregrine"
    CONFIG = _xdg("XDG_CONFIG_HOME", ".config") / "peregrine"

def _media_home(kind: str, fallback: str, registry_name: str) -> Path:
    home = Path.home()
    if WINDOWS:
        try:
            import winreg
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER,
                                r"Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders") as key:
                value, _ = winreg.QueryValueEx(key, registry_name)
            folder = Path(os.path.expandvars(value))
            if folder.is_absolute():
                return folder
        except OSError:
            pass
    else:
        try:
            config = _xdg("XDG_CONFIG_HOME", ".config") / "user-dirs.dirs"
            prefix = f"XDG_{kind}_DIR="
            for line in config.read_text(encoding="utf-8").splitlines():
                if line.startswith(prefix):
                    value = line[len(prefix):].strip().strip('"')
                    folder = Path(value.replace("${HOME}", str(home)).replace("$HOME", str(home)))
                    if folder.is_absolute():
                        return folder
        except (OSError, UnicodeError):
            pass
    return home / fallback


SCREENSHOT_ROOT = _media_home("PICTURES", "Pictures", "My Pictures") / "Light Client"
CLIPS = _media_home("VIDEOS", "Videos", "My Video") / "Light Client"

LIBRARIES = DATA / "libraries"
ASSETS = DATA / "assets"
VERSIONS = DATA / "versions"
RUNTIMES = DATA / "runtimes"
INSTANCES = DATA / "instances"
NATIVES = DATA / "natives"
CACHE = DATA / "cache"
LOGS = DATA / "logs"

CONFIG_FILE = CONFIG / "config.json"
# Peregrine Client's settings (HUD layout, features, options), shared by every instance
# so they follow the player to any Minecraft version.
CLIENT_SETTINGS = DATA / "client" / "peregrine-client.json"
ACCOUNTS_FILE = CONFIG / "accounts.json"


def ensure() -> None:
    for p in (DATA, CONFIG, LIBRARIES, ASSETS, VERSIONS, RUNTIMES, INSTANCES, NATIVES, CACHE, LOGS):
        p.mkdir(parents=True, exist_ok=True)

def screenshot_root(game_dir: Path) -> Path:
    return SCREENSHOT_ROOT / game_dir.parent.name


def screenshot_files(game_dir: Path) -> list[Path]:
    files = []
    for folder in (screenshot_root(game_dir) / "screenshots", game_dir / "screenshots"):
        try:
            files.extend(p for p in folder.iterdir()
                         if p.suffix.lower() in (".png", ".jpg", ".jpeg") and p.is_file())
        except OSError:
            pass
    return files
