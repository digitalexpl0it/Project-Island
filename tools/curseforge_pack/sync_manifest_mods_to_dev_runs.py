#!/usr/bin/env python3
"""Download every CurseForge-pinned mod JAR from modpack/curseforge/manifest.json into dev runs.

Writes manifest JARs into ``run-client/mods/`` for every pin, and into ``run-server/mods/``
for all pins **except** a small **client-only dev** set (Inventory HUD+ — CurseForge
**``357540``** / HUD overlay) so ``runServer`` matches a minimal dedicated layout. Gradle
``runClient`` still gets every client manifest mod.

Uses the public CurseForge file API + Forge CDN (no API key). URL-encodes ``+`` in
filenames for ``mediafiles.forgecdn.net``.

Usage::

  python3 tools/curseforge_pack/sync_manifest_mods_to_dev_runs.py [--manifest PATH] [--dry-run]

After changing ``manifest.json`` (e.g. ``bump_manifest_latest_neoforge.py --apply``), run
this (or ``./gradlew syncManifestModJarsToDevRuns``) then refresh ``MOD_LIST.md`` if
filenames changed.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

# Do not place these ``projectID`` JARs under ``run-server/mods`` (client-first HUD / UI).
MANIFEST_DEV_SERVER_SKIP_PROJECT_IDS = frozenset({357540})


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

    for row in rows:
        pid = int(row["projectID"])
        fid = int(row["fileID"])
        time.sleep(max(0.0, args.sleep))
        meta = api_file(pid, fid)
        name = meta.get("fileName") or ""
        if not name.endswith(".jar"):
            print(f"skip (not jar)\t{pid}\t{fid}\t{name}", file=sys.stderr)
            continue
        print(f"{pid}\t{fid}\t{name}")
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

    if args.dry_run:
        print(f"Dry-run: would sync {len(rows)} manifest row(s).", file=sys.stderr)
    else:
        print(
            f"Synced {len(rows)} JAR(s) to {client_mods} (and to {server_mods} except projectIDs {sorted(MANIFEST_DEV_SERVER_SKIP_PROJECT_IDS)}).",
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
