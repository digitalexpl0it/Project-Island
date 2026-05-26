# CurseForge — pack upload notes (copy / paste)

Use this when you publish a new **Project Island modpack** release on CurseForge: paste the **Client** section into the **primary** file description (or project update), and the **Server** section into the **additional server** file description if you want hosts to see a focused summary.

**Pack version:** **1.4.2** · **Minecraft** **1.21.1** · **NeoForge** **21.1.227** (see `gradle.properties` and `manifest.json`).

**Maintainers:** After changing `modpack/curseforge/manifest.json`, run `python3 tools/curseforge_pack/bump_manifest_latest_neoforge.py --manifest modpack/curseforge/manifest.json` (dry-run) or `--apply`, then `./gradlew syncManifestModJarsToDevRuns`, update **`MOD_LIST.md`**, edit **this file** with player-facing bullets, and rebuild `./gradlew curseforgeModpackAll`.

---

## Client pack (`projectisland-modpack-*-curseforge.zip`)

**What players get:** the CurseForge / Prism / etc. launcher installs every mod from **`manifest.json`** (exact **`fileID`** per mod) and merges **`overrides/`** (configs, FTB Quests SNBT, optional resource packs, shader pack if shipped, etc.). The **Project Island** mod itself is **only** a manifest pin (CurseForge project **1534225**) — there is **no** duplicate `projectisland-*.jar` under `overrides/mods/` on the client zip.

**This release — mod pins**

- **Removed:** **Create: Levite Fields**, **Create Aeronautics**, **Sable**, **Biolith** (Levite test stack; **Project Island** still supports **`levmod`** when added manually).
- **Project Island** mod: upload **`projectisland-1.4.2.jar`** (or **`1.4.1`+** with Levite worldgen) and bump manifest **`fileID`** on project **1534225** when published.

**This release — pack content (high level)**

- **Modpack `1.4.0`:** default-on **[Dramatic Skys](https://www.curseforge.com/minecraft/texture-packs/dramatic-skys)** and **[Mandala's GUI - Dark mode](https://www.curseforge.com/minecraft/texture-packs/mandalas-gui-dark-mode)** on the client **`manifest.json`**; **Resource Pack Overrides** enables them with **`Project_Island_menu_assets.zip`** on first install (not on the server additional file).
- Carries forward **1.3.0** modpack content (inventory HUD defaults, Xaero defaults, FTB quest fixes, dragon respawn, etc.) — see **`CHANGELOG_CLIENT.md`**.
- **Manifest refresh:** all other mods were already on the **latest** CurseForge file matching **Minecraft 1.21.1** + **NeoForge** at the time `bump_manifest_latest_neoforge.py --apply` was last run (only **Treasure Balloons** needed a newer pin in that pass).

---

## Server pack (`projectisland-modpack-*-curseforge-server.zip`)

**What hosts get:** a **fat** `mods` tree under **`overrides/mods/`** (third-party JARs copied from your **`run-server/mods/`** build, plus **`projectisland-<version>.jar`** from **`./gradlew jar`**). Server **`manifest.json`** uses **`files`: `[]`** so nothing is double-downloaded from the manifest.

**Not in this zip (install separately):** see **`overrides/SERVER_README.md`** inside the zip — filenames match **`curseforgeServerPackUndistributableModJars`** in root **`build.gradle`** (e.g. some mods we do not redistribute in the server archive).

**Client-only folders omitted:** e.g. **`overrides/shaderpacks/`**, **`overrides/resourcepacks/`**, **`resourcepackoverrides.json`** — dedicated servers do not need those paths.

**This release — same mod bumps as client**

- **Treasure Balloons** and **Project Island** JAR versions match the client manifest / built server tree above.

---

## Next release checklist

1. Bump **`mod_version`** in **`gradle.properties`** (and ship matching **`projectisland-*.jar`** on the **Mods** project if the mod API changed).
2. **`python3 tools/curseforge_pack/bump_manifest_latest_neoforge.py --manifest modpack/curseforge/manifest.json --apply`**
3. **`./gradlew syncManifestModJarsToDevRuns`**
4. Update **`MOD_LIST.md`** if any downloaded **filename** changed.
5. Rewrite the **Client** / **Server** bullet lists in **this file** for what actually changed.
6. Mirror anything player-facing into **`CHANGELOG_CLIENT.md`** / **`CHANGELOG_SERVER.md`** as needed.
7. **`./gradlew curseforgeModpackAll`**
