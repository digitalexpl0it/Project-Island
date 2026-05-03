# Changelog — Project Island modpack (server)

Host-facing notes for **`projectisland-modpack-<version>-curseforge-server.zip`** (upload as **Additional file** on the same CurseForge release). Build: `./gradlew curseforgeServerPackZip`.

**Client pack changelog:** [`CHANGELOG_CLIENT.md`](CHANGELOG_CLIENT.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions match **`gradle.properties`** `mod_version` (manifest inside the zip uses **`name`: `Project Island (Server)`**).

---

## [Unreleased]

_Add entries here while developing; copy the relevant bullets into the CurseForge **server** additional file changelog when you publish._

---

## [0.1.0] — 2026-05-03

### Added

- Initial **NeoForge 1.21.1** dedicated-server pack: trimmed **`manifest.json`** (same gameplay stack as the client minus client-only mods — see below).
- **Project Island** mod JAR under **`overrides/mods/`**.
- **FTB Quests** SNBT under **`overrides/config/ftbquests/`** (parity with client for quest data on the server).

### Removed / not shipped (vs client)

These **CurseForge projects** are omitted from the server **`manifest.json`** (see **`server-pack-excluded-project-ids.json`**):

- **JEI**, **Xaero’s Minimap**, **Xaero’s World Map**
- **Embeddium**, **GlitchCore**, **Jupiter**, **Uranus**, **NeOculus**
- **Resource Pack Overrides**

Also omitted from the zip tree: **`modlist.html`**, **`resourcepackoverrides.json`**, **`overrides/resourcepacks/`**, **`overrides/shaderpacks/`**.

### Notes

- Install with the same **Minecraft** / **NeoForge** build as the matching **client** release.
