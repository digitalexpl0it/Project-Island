#!/usr/bin/env python3
"""Pick latest CurseForge file per manifest row for Minecraft 1.21.1 + NeoForge.

Uses the public www.curseforge.com files API (no API key). Filters files whose
``gameVersions`` contains **exact** ``\"1.21.1\"`` and ``\"NeoForge\"`` (so
``1.21.11`` etc. do not match). Among matches, picks the newest ``dateCreated``
(upload time) so a newer beta (e.g. JEI) is not replaced by an older “Release” build.

Usage:
  python3 tools/curseforge_pack/bump_manifest_latest_neoforge.py \\
      --manifest modpack/curseforge/manifest.json [--apply]

Without ``--apply``, prints a TSV table and exits 1 if any pin would change.
With ``--apply``, rewrites ``manifest.json`` (pretty JSON, sorted by projectID).

See ``modpack/curseforge/README.md`` — refresh ``MOD_LIST.md`` / bundled JARs
after applying bumps.
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path


MC = "1.21.1"
LOADER = "NeoForge"


def fetch_mod_files(project_id: int, page_size: int = 50, max_pages: int = 80) -> list[dict]:
    out: list[dict] = []
    for page_index in range(max_pages):
        q = urllib.parse.urlencode(
            {"pageSize": page_size, "pageIndex": page_index}
        )
        url = f"https://www.curseforge.com/api/v1/mods/{project_id}/files?{q}"
        with urllib.request.urlopen(url) as resp:
            chunk = json.load(resp).get("data") or []
        if not chunk:
            break
        out.extend(chunk)
        if len(chunk) < page_size:
            break
    return out


def pick_latest_neoforge_1211(files: list[dict]) -> dict | None:
    candidates: list[dict] = []
    for f in files:
        gv = f.get("gameVersions") or []
        if MC not in gv or LOADER not in gv:
            continue
        if f.get("status") not in (None, 4):
            continue
        candidates.append(f)
    if not candidates:
        return None

    def sort_key(f: dict) -> str:
        return f.get("dateCreated") or ""

    candidates.sort(key=sort_key, reverse=True)
    return candidates[0]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", type=Path, required=True)
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    data = json.loads(args.manifest.read_text(encoding="utf-8"))
    rows = data.get("files") or []
    changes: list[tuple[int, int, int, str, str]] = []
    new_rows: list[dict] = []

    for row in rows:
        pid = int(row["projectID"])
        cur_fid = int(row["fileID"])
        files = fetch_mod_files(pid)
        best = pick_latest_neoforge_1211(files)
        if not best:
            print(f"WARN\t{pid}\tno\t{MC}+{LOADER}\tfile", file=sys.stderr)
            new_rows.append(row)
            continue
        new_fid = int(best["id"])
        name = best.get("fileName", "")
        if new_fid != cur_fid:
            changes.append((pid, cur_fid, new_fid, name, best.get("dateCreated", "")))
        new_rows.append({"projectID": pid, "fileID": new_fid, "required": bool(row.get("required", True))})

    new_rows.sort(key=lambda r: r["projectID"])

    for pid, old_f, new_f, name, dc in sorted(changes, key=lambda x: x[0]):
        print(f"{pid}\t{old_f}\t{new_f}\t{name}\t{dc}")

    if not args.apply:
        if changes:
            print(f"\n{len(changes)} pin(s) behind latest; re-run with --apply to write manifest.", file=sys.stderr)
            return 1
        print("All pins already match latest NeoForge 1.21.1 file on CurseForge.", file=sys.stderr)
        return 0

    data["files"] = new_rows
    args.manifest.write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {args.manifest} ({len(changes)} fileID updates).", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
