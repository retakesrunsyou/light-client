"""Adds Peregrine Client (the Right Shift in-game menu) to Fabric instances.

The mod is built per Minecraft version and attached to the GitHub release as
peregrine-client-<version>.jar. If there's no build for an instance's version
yet, nothing happens and the game launches as normal.
"""

from pathlib import Path

from . import UPDATE_REPO, VERSION, modrinth, net, paths

PREFIX = "peregrine-client-"


def _version_tuple(v: str) -> tuple:
    out = []
    for part in str(v).lstrip("vV").split("."):
        digits = "".join(ch for ch in part if ch.isdigit())
        out.append(int(digits) if digits else 0)
    return tuple(out)


def _release_assets(max_age: float = 3600) -> dict:
    if not UPDATE_REPO:
        return {}

    def get(age):
        return net.get_json_cached(f"https://api.github.com/repos/{UPDATE_REPO}/releases/latest",
                                   paths.CACHE / "latest_release.json", age,
                                   headers={"Accept": "application/vnd.github+json"})
    release = get(max_age)
    if max_age > 60 and _version_tuple(release.get("tag_name", "0")) < _version_tuple(VERSION):
        # The saved release is older than this launcher (it was just updated): the
        # in-game mod for this version is out, so look again now instead of in an hour.
        release = get(60)
    return {a["name"]: a for a in release.get("assets", [])}


def _installed(mods: Path) -> list:
    return [f for f in mods.glob(PREFIX + "*.jar*")] if mods.is_dir() else []


def _has_fabric_api(mods: Path) -> bool:
    return any(f.name.lower().startswith(("fabric-api-", "fabric-api_")) for f in mods.glob("*.jar"))


def _sync_direct(mods: Path, name: str, progress, inst, why: Exception) -> str:
    """sync() for when the release can't be looked up: keep the jar we have, or download
    it from the release's direct link."""
    dest = mods / name
    if dest.with_name(dest.name + ".disabled").exists():
        return "turned off in Content"
    if dest.is_file():
        return "kept (release lookup failed)"
    if not UPDATE_REPO:
        raise why
    url = f"https://github.com/{UPDATE_REPO}/releases/latest/download/{name}"
    try:
        net.fetch_all([net.Download(url, dest)], "Light Client", progress)
    except Exception:
        raise why from None
    for f in _installed(mods):
        if f != dest:
            f.unlink()
    # No release id to remember: the next normal lookup replaces it once with the tracked copy.
    (mods / ".peregrine-client").write_text("direct\n")
    if not _has_fabric_api(mods):
        modrinth.install(["fabric-api"], inst.data["mc_version"], "fabric", mods, progress)
    return "installed (direct link)"


def sync(inst, enabled: bool, progress=None) -> str:
    """Make the instance's mods folder match the setting. Returns what happened."""
    if inst.data["loader"] != "fabric":
        return "not fabric"
    mods = inst.game_dir / "mods"
    if not enabled:
        for f in _installed(mods):
            f.unlink()
        return "removed"

    name = f"{PREFIX}{inst.data['mc_version']}.jar"
    try:
        asset = _release_assets().get(name)
        if not asset:
            # Maybe a release came out since we last looked (its files are attached a few
            # minutes after publishing), so check again instead of waiting an hour.
            asset = _release_assets(max_age=60).get(name)
    except Exception as e:
        # GitHub's API allows 60 lookups an hour per network, and shared networks (schools,
        # VPNs) run out. The file itself is still at its plain download link, which has no
        # such limit, so fetch it from there rather than starting without the menu.
        return _sync_direct(mods, name, progress, inst, e)
    if not asset:
        return "no build for this version"
    dest = mods / asset["name"]
    if dest.with_name(dest.name + ".disabled").exists():
        return "turned off in Content"  # the player switched it off for this instance
    for f in _installed(mods):
        if f != dest:
            f.unlink()  # a copy for another version
    # Remember which release file we have, so a rebuilt jar of the same size
    # still gets replaced when a new release comes out.
    marker = mods / ".peregrine-client"
    stamp = f"{asset.get('id', '')} {asset.get('updated_at', '')} {asset.get('size', '')}"
    if dest.exists() and (not marker.is_file() or marker.read_text().strip() != stamp):
        dest.unlink()
    downloads = [net.Download(asset["browser_download_url"], dest, size=asset.get("size"))]
    net.fetch_all(downloads, "Light Client", progress)
    marker.write_text(stamp + "\n")
    if not _has_fabric_api(mods):
        modrinth.install(["fabric-api"], inst.data["mc_version"], "fabric", mods, progress)
    return "installed"
