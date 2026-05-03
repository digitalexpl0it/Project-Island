#!/usr/bin/env python3
"""Resolve a CurseForge file id for an exact jar name (public files API; no API key).

Pagination uses ``pageIndex`` (the public ``www.curseforge.com`` listing ignores ``index``).

Usage:
  python3 tools/curseforge_pack/resolve_file_id.py <project_id> <exact_filename>

Example:
  python3 tools/curseforge_pack/resolve_file_id.py 531761 balm-neoforge-1.21.1-21.0.56.jar
"""
from __future__ import annotations

import json
import sys
import urllib.request


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    project_id = int(sys.argv[1])
    want = sys.argv[2]
    # Public www.curseforge.com files listing ignores `index`; use `pageIndex` (0-based page).
    page_index = 0
    while page_index < 2000:
        url = (
            f"https://www.curseforge.com/api/v1/mods/{project_id}/files"
            f"?pageSize=50&pageIndex={page_index}"
        )
        with urllib.request.urlopen(url) as resp:
            data = json.load(resp).get("data") or []
        if not data:
            break
        for f in data:
            if f.get("fileName") == want:
                print(f["id"])
                return 0
        page_index += 1
    print("not found", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
