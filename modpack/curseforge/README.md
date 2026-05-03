# Project Island — CurseForge modpack layout

This folder matches what the **CurseForge** launcher expects when you upload a **Minecraft modpack** project (zip containing `manifest.json`, optional `modlist.html`, and `overrides/`).

**CurseForge website (summary, license, images, files):** **[`CURSEFORGE_PAGE.md`](CURSEFORGE_PAGE.md)**.

**Pack release notes (paste into CurseForge file changelogs):** **[`CHANGELOG_CLIENT.md`](CHANGELOG_CLIENT.md)** · **[`CHANGELOG_SERVER.md`](CHANGELOG_SERVER.md)**.

**CurseForge page body (overview description):** **[`DESCRIPTION_OVERVIEW.md`](DESCRIPTION_OVERVIEW.md)**.

## Versions

| Field | Value |
|-------|--------|
| Minecraft | **1.21.1** (see root `gradle.properties`) |
| NeoForge | **21.1.227** |
| Pack / mod version | Keep **`manifest.json`** `version` in sync with **`gradle.properties`** `mod_version` when you cut a release. |

## Build the zips (includes Project Island)

From the repository root:

```bash
./gradlew curseforgeModpackZip          # client pack (primary CurseForge download)
./gradlew curseforgeServerPackZip       # dedicated server pack (upload as Additional file)
./gradlew curseforgeModpackAll          # both
```

Outputs:

| Gradle task | Artifact |
|-------------|----------|
| **`curseforgeModpackZip`** | **`build/dist/projectisland-modpack-<version>-curseforge.zip`** |
| **`curseforgeServerPackZip`** | **`build/dist/projectisland-modpack-<version>-curseforge-server.zip`** |

Both tasks copy **`build/libs/projectisland-<version>.jar`** into **`overrides/mods/`** and merge **`examples/dev-progression/ftbquests/`** into **`overrides/config/ftbquests/`** (same chapters as **`./gradlew runClient`**). Without those files the **FTB Quests** book is empty even though the mod is on the manifest—**single-player vs multiplayer does not matter**.

### Server pack vs client

The **server** zip uses **`manifest.json`** with **`name`: `Project Island (Server)`** and drops **CurseForge `projectID`s** listed in **`server-pack-excluded-project-ids.json`** (JEI, Xaero’s map mods, Embeddium + GlitchCore + Jupiter + Uranus + NeOculus, Resource Pack Overrides). It **does not** ship **`modlist.html`**, **`resourcepackoverrides.json`**, **`overrides/resourcepacks/`**, or **`overrides/shaderpacks/`**. When you add a mod to **`manifest.json`**, remove its **`projectID`** from that JSON if it is client-only on the server.

## Pack logo / thumbnail

The repo logo lives at **`branding/Project_Island_logo.png`** (see **`branding/README.md`**). It is bundled in the zip under **`branding/`** for convenience only—**CurseForge never reads it from the zip** as the project thumbnail. After you create or update the project, upload that PNG under **Edit project → Images / Logo** every time you need the listing art.

## Ground truth: what to include

Authoritative **installed** sets live on disk (often **gitignored**):

| Location | Role |
|----------|------|
| **`run-server/mods/`** | Dedicated-server mod set — use this as the **minimum** third-party list for multiplayer parity. |
| **`run-client/mods/`** | Client mod set — usually **server mods + client extras** (performance, shaders loader, optional QoL). |

Diff the folder listing against **`manifest.json`** `files`: anything **not** covered by a manifest entry must sit under **`overrides/mods/`** (or get a new `projectID`/`fileID` line). The **RPG Series / Spell Engine** pins match **`MOD_LIST.md`**; remaining usual gaps for a full dev **`mods/`** folder are listed in **`overrides/mods/README-SUPPLEMENT.md`**.

**Shader packs:** see **`overrides/shaderpacks/README.md`**. Example in dev: **`run-client/shaderpacks/ComplementaryReimagined_r5.7.1.zip`** — do **not** assume it can ship on CurseForge until you confirm the license.

**Resource packs** (including **title screen / menu** packs): put the **`.zip`** in **`overrides/resourcepacks/`** — see **`overrides/resourcepacks/README.md`**. The pack ships **[Resource Pack Overrides](https://www.curseforge.com/minecraft/mc-mods/resource-pack-overrides)** with **`overrides/config/resourcepackoverrides.json`**, which enables **`Project_Island_menu_assets.zip`** by default for fresh installs (rename your menu pack to match, or edit that JSON). Players can still reorder packs unless marked required there.

## Publishing on CurseForge

1. Create a **Modpack** project (Minecraft / Modpacks).
2. Upload **`projectisland-modpack-<version>-curseforge.zip`** as the **primary** file (player / client install).
3. Upload **`projectisland-modpack-<version>-curseforge-server.zip`** as an **Additional file** on the same version so hosts and “server install” flows can download it (wording on the site varies).
4. The launcher resolves each **`manifest.json`** `projectID` + `fileID` from CurseForge CDN.
5. Files under **`overrides/`** layer on top (configs, optional extra mods in `overrides/mods/`).

## Expanding the manifest

To add another CurseForge-hosted mod:

1. Open the mod’s CurseForge page → **Files** → pick the build for **1.21.1** + **NeoForge**.
2. Note **Project ID** (sidebar) and **File ID** (URL `/files/<id>`).
3. Append `{ "projectID": …, "fileID": …, "required": true }` to **`manifest.json`** (keep entries sorted by `projectID` if you like clean diffs).
4. Optionally add a row to **`modlist.html`**.

Helper (optional): `tools/curseforge_pack/resolve_file_id.py`

## Full mod list

Curated third-party filenames and links live in **[`MOD_LIST.md`](../../MOD_LIST.md)**. Anything not listed in `manifest.json` should be placed under **`overrides/mods/`** until you add manifest entries — see **`overrides/mods/README-SUPPLEMENT.md`**.
