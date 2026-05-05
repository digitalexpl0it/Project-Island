# Changelog — Project Island modpack (server)

Host-facing notes for **`projectisland-modpack-<version>-curseforge-server.zip`** (upload as **Additional file** on the same CurseForge release). Build: **`./gradlew curseforgeServerPackZip`** — copies third-party JARs from **`run-server/mods/`** (see [`README.md`](README.md) **Server pack vs client**).

**Client pack changelog:** [`CHANGELOG_CLIENT.md`](CHANGELOG_CLIENT.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). **`manifest.json`** **`version`** is written at build time from **`gradle.properties`** **`mod_version`** (manifest uses **`name`: `Project Island (Server)`**).

---

## [Unreleased]

_Add entries here while developing; merge into `[x.y.z]` when you publish._

---

## [0.1.0] — 2026-05-02

### Added

- Initial **NeoForge 1.21.1** dedicated-server pack: **`manifest.json`** uses **`files`: `[]`**; third-party **`.jar`** files ship under **`overrides/mods/`** (copied from maintainer **`run-server/mods/`** at zip build time — **no CurseForge API**). **Project Island** JAR still comes from **`./gradlew jar`**.
- **Project Island** mod JAR under **`overrides/mods/`**.
- **FTB Quests** SNBT under **`overrides/config/ftbquests/`** (parity with client for quest data on the server).

### Removed / not shipped (vs client)

These **CurseForge projects** are omitted from the **client** manifest for dedicated-server use (see **`server-pack-excluded-project-ids.json`** as a checklist — your **`run-server/mods`** folder should omit the same client-only stacks):

- **JEI**, **Xaero’s Minimap**, **Xaero’s World Map**
- **Embeddium**, **GlitchCore**, **Jupiter**, **Uranus**, **NeOculus**
- **Resource Pack Overrides**

Also omitted from the zip tree: **`modlist.html`**, **`resourcepackoverrides.json`**, **`overrides/resourcepacks/`**, **`overrides/shaderpacks/`**.

### Changed

- **FTB Quests:** **Project Island** chapter rewards aligned with the client pack (no **Create**-only item ids).

### Troubleshooting

- **“Channel … projectisland … missing on the server” when joining:** the server **`mods/`** folder must include **`projectisland-0.1.0.jar`** (same version as the client). The CurseForge **server** zip places **Project Island** and dependencies under **`overrides/mods/`** — merge **`overrides`** into the instance (or copy those JARs into **`mods/`**). Re-download the **server** additional file for **0.1.0** or rebuild with **`./gradlew curseforgeServerPackZip`** after populating **`run-server/mods/`**.

### Notes

- Install with the same **Minecraft** / **NeoForge** build as the matching **client** release.
