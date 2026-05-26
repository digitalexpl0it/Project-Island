#!/usr/bin/env python3
"""Download CurseForge manifest pins into dev runs (mods + client resource packs).

Writes manifest **``.jar``** files into ``run-client/mods/`` for every pin, and into
``run-server/mods/`` for all pins **except** a small **client-only dev** set (Inventory
HUD+ — CurseForge **``357540``** / HUD overlay) so ``runServer`` matches a minimal
dedicated layout. Gradle ``runClient`` still gets every client manifest mod.

Writes manifest **``.zip``** resource packs into ``run-client/resourcepacks/`` only (never
the server tree). Also copies ``modpack/curseforge/overrides/resourcepacks/*.zip`` (e.g.
``Project_Island_menu_assets.zip``) into that folder when present.

Uses the public CurseForge file API + Forge CDN (no API key). URL-encodes ``+`` in
filenames for ``mediafiles.forgecdn.net``.

Usage::

  python3 tools/curseforge_pack/sync_manifest_mods_to_dev_runs.py [--manifest PATH] [--dry-run]

After changing ``manifest.json`` (e.g. ``bump_manifest_latest_neoforge.py --apply``), run
this (or ``./gradlew syncManifestModJarsToDevRuns``) then refresh ``MOD_LIST.md`` if
filenames changed.

This script **does not delete** other ``.jar`` files in ``run-*/mods``. When CurseForge
changes a mod's **filename** between pins (e.g. two ``balm-neoforge-*`` versions), remove
the stale jar manually so NeoForge does not load duplicates.

**Optional Levite Fields testing:** ``levmod`` is not manifest-pinned; add it and its deps manually
if needed. **Sable** / Veil conflict with Embeddium — see ``docs/TECHNICAL_REFERENCE.md``.
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

# Do not place these ``projectID`` JARs under ``run-server/mods`` (client-only HUD / rendering).
# Embeddium is not manifest-pinned; omit it from ``run-server/mods`` manually when testing Sable/Veil.
MANIFEST_DEV_SERVER_SKIP_PROJECT_IDS = frozenset({
    357540,  # Inventory HUD+
    1010827,  # Uranus (Embeddium / Sodium companion)
    1072905,  # Jupiter (Embeddium / Sodium companion)
    1200907,  # NeOculus (requires Embeddium; shaders are client-only)
})


def api_file(project_id: int, file_id: int) -> dict:
    url = f"https://www.curseforge.com/api/v1/mods/{project_id}/files/{file_id}"
    with urllib.request.urlopen(url) as resp:
        return json.load(resp)["data"]


def download_bytes(fid: int, file_name: str) -> bytes:
    d1, d2 = fid // 1000, fid % 1000
    enc = urllib.parse.quote(file_name, safe="")
    url = f"https://mediafiles.forgecdn.net/files/{d1}/{d2}/{enc}"
    with urllib.request.urlopen(url) as resp:
        return resp.read()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--manifest",
        type=Path,
        default=Path("modpack/curseforge/manifest.json"),
        help="Path to CurseForge manifest.json (client pins).",
    )
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument(
        "--sleep",
        type=float,
        default=0.12,
        help="Seconds to sleep between CurseForge API calls (rate courtesy). Default: 0.12",
    )
    args = ap.parse_args()

    root = Path(__file__).resolve().parent.parent.parent
    manifest = root / args.manifest if not args.manifest.is_absolute() else args.manifest
    data = json.loads(manifest.read_text(encoding="utf-8"))
    rows = data.get("files") or []

    server_mods = root / "run-server" / "mods"
    client_mods = root / "run-client" / "mods"
    client_resourcepacks = root / "run-client" / "resourcepacks"
    overrides_resourcepacks = root / "modpack" / "curseforge" / "overrides" / "resourcepacks"

    jar_count = 0
    zip_count = 0

    for row in rows:
        pid = int(row["projectID"])
        fid = int(row["fileID"])
        time.sleep(max(0.0, args.sleep))
        meta = api_file(pid, fid)
        name = meta.get("fileName") or ""
        if name.endswith(".zip"):
            print(f"resourcepack\t{pid}\t{fid}\t{name}")
            zip_count += 1
            if args.dry_run:
                continue
            client_resourcepacks.mkdir(parents=True, exist_ok=True)
            blob = download_bytes(fid, name)
            (client_resourcepacks / name).write_bytes(blob)
            continue
        if not name.endswith(".jar"):
            print(f"skip\t{pid}\t{fid}\t{name}", file=sys.stderr)
            continue
        print(f"jar\t{pid}\t{fid}\t{name}")
        jar_count += 1
        if args.dry_run:
            continue
        client_mods.mkdir(parents=True, exist_ok=True)
        blob = download_bytes(fid, name)
        (client_mods / name).write_bytes(blob)
        if pid in MANIFEST_DEV_SERVER_SKIP_PROJECT_IDS:
            sp = server_mods / name
            if sp.is_file():
                sp.unlink()
        else:
            server_mods.mkdir(parents=True, exist_ok=True)
            (server_mods / name).write_bytes(blob)

    override_zip_count = 0
    if overrides_resourcepacks.is_dir():
        for src in sorted(overrides_resourcepacks.glob("*.zip")):
            override_zip_count += 1
            print(f"override resourcepack\t\t\t{src.name}")
            if args.dry_run:
                continue
            client_resourcepacks.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, client_resourcepacks / src.name)

    if args.dry_run:
        print(
            f"Dry-run: would sync {jar_count} JAR(s), {zip_count} manifest resource pack(s), "
            f"{override_zip_count} override resource pack(s).",
            file=sys.stderr,
        )
    else:
        print(
            f"Synced {jar_count} JAR(s) to {client_mods} "
            f"(and to {server_mods} except projectIDs {sorted(MANIFEST_DEV_SERVER_SKIP_PROJECT_IDS)}). "
            f"Synced {zip_count} manifest + {override_zip_count} override resource pack(s) to {client_resourcepacks}.",
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
