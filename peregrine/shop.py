"""Light Fragments and the cosmetics you can buy with them.

Fragments are earned (a starter gift and a bit every day you open the launcher) and spent on
capes. What you own is kept in <data>/shop/wallet.json, and the game reads <data>/shop/owned.json.

This is a local wallet: it lives on your computer, so it is not tamper-proof. Buying fragments with
real money needs a payment provider and a server that keeps balances; set the "fragments_url" in the
launcher config to your checkout page and the Locker shows a button for it. Until then, no money moves.
"""
import json
import time

from . import config, paths

DIR = paths.DATA / "shop"
WALLET = DIR / "wallet.json"
OWNED = DIR / "owned.json"

STARTER = 60
DAILY = 15
FREE = ("light",)

# the cape files (see locker.CAPE_FILES) and what they cost in Light Fragments
PRICES = {
    "violet": 60, "ember": 60, "pumpkin": 60, "frost": 60,
    "dawn": 80, "mint": 80, "rose": 80,
    "midnight": 90, "obsidian": 90,
    "starry": 100, "prism": 100, "lightspeed": 100,
    "sunbeam": 120,
    "aurora": 150, "fragments": 150,
}


def _read() -> dict:
    try:
        w = json.loads(WALLET.read_text())
        if isinstance(w, dict):
            return w
    except (OSError, ValueError):
        pass
    return {}


def _write(w: dict) -> None:
    DIR.mkdir(parents=True, exist_ok=True)
    WALLET.write_text(json.dumps(w, indent=2))
    OWNED.write_text(json.dumps({"capes": sorted(set(FREE) | set(w.get("owned", [])))}))


def _wear_now() -> str:
    """The cape file worn right now (so people don't lose the one they already use)."""
    try:
        from . import locker
        i = locker.peregrine_cape()
        name = locker.CAPE_FILES[i]
        return name if name in PRICES else ""
    except Exception:
        return ""


def load(today: str = None) -> dict:
    """The wallet, with the starter gift and today's bonus paid in."""
    w = _read()
    today = today or time.strftime("%Y-%m-%d")
    changed = False
    if "fragments" not in w:
        w["fragments"] = STARTER
        w["owned"] = [c for c in (_wear_now(),) if c]   # the cape you already wear stays yours
        w["last_daily"] = today
        changed = True
    elif w.get("last_daily") != today:
        w["fragments"] = int(w["fragments"]) + DAILY
        w["last_daily"] = today
        changed = True
    if changed or not OWNED.exists():
        _write(w)
    return w


def balance() -> int:
    return int(load().get("fragments", 0))


def price(file: str) -> int:
    """0 when it is free."""
    return PRICES.get(file, 0)


def owns(file: str) -> bool:
    if not file or file == "custom" or file in FREE or file not in PRICES:
        return True
    return file in load().get("owned", [])


def buy(file: str) -> str:
    """Spends fragments on a cape. Returns '' on success, or why not."""
    if file not in PRICES:
        return "That one is not for sale."
    w = load()
    if file in w.get("owned", []):
        return "You already own it."
    cost = PRICES[file]
    if int(w.get("fragments", 0)) < cost:
        return f"You need {cost - int(w.get('fragments', 0))} more Light Fragments."
    w["fragments"] = int(w["fragments"]) - cost
    w.setdefault("owned", []).append(file)
    _write(w)
    return ""


def checkout_url() -> str:
    """Where to buy fragments with real money, if the launcher was set up with one."""
    url = str(config.load().get("fragments_url") or "").strip()
    return url if url.startswith("https://") else ""
