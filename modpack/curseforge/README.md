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
| Pack / mod **`version`** | **Injected at zip build time** from **`project.version`** (normally **`gradle.properties`** **`mod_version`**; if **`append_dev_timestamp_to_mod_version=true`**, the **manifest** **`version`** field uses **`mod_version+t<millis>`** so every Gradle run gets a new version string). You do **not** need to hand-edit **`manifest.json`** `version` before `./gradlew curseforgeModpackZip` / **`curseforgeServerPackZip`** — the written **`build/tmp/curseforge-client/manifest.json`** is what goes into the zips. |
| Gradle wrapper | **`gradle/wrapper/gradle-wrapper.properties`** — bump with **`./gradlew wrapper --gradle-version=…`** when you intentionally upgrade the toolchain. |

## Build the zips (CurseForge-compliant)

From the repository root:

```bash
./gradlew curseforgeModpackZip          # client pack (primary CurseForge download)
./gradlew curseforgeServerPackZip       # dedicated server pack — copies third-party JARs from run-server/mods (see below)
./gradlew curseforgeModpackAll          # both
```

Outputs:

| Gradle task | Artifact |
|-------------|----------|
| **`curseforgeModpackZip`** | **`build/dist/projectisland-modpack-<version>-curseforge.zip`** |
| **`curseforgeServerPackZip`** | **`build/dist/projectisland-modpack-<version>-curseforge-server.zip`** |

Both tasks merge **`examples/dev-progression/ftbquests/`** into **`overrides/config/ftbquests/`** (same chapters as **`./gradlew runClient`**). **Client pack:** **Project Island** is **only** a **`manifest.json`** pin (**CurseForge** project **`1534225`**, file **`8076227`** for **`0.1.2`**) — **no** **`projectisland-*.jar`** under **`overrides/mods/`** so CurseForge does not reject the **primary** upload for duplicating a hosted mod JAR. Bump **`fileID`** when you publish a new **Project Island** mod file. **Server pack:** **`curseforgeServerPackZip`** adds **`./gradlew jar`** output as **`overrides/mods/projectisland-*.jar`** and sets server **`manifest.json`** **`files`** to **`[]`** so the mod is not fetched twice. **`verifyDevProgressionFtbQuests`** runs first and fails the build if required SNBT (**`quests/data.snbt`**, **`quests/chapters/project_island.snbt`**, **`quests/chapters/rpg_series.snbt`**) is missing — so shipped zips always match the repo’s canonical quest tree. Without those files the **FTB Quests** book is empty even though the mod is on the manifest—**single-player vs multiplayer does not matter**.

### Server pack vs client

**Client zip** — Uses the full **`manifest.json`** **`files`** list (CurseForge **`projectID` / `fileID`** pins, including **Project Island**) so the launcher downloads mods; **`overrides/`** layers configs, FTB quest SNBT, resource packs, shader packs, etc. (no **`projectisland-*.jar`** under **`overrides/mods/`**).

**Server zip (fat `mods/`)** — **`copyCurseforgeServerModJarsFromRunServer`** copies every **`.jar`** in **`run-server/mods/`** except **`projectisland-*.jar`** and any filename in **`curseforgeServerPackUndistributableModJars`** in root **`build.gradle`** (mods we do not redistribute in the zip; hosts install those JARs separately). Staged copies go into **`build/tmp/curseforge-server/mod-jars/`**, and **`curseforgeServerPackZip`** places them under **`overrides/mods/`** together with **`./gradlew jar`** → **`projectisland-*.jar`**. The shipped server **`manifest.json`** uses **`files`: `[]`** (third-party mods are already under **`overrides/mods/`**; **Project Island** is the built JAR, not a second manifest download). Minecraft / NeoForge pins (**`minecraft.version`**, **`modLoaders`**) stay in the manifest for tooling.

**Before `curseforgeServerPackZip`:** ensure **`run-server/mods/`** exists and matches what a **dedicated server** should load (see **[`MOD_LIST.md`](../../MOD_LIST.md)** and **`./gradlew runServer`**). The task does **not** strip client-only mods for you — keep **`run-server/mods`** free of JEI, shader loaders, etc. Use **`server-pack-excluded-project-ids.json`** as a **checklist** of Curse **`projectID`s** the *client* manifest lists that typically should **not** appear on a headless server; filenames must match what you actually install under **`run-server/mods`**.

