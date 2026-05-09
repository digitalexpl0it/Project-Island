# Changelog — Project Island modpack (client)

Player-facing notes for **`projectisland-modpack-<version>-curseforge.zip`** (primary CurseForge download). Build: **`./gradlew curseforgeModpackZip`**.

**Server pack changelog:** [`CHANGELOG_SERVER.md`](CHANGELOG_SERVER.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The zip’s **`manifest.json`** **`version`** is written at build time from **`project.version`** (normally **`gradle.properties`** **`mod_version`**).

---

## [Unreleased]

_Add entries here while developing; merge into `[x.y.z]` when you publish._

---

## [0.1.2] — 2026-05-09

### Added

- **Starter supply chest** content is documented in the **Project Island** intro quest; loot is provided by the **Project Island** mod (**`projectisland-0.1.2.jar`**) when players receive their starter island.

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
