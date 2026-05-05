# Project Island — CurseForge **mod** (JAR) listing

Use this folder when you publish the **standalone mod** on CurseForge (**Mods**, not **Modpacks**). The modpack lives under [`modpack/curseforge/`](../../modpack/curseforge/README.md).

## Build artifact

```bash
./gradlew build
```

Upload **`build/libs/projectisland-<version>.jar`** (version from [`gradle.properties`](../../gradle.properties) `mod_version`).

| File | Use on CurseForge |
|------|-------------------|
| [`CURSEFORGE_SUMMARY.txt`](CURSEFORGE_SUMMARY.txt) | Short **summary** / tagline (kept **≤ 256** characters for CurseForge) |
| [`DESCRIPTION.md`](DESCRIPTION.md) | Main **description** — paste the **whole** file into CurseForge (Markdown, no repo-only links; self-contained for players) |
| [`CHANGELOG.md`](CHANGELOG.md) | **Release** changelog (copy per file upload or keep in sync with repo) |

**License (mod code):** **MIT** — [`LICENSE`](../../LICENSE). Third-party mods you recommend in the description keep their own licenses.