The server zip still **does not** ship **`modlist.html`**, **`resourcepackoverrides.json`**, **`overrides/resourcepacks/`**, or **`overrides/shaderpacks/`**. It **does** ship **`overrides/SERVER_README.md`** — dedicated host notes and **mods omitted from the zip** (must be installed manually; filenames match **`curseforgeServerPackUndistributableModJars`** in root **`build.gradle`**). When you add a mod to **`manifest.json`**, keep **`server-pack-excluded-project-ids.json`** in sync for documentation and client/server parity reviews.

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
4. The launcher resolves each **`manifest.json`** `projectID` + `fileID` from CurseForge CDN (client zip). The **server** additional file is a **fat** pack: third-party JARs are already under **`overrides/mods/`**; **`files`** in that **`manifest.json`** is empty by design.
5. Files under **`overrides/`** layer on top (configs, optional extra mods in `overrides/mods/`).

## Expanding the manifest

To add another CurseForge-hosted mod:

1. Open the mod’s CurseForge page → **Files** → pick the build for **1.21.1** + **NeoForge**.
2. Note **Project ID** (sidebar) and **File ID** (URL `/files/<id>`).
3. Append `{ "projectID": …, "fileID": …, "required": true }` to **`manifest.json`** (keep entries sorted by `projectID` if you like clean diffs).
4. Optionally add a row to **`modlist.html`**.

Helpers (optional): `tools/curseforge_pack/resolve_file_id.py` · `tools/curseforge_pack/sync_manifest_mods_to_dev_runs.py`

## Refreshing CurseForge pins (latest NeoForge **1.21.1**)

To bump every **`manifest.json`** row to the **newest** CurseForge file whose **`gameVersions`** include **exact** **`1.21.1`** + **`NeoForge`** (by upload date, so newer betas are not downgraded to older “Release” builds):

```bash
python3 tools/curseforge_pack/bump_manifest_latest_neoforge.py --manifest modpack/curseforge/manifest.json
python3 tools/curseforge_pack/bump_manifest_latest_neoforge.py --manifest modpack/curseforge/manifest.json --apply
```

After **`--apply`**: update **[`MOD_LIST.md`](../../MOD_LIST.md)** (and **`run-server/mods/`** / **`run-client/mods/`**) to match new JAR names; if **Waystones** (or another mod on **`curseforgeServerPackUndistributableModJars`**) renames its jar, sync **`build.gradle`** and **`overrides/SERVER_README.md`**.

### Dev `run-server/mods` + `run-client/mods` = manifest pins

Gradle: **`./gradlew syncManifestModJarsToDevRuns`** (Python **`tools/curseforge_pack/sync_manifest_mods_to_dev_runs.py`**) downloads **every** manifest **`fileID`** into **`run-client/mods/`** and into **`run-server/mods/`** except **client-first** pins (currently **Inventory HUD+** **`357540`** — server folder is skipped / stale file removed). Requires **`python3`** and network access. Does **not** delete extra JARs you added manually (e.g. Modrinth-only mods); keep those documented in **`MOD_LIST.md`**.

### NeoForge global version check (`config/fml.toml`)

The pack includes **`overrides/config/fml.toml`** with **`versionCheck = false`** so NeoForge’s optional global update hint system is off ([NeoForge update checker](https://docs.neoforged.net/docs/misc/updatechecker)). That does **not** disable third-party mods’ **own** update logic—those are per-mod **`config/`** files if they exist.

### CurseForge App / launchers

The **manifest** locks versions; the launcher should install **exact** **`fileID`s**. If a launcher offers **per-mod auto-updates**, turn them off for this profile so players stay on the pack’s tested set until you publish a new pack version.

## Full mod list

Curated third-party filenames and links live in **[`MOD_LIST.md`](../../MOD_LIST.md)**. Anything not listed in `manifest.json` should be placed under **`overrides/mods/`** until you add manifest entries — see **`overrides/mods/README-SUPPLEMENT.md`**.
