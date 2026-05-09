# Changelog — Project Island modpack (server)

Host-facing notes for **`projectisland-modpack-<version>-curseforge-server.zip`** (upload as **Additional file** on the same CurseForge release). Build: **`./gradlew curseforgeServerPackZip`** — copies third-party JARs from **`run-server/mods/`** (see [`README.md`](../../README.md) and [`modpack/curseforge/README.md`](README.md)).

**Client pack changelog:** [`CHANGELOG_CLIENT.md`](CHANGELOG_CLIENT.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). **`manifest.json`** **`version`** is written at build time from **`project.version`** (same base as **`gradle.properties`** **`mod_version`**). Server **`manifest.json`** uses **`name`:** **`Project Island (Server)`** and **`files`: `[]`** because mods ship under **`overrides/mods/`**.

---

## [Unreleased]

_Add entries here while developing; merge into `[x.y.z]` when you publish._

### Changed

- **`curseforgeServerPackZip`** no longer places certain third-party **`.jar`** filenames in **`overrides/mods/`** (not redistributable in our server zip); they are still expected under **`run-server/mods/`** for local dev but hosts must obtain those mods separately. Filenames are listed in root **`build.gradle`** (`curseforgeServerPackUndistributableModJars`).

---

## [0.1.0] — 2026-05-02

### Added

- Initial **NeoForge 1.21.1** dedicated-server pack: **`manifest.json`** with **`files`: `[]`** and a full **`mods/`** set under **`overrides/mods/`** — third-party **`.jar`** files are **copied from the maintainer’s `run-server/mods/`** at zip build time (**no CurseForge API**). The **Project Island** JAR in the zip always comes from **`./gradlew jar`** (any **`projectisland-*.jar`** already in **`run-server/mods`** is skipped when copying).
- **Project Island** mod JAR under **`overrides/mods/`**.
- **FTB Quests** SNBT under **`overrides/config/ftbquests/`** (parity with the client pack).

### Removed / not shipped (vs client)

Use **`server-pack-excluded-project-ids.json`** as a checklist: those **`projectID`s** are client-only in the **client** manifest — your **`run-server/mods`** tree should normally **omit** the same stacks on a headless server:

- **JEI**, **Xaero’s Minimap**, **Xaero’s World Map**
- **Embeddium**, **GlitchCore**, **Jupiter**, **Uranus**, **NeOculus**
- **Resource Pack Overrides**

Also omitted from the server zip tree: **`modlist.html`**, **`resourcepackoverrides.json`**, **`overrides/resourcepacks/`**, **`overrides/shaderpacks/`**.

### Fixed

- **FTB Quests — Project Island:** reward items aligned with the client pack (no **Create**-only ids), so dedicated servers do not show **Missing Item** on reward claim.

### Troubleshooting

- **“Channel … projectisland … missing on the server” when joining:** the server **`mods/`** folder must include **`projectisland-0.1.0.jar`** (same version as the client). Merge the zip’s **`overrides/`** into the server instance (or copy JARs into **`mods/`**). Re-download the **server** **Additional file** for **0.1.0** or rebuild with **`./gradlew curseforgeServerPackZip`** after **`run-server/mods/`** is populated.

### Notes

- Install with the same **Minecraft** / **NeoForge** build as the matching **client** release.
- **Maintainers:** if **`curseforgeServerPackZip`** fails, confirm **`run-server/mods/`** exists and contains at least one third-party **`.jar`** besides **`projectisland-*.jar`**.
