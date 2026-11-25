"""Entry point for the standalone Linux setup executable."""
import argparse
import sys
from pathlib import Path

from peregrine import VERSION, linuxsetup
from peregrine.ui.setup_ui import run_installer


def main():
    parser = argparse.ArgumentParser(description="Install Light Client Launcher")
    parser.add_argument("--install-dir", type=Path)
    parser.add_argument("--quiet", action="store_true", help="Install without opening the setup window")
    parser.add_argument("--no-desktop-shortcut", action="store_true")
    args = parser.parse_args()
    root = Path(getattr(sys, "_MEIPASS", Path(__file__).parent))
    payload = root / linuxsetup.IMAGE_NAME
    if not payload.is_file():
        parser.error("The bundled Light Client AppImage is missing")
    flags = ["/VERYSILENT"] if args.quiet else []
    if args.install_dir:
        flags.append(f"/DIR={args.install_dir}")
    if args.no_desktop_shortcut:
        flags.append("/TASKS=")
    return run_installer(VERSION, payload, flags, backend=linuxsetup)


if __name__ == "__main__":
    sys.exit(main())
