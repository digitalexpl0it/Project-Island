# Changelog — Project Island modpack (client)

Player-facing notes for **`projectisland-modpack-<version>-curseforge.zip`** (primary CurseForge download). Build: `./gradlew curseforgeModpackZip`.

**Server pack changelog:** [`CHANGELOG_SERVER.md`](CHANGELOG_SERVER.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions match **`manifest.json`** / **`gradle.properties`** `mod_version`.

---

## [Unreleased]

_Add entries here while developing; copy the relevant bullets into the CurseForge **client** file changelog when you publish._

---

## [0.1.0] — 2026-05-03

### Added

- Initial **NeoForge 1.21.1** CurseForge client pack: full **`manifest.json`** mod pins (RPG stack, **Lootr**, **Waystones**, **Wings Of Fire**, **Biomes O’ Plenty**, **FTB** stack, **JEI**, **Xaero’s** minimap/world map, **Embeddium** rendering stack, etc.).
- **Project Island** mod JAR bundled under **`overrides/mods/`**.
- **FTB Quests** chapters (**Project Island** + **RPG Series**) under **`overrides/config/ftbquests/`** so the quest book is populated out of the box.
- **[Resource Pack Overrides](https://www.curseforge.com/minecraft/mc-mods/resource-pack-overrides)** plus **`overrides/config/resourcepackoverrides.json`** — default-on external menu/title pack id **`file/Project_Island_menu_assets.zip`** when that zip is shipped under **`overrides/resourcepacks/`** (rename your pack or edit the config).
- **`modlist.html`** for launcher mod list pages where shown.
- **`branding/Project_Island_logo.png`** inside the zip for handoff (CurseForge project thumbnail still set manually on the website).

### Notes

- Match **Minecraft** / **NeoForge** to the file label when installing.
