# Changelog — Project Island modpack (server)

Host-facing notes for **`projectisland-modpack-<version>-curseforge-server.zip`** (upload as **Additional file** on the same CurseForge release). Build: **`./gradlew curseforgeServerPackZip`** — copies third-party JARs from **`run-server/mods/`** (see [`README.md`](../../README.md) and [`modpack/curseforge/README.md`](README.md)).

**Client pack changelog:** [`CHANGELOG_CLIENT.md`](CHANGELOG_CLIENT.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). **`manifest.json`** **`version`** is written at build time from **`project.version`** (same base as **`gradle.properties`** **`mod_version`**). Server **`manifest.json`** uses **`name`:** **`Project Island (Server)`** and **`files`: `[]`**; **Project Island** ships as **`overrides/mods/projectisland-*.jar`** from **`./gradlew jar`**, and third-party mods ship under **`overrides/mods/`** too.

---

## [1.3.0] — 2026-05-12

### Changed

- **Pack version** **`1.3.0`**; server zip still ships **`overrides/mods/projectisland-1.3.0.jar`** from **`./gradlew jar`** plus vendored third-party JARs under **`overrides/mods/`**. Client **`manifest.json`** pins the same mod release as **`fileID` `8090568`** on CurseForge **Mods**.
- **Xaero’s Minimap / World Map:** same **`overrides/config/xaerominimap-common.txt`** and **`xaeroworldmap-common.txt`** defaults as the client pack (merge **`overrides/`** into the server instance so **`config/`** picks them up on first run).

## [0.1.2] — 2026-05-11

### Added

- **Curios API** on the client manifest (**NeoForge 1.21.1** pin **`6529130`**) — **required on the dedicated server** for **Traveler's Backpack**’s **Curios back** slot (slot data is **server-authoritative**). The **server pack** zip now **always** ships **`curios-neoforge-9.5.1+1.21.1.jar`** from **`modpack/curseforge/overrides/mods/`** (same **vendored** pattern as Traveler's Backpack / GraveStone / FallingTree; **`curseforgeServerPackModsBundledInRepoOverrides`** in **`build.gradle`**).
- **Inventory HUD+** on the client manifest (optional on headless servers — **`server-pack-excluded-project-ids.json`** **`357540`**).
- **Traveler's Backpack**, **GraveStone Mod**, and **FallingTree:** manifest pins plus **committed** JARs under **`modpack/curseforge/overrides/mods/`** so **`curseforgeServerPackZip`** always places them in **`overrides/mods/`**; Gradle skips re-staging those filenames from **`run-server/mods`** and omits them from **`curseforgeModpackZip`** to avoid duplicate installs.
- **`overrides/SERVER_README.md`** in the server zip: lists JARs omitted from **`overrides/mods/`** (see **`curseforgeServerPackUndistributableModJars`**) for manual host install. **ModernFix** and **FerriteCore** are included when built from a populated **`run-server/mods/`** (same as other redistributable mods).
- Same **FTB Quests** SNBT as the client pack; **`curseforgeServerPackZip`** depends on **`verifyDevProgressionFtbQuests`** so a missing **`examples/dev-progression/ftbquests/`** tree fails the build.
- **Floating islands grow pointy "stalactite root" undersides** (default **on**). Worldgen-only change shipped by the **Project Island** mod (**`projectisland-0.1.2.jar`**); chunks generated before this release keep their old smooth bottom. To revert or tune, edit **`floatingIslandBottomSpike*`** keys in **`config/projectisland-common.toml`** (most importantly **`floatingIslandBottomSpikesEnabled = false`** for the legacy look).

### Changed

- **Client vs server Project Island delivery:** **`curseforgeModpackZip`** (client) keeps **Project Island** as a **`manifest.json`** pin only (**no** **`overrides/mods/projectisland-*.jar`** — CurseForge moderation on the **primary** file). **`curseforgeServerPackZip`** embeds **`./gradlew jar`** as **`overrides/mods/projectisland-*.jar`** and sets server **`manifest.json`** **`files`** to **`[]`** so hosts get a self-contained fat **`mods`** tree without duplicating a manifest download.
- **CurseForge manifest pins** aligned with latest **1.21.1** + **NeoForge** file uploads where newer JARs exist (Waystones, Xaero map mods, Lootr, Uranus, CNB); **`MOD_LIST.md`**, **`build.gradle`** (Waystones omit filename), and **`SERVER_README.md`** updated.
- **`overrides/config/fml.toml`** (**`versionCheck = false`**) and maintainer workflow **`syncManifestModJarsToDevRuns`** / **`sync_manifest_mods_to_dev_runs.py`** for dev **`run-*/mods`** parity with **`manifest.json`**.
- **`curseforgeServerPackZip`** omits certain third-party **`.jar`** filenames from **`overrides/mods/`** (not redistributable in our server zip); they are still expected under **`run-server/mods/`** for local dev but hosts must obtain those mods separately. Filenames are listed in root **`build.gradle`** (`curseforgeServerPackUndistributableModJars`).

### Fixed

- **FTB Quests** chapter fixes (WoF lantern ids, **Ride the Zipline** rewards for **1.21.1**) — see client **[`CHANGELOG_CLIENT.md`](CHANGELOG_CLIENT.md)**.

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
