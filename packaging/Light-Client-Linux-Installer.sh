#!/usr/bin/env bash
# Light Client installer for Linux: downloads the latest Light Client Launcher (an AppImage),
# puts it in ~/Applications, and adds it to your app menu. Run it again any time to update;
# your instances, worlds and settings are kept. Remove it with: --uninstall
set -euo pipefail

REPO="retakesrunsyou/peregrine-launcher"
DIR="$HOME/Applications"
APP="$DIR/Light-Client.AppImage"
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"

say() { printf '%s\n' "$*"; }
fail() { say; say "Install failed: $1"; exit 1; }

if [ "${1:-}" = "--uninstall" ]; then
  rm -f "$APP" "$DATA/applications/peregrine.desktop"
  DESKTOP_DIR="$(command -v xdg-user-dir >/dev/null && xdg-user-dir DESKTOP 2>/dev/null || echo "$HOME/Desktop")"
  rm -f "$DESKTOP_DIR/peregrine.desktop"
  say "Light Client Launcher was removed. Your games and settings are still in $DATA/peregrine"
  say "(delete that folder too if you want them gone)."
  exit 0
fi

[ "$(uname -m)" = "x86_64" ] || fail "Light Client's Linux app is for 64-bit Intel/AMD PCs (x86_64)."
command -v curl >/dev/null || fail "curl isn't installed. Run: sudo apt install curl"

# AppImages need FUSE 2 to start (most desktops have it; Ubuntu 22.04+ needs libfuse2)
if command -v ldconfig >/dev/null && ! ldconfig -p 2>/dev/null | grep -q "libfuse.so.2"; then
  say "Light Client needs FUSE 2 first. Run:"
  say
  say "  sudo apt install libfuse2      (Ubuntu 24.04: libfuse2t64)"
  say
  say "Then run this installer again."
  exit 1
fi

say "Finding the latest Light Client…"
json="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest")" || fail "couldn't reach GitHub. Are you online?"
url=""
for name in Light-Client-x86_64.AppImage Peregrine-x86_64.AppImage; do
  url="$(printf '%s' "$json" | grep -o "\"browser_download_url\": *\"[^\"]*/$name\"" | head -n 1 | sed 's/.*"\(https[^"]*\)"/\1/')"
  [ -n "$url" ] && break
done
[ -n "$url" ] || fail "the latest release has no Linux app yet. Try again in a few minutes."
version="$(printf '%s' "$json" | grep -o '"tag_name": *"[^"]*"' | head -n 1 | sed 's/.*"\([^"]*\)"$/\1/')"

mkdir -p "$DIR"
say "Downloading Light Client ${version}…"
curl -fL --progress-bar -o "$APP.part" "$url" || fail "the download didn't finish."
chmod +x "$APP.part"
mv -f "$APP.part" "$APP"

# The app adds itself to the app menu (and the desktop) the first time it runs: do that quietly now.
QT_QPA_PLATFORM=offscreen timeout 60 "$APP" --smoke >/dev/null 2>&1 || true

say
say "Done! Open 'Light Client Launcher' from your app menu, or run: $APP"
say "It updates itself from now on."
