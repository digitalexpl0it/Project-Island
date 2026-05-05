# Changelog — Project Island modpack (client)

Player-facing notes for **`projectisland-modpack-<version>-curseforge.zip`** (primary CurseForge download). Build: `./gradlew curseforgeModpackZip`.

**Server pack changelog:** [`CHANGELOG_SERVER.md`](CHANGELOG_SERVER.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions match **`manifest.json`** / **`gradle.properties`** `mod_version`.

---

## [Unreleased]

_Add entries here while developing; copy into the latest `[x.y.z]` section (or a new one) when you publish._

---

## [0.1.0] — 2026-05-02

### Added

- Initial **NeoForge 1.21.1** CurseForge client pack: full **`manifest.json`** mod pins (RPG stack, **Lootr**, **Waystones**, **Wings Of Fire**, **Biomes O’ Plenty**, **FTB** stack, **JEI**, **Xaero’s** minimap/world map, **Embeddium** rendering stack, etc.).
- **Project Island** mod JAR bundled under **`overrides/mods/`**.
- **FTB Quests** chapters (**Project Island** + **RPG Series**) under **`overrides/config/ftbquests/`** so the quest book is populated out of the box.
- **[Resource Pack Overrides](https://www.curseforge.com/minecraft/mc-mods/resource-pack-overrides)** plus **`overrides/config/resourcepackoverrides.json`** — default-on external menu/title pack id **`file/Project_Island_menu_assets.zip`** when that zip is shipped under **`overrides/resourcepacks/`** (rename your pack or edit the config).
- **`modlist.html`** for launcher mod list pages where shown.
- **`branding/Project_Island_logo.png`** inside the zip for handoff (CurseForge project thumbnail still set manually on the website).

### Changed

- **FTB Quests — Project Island chapter:** **Harpoon Gun & Rope Surfing** / **Reinforced Rope** rewards no longer reference **Create** items; **Stockpile Andesite** replaces the old alloy quest — [`examples/dev-progression/ftbquests/quests/chapters/project_island.snbt`](../../examples/dev-progression/ftbquests/quests/chapters/project_island.snbt).
- **Server pack Gradle build:** **`curseforgeServerPackZip`** copies third-party JARs from **`run-server/mods/`** (no CurseForge API). Optional dev jar naming: **`append_dev_timestamp_to_mod_version`** in root **`gradle.properties`** (see root **README** **Distribution**).

### Notes

- Match **Minecraft** / **NeoForge** to the file label when installing.
