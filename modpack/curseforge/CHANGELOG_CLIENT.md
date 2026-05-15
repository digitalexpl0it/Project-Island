# Changelog — Project Island modpack (client)

Player-facing notes for **`projectisland-modpack-<version>-curseforge.zip`** (primary CurseForge download). Build: **`./gradlew curseforgeModpackZip`**.

**Server pack changelog:** [`CHANGELOG_SERVER.md`](CHANGELOG_SERVER.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The zip’s **`manifest.json`** **`version`** is written at build time from **`project.version`** (normally **`gradle.properties`** **`mod_version`**).

---

## [1.3.0] — 2026-05-12

### Changed

- **Pack version** **`1.3.0`** (from **`gradle.properties`** **`mod_version`**); built **`manifest.json`** **`version`** matches. **CurseForge client manifest:** **Project Island** (**`projectID` `1534225`**) pins **`fileID` `8090568`** (**`projectisland-1.3.0.jar`**).
- **Inventory HUD+:** **`overrides/config/inventoryhud-client.toml`** now ships full pack defaults (armor / potion HUD layout, positions, scales — inventory hotbar strip still **`byDefault = false`** until players toggle it on).
- **Xaero’s Minimap / World Map:** **`overrides/config/xaerominimap-common.txt`** and **`overrides/config/xaeroworldmap-common.txt`** — server-side profile defaults (**`Default`** minimap profile, **`ProjectIsland`** world map profile). Fresh installs merge from the pack; existing **`config/`** files are not overwritten.

## [0.1.2] — 2026-05-11

### Added

- **[Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)** (**NeoForge 1.21.1** pin **`6529130`**) — **Traveler's Backpack** can be worn on the **Curios back** slot (the pack still ships **Accessories** for RPG trinkets). **`curios-neoforge-9.5.1+1.21.1.jar`** is **committed** under **`modpack/curseforge/overrides/mods/`** so the **additional server** zip always ships Curios (matches Traveler's Backpack / GraveStone / FallingTree).
- **[Inventory HUD+](https://www.curseforge.com/minecraft/mc-mods/inventory-hud-forge)** (**NeoForge 1.21.1** pin **`6369797`**) — HUD overlay mod; **`357540`** added to **`server-pack-excluded-project-ids.json`** as a **client-first** checklist entry (optional on dedicated servers).
- **[Traveler's Backpack](https://www.curseforge.com/minecraft/mc-mods/travelers-backpack)**, **[GraveStone Mod](https://www.curseforge.com/minecraft/mc-mods/gravestone-mod)**, and **[FallingTree](https://www.curseforge.com/minecraft/mc-mods/falling-tree)** on the CurseForge manifest (NeoForge **1.21.1** pins); **`MOD_LIST.md`** and dev **`run-server/mods`** / **`run-client/mods`** updated to match. The same JARs live under **`modpack/curseforge/overrides/mods/`** for the **server** zip; **`curseforgeModpackZip`** excludes them so the app does not install duplicates next to the manifest.
- **[ModernFix](https://www.curseforge.com/minecraft/mc-mods/modernfix)** and **[FerriteCore](https://www.curseforge.com/minecraft/mc-mods/ferritecore)** (NeoForge **1.21.1** pins); both ship in the server **`overrides/mods/`** fat pack when present under **`run-server/mods/`** (LGPL / MIT on CurseForge — not on the server-pack omit list). **`overrides/SERVER_README.md`** documents mods hosts must install manually (Gradle **`curseforgeServerPackUndistributableModJars`**).
- **Starter supply chest** content is documented in the **Project Island** intro quest; loot is provided by the **Project Island** mod (**`projectisland-0.1.2.jar`**) when players receive their starter island.
- **Floating islands look chunkier on the bottom:** procedural islands now grow **3–5** "stalactite root" spikes under each disk instead of one smooth dome. Existing chunks keep their old shape; fly into freshly generated terrain to see it. Server operators can tune or disable via **`floatingIslandBottomSpike*`** keys in **`config/projectisland-common.toml`**.

### Changed

- **CurseForge moderation (client zip):** **Project Island** is referenced only in **`manifest.json`** (**`projectID` `1534225`**, **`fileID` `8076227`**) — the **client** pack zip does **not** ship **`overrides/mods/projectisland-*.jar`** (CurseForge rejects duplicating a hosted mod JAR on the primary upload). The **server** additional zip still ships **`projectisland-*.jar`** under **`overrides/mods/`**. Bump **`fileID`** when you upload a new **Mods** file.
- **Inventory HUD+:** **`overrides/config/inventoryhud-client.toml`** added with the **inventory** strip **off** by default (**`byDefault = false`**); armor and potion HUDs on at mod defaults (see **1.3.0** for the expanded pack profile in the same file).
- **CurseForge pins** refreshed to latest **NeoForge 1.21.1** uploads (script: **`tools/curseforge_pack/bump_manifest_latest_neoforge.py --apply`**): **Waystones**, **Xaero’s Minimap** / **World Map**, **Lootr**, **Uranus**, **Creatures and Beasts: Continued** — see **`MOD_LIST.md`** for exact JAR names.
- **`overrides/config/fml.toml`**: **`versionCheck = false`** (NeoForge global update hints off; manifest pins remain authoritative). **`./gradlew syncManifestModJarsToDevRuns`** + **`tools/curseforge_pack/sync_manifest_mods_to_dev_runs.py`** align **`run-server/mods`** and **`run-client/mods`** with **`manifest.json`**.

### Fixed

- **FTB Quests:** **Ride the Zipline** rewards use **`minecraft:breeze_rod`** and **`minecraft:feather`** (replacing **`wind_charge`**, which is not in Minecraft **1.21.1**). **WoF** dark/gilded bound lanterns: use **`tempered_dark_phoenixlantern`** / **`tempered_gilded_phoenixlantern`** (matches **Wings Of Fire V1.0** jar ids; other breeds use **`…_phoenix_lantern`**).

### Build

- **`./gradlew curseforgeModpackZip`** (and the server pack task) run **`verifyDevProgressionFtbQuests`** so the zip is not produced if **`examples/dev-progression/ftbquests/`** is incomplete — the same tree Gradle copies for **`runClient`** / **`runServer`**.

---

## [0.1.0] — 2026-05-02

### Added

- Initial **NeoForge 1.21.1** CurseForge client pack: full **`manifest.json`** mod pins (RPG stack, **Lootr**, **Waystones**, **Wings Of Fire**, **Biomes O’ Plenty**, **FTB** stack, **JEI**, **Xaero’s** minimap/world map, **Embeddium** rendering stack, etc.).
- **Project Island** mod JAR under **`overrides/mods/`** (same version string as **`manifest.json`** **`version`** from the Gradle build).
- **FTB Quests** chapters (**Project Island** + **RPG Series**) under **`overrides/config/ftbquests/`** so the quest book is populated out of the box (source in repo: **`examples/dev-progression/ftbquests/`**).
- **[Resource Pack Overrides](https://www.curseforge.com/minecraft/mc-mods/resource-pack-overrides)** plus **`overrides/config/resourcepackoverrides.json`** — default-on external menu/title pack id **`file/Project_Island_menu_assets.zip`** when that zip is shipped under **`overrides/resourcepacks/`** (rename your pack or edit the config).
- **`modlist.html`** for launcher mod list pages where shown.
- **`branding/Project_Island_logo.png`** inside the zip for handoff (CurseForge project thumbnail still set manually on the website).

### Fixed

- **FTB Quests — Project Island:** rewards no longer reference **Create** items (not in this pack), so claiming does not show **Missing Item**. **Harpoon Gun & Rope Surfing** / **Reinforced Rope** use vanilla **iron** / **copper** rewards; **Stockpile Andesite** replaces the old alloy quest — [`examples/dev-progression/ftbquests/quests/chapters/project_island.snbt`](../../examples/dev-progression/ftbquests/quests/chapters/project_island.snbt).

### Notes

- Match **Minecraft** / **NeoForge** to the file label when installing.
- **Maintainers:** **`curseforgeServerPackZip`** (server **Additional file**) copies third-party JARs from **`run-server/mods/`** (populate that folder before **`./gradlew curseforgeModpackAll`**). Optional dev JAR names: **`append_dev_timestamp_to_mod_version`** in root **`gradle.properties`** (see root **README**, **Distribution**).
