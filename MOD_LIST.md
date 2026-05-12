# Project Island — mod list (synced to `run-server/mods`)

This file lists **every third-party JAR** currently in **`run-server/mods/`** for this checkout (NeoForge **1.21.1**). It is the **authoritative inventory** for matching **dedicated server** and **client** installs: copy the same set into **`run-client/mods/`** unless you intentionally omit client-only stacks.

- **CurseForge modpack:** [`modpack/curseforge/`](modpack/curseforge/README.md) — `manifest.json` + `overrides/`; run **`./gradlew curseforgeModpackZip`** for **`build/dist/projectisland-modpack-*-curseforge.zip`**. The **server** zip (**`curseforgeServerPackZip`**) copies JARs from **`run-server/mods/`** into **`overrides/mods/`** (no API key). Extend the client **`manifest.json`** or drop extra JARs under **`overrides/mods/`** until this list is fully covered (see **`modpack/curseforge/overrides/mods/README-SUPPLEMENT.md`**).
- **Loader / game version:** see [README.md](README.md) — *Pinned toolchain*.
- **Project Island** itself: `build/libs/projectisland-<version>.jar` from `./gradlew build` (Gradle dev runs often inject the mod from sources instead of dropping a copy in `run-*/mods`).
- **Spawn tuning:** natural overworld thinning (`floatingIslandsSpawnTuning*`) **does not** apply to entity namespaces in **`floatingIslandsSpawnTuningBypassEntityNamespaces`**. Defaults include **`mowziesmobs`** and **`cnb`** (Creatures and Beasts: Continued) so those mobs are not rolled like vanilla zombies; tune in **`projectisland-common.toml`** if you want different behavior. **Existing configs** keep whatever list was already saved — add **`cnb`** there manually if you do not regenerate common config.

## Third-party JARs (exact filenames)

Sorted by filename. **Not** listed here: anything absent from `run-server/mods/` (e.g. **Create**, **ProgressiveStages** were removed from this manifest because they are not in the folder right now — add the JARs back and extend this table when you reinstall them).

| JAR (exact) | Role | Side |
|-------------|------|------|
| `accessories-neoforge-1.1.0-beta.53+1.21.1.jar` | **[Accessories](https://modrinth.com/mod/accessories)** — trinket-style equipment API (RPG stack). | **Client + server** |
| `archers-neoforge-2.7.0+1.21.1.jar` | **[Archers (RPG Series)](https://modrinth.com/mod/archers)** | **Client + server** |
| `architectury-13.0.8-neoforge.jar` | **[Architectury](https://modrinth.com/mod/architectury-api)** — shared API for mods that depend on it. | **Client + server** |
| `azurelibarmor-neo-1.21.1-3.1.3.jar` | **[AzureLib Armor](https://modrinth.com/mod/azurelib-armor)** | **Client + server** |
| `balm-neoforge-1.21.1-21.0.57.jar` | **Balm** — shared config / utility library (often pulled by world / QoL mods). [CurseForge modpack](modpack/curseforge/manifest.json) pins **[balm-neoforge-1.21.1-21.0.56.jar](https://www.curseforge.com/minecraft/mc-mods/balm)** (**`7420963`**); this checkout may carry **`.57`** from Modrinth — align or keep **`overrides/mods`** if you stay ahead of CF. | **Client + server** |
| `bettervillage-neoforge-1.21.1-3.3.1.jar` | **Better village** — mod id **`bettervillage`** (village structure overhaul; **ARR** in jar metadata — confirm on the mod page before redistributing). CurseForge modpack pins **[Better Villages - NeoForge](https://www.curseforge.com/minecraft/mc-mods/better-village-neoforge)** (**`6593297`**); do **not** use **[better-village-forge](https://www.curseforge.com/minecraft/mc-mods/better-village-forge)** **`…-all.jar`** on NeoForge **21.1+**. | **Client + server** |
| `BiomesOPlenty-neoforge-1.21.1-21.1.0.13.jar` | **[Biomes O’ Plenty](https://www.curseforge.com/minecraft/mc-mods/biomes-o-plenty)** ([Modrinth](https://modrinth.com/mod/biomes-o-plenty)) — extra overworld biomes (pairs with **TerraBlender**). **CurseForge modpack** pins **`7251965`** for this filename (`manifest.json` **220318**). | **Client + server** |
| `bundle-api-neoforge-1.1.0.jar` | **[Bundle API](https://modrinth.com/mod/bundle-api)** | **Client + server** |
| `cloth-config-15.0.140-neoforge.jar` | **Cloth Config** — config UI / API. | **Client + server** |
| `CNB-1.21.1-neoforge-1.7.8.jar` | **[Creatures and Beasts: Continued](https://www.curseforge.com/minecraft/mc-mods/creatures-and-beasts-continued)** — mod id **`cnb`**. Biome-tied creatures; on floating islands density follows **island biomes** (see BOP note below). Optional **`cnb`** in **`neoforge.mods.toml`**. CurseForge modpack pins **`8066562`** (**`1197295`**). | **Client + server** |
| `embeddium-1.0.15+mc1.21.1.jar` | **Embeddium** — performance / rendering (normally **client**; safe to omit on a headless dedicated server if you trim rendering mods). | **Usually client** |
| `FallingTree-1.21.1-1.21.1.11.jar` | **[FallingTree](https://www.curseforge.com/minecraft/mc-mods/falling-tree)** — tree-felling QoL. CurseForge modpack pins **`6835168`** (**`349559`**). | **Client + server** |
| `ferritecore-7.0.3-neoforge.jar` | **[FerriteCore ((Neo)Forge)](https://www.curseforge.com/minecraft/mc-mods/ferritecore)** — memory / object overhead reductions. CurseForge modpack pins **`7524151`** (**`429235`**). No required dependencies on CurseForge. | **Client + server** |
| `forgified-fabric-api-0.116.7+2.2.4+1.21.1.jar` | **[Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api)** | **Client + server** |
| `friendsandfoes-neoforge-4.0.25+mc1.21.1.jar` | **[Friends&Foes](https://www.curseforge.com/minecraft/mc-mods/friends-and-foes-forge)** — mod id **`friendsandfoes`**. | **Client + server** |
| `ftb-library-neoforge-2101.1.31.jar` | **FTB Library** | **Client + server** |
| `ftb-quests-neoforge-2101.1.24.jar` | **FTB Quests** | **Client + server** |
| `ftb-teams-neoforge-2101.1.10.jar` | **FTB Teams** | **Client + server** |
| `ftb-xmod-compat-neoforge-21.1.8.jar` | **FTB XMod Compat** | **Client + server** |
| `geckolib-neoforge-1.21.1-4.8.4.jar` | **[GeckoLib](https://modrinth.com/mod/geckolib)** | **Client + server** |
| `gravestone-neoforge-1.21.1-1.0.37.jar` | **[GraveStone Mod](https://www.curseforge.com/minecraft/mc-mods/gravestone-mod)** — death inventory recovery. CurseForge modpack pins **`8056307`** (**`238551`**). | **Client + server** |
| `GlitchCore-neoforge-1.21.1-2.1.0.0.jar` | **GlitchCore** — dependency for Embeddium / rendering stack. | **Client + server** *(often bundled with client perf mods)* |
| `inventoryhud.neoforged.1.21.1-3.4.28.jar` | **[Inventory HUD+](https://www.curseforge.com/minecraft/mc-mods/inventory-hud-forge)** — on-screen inventory / effects / equipment HUD. CurseForge modpack pins **`6369797`** (**`357540`**); **ARR** on the mod page. **Dev:** keep under **`run-client/mods`** only (not **`run-server/mods`**); **`sync_manifest_mods_to_dev_runs.py`** skips the server copy for **`357540`**. Omit on dedicated servers (see **`server-pack-excluded-project-ids.json`**). | **Usually client** |
| `jei-1.21.1-neoforge-19.27.0.340.jar` | **Just Enough Items** | **Client + server** |
| `jupiter-2.3.7-1.21.1-neoforge.jar` | **Jupiter** — Sodium / Embeddium ecosystem library. Pinned on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/jupiter) (**`7738312`**). | **Usually client** |
| `libraryferret-neoforge-1.21.1-4.0.0.jar` | **Library Ferret** — dependency (e.g. **Better Villages**). Pinned on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/library-ferret-neoforge) (**`6118136`**). | **Client + server** |
| `lootr-neoforge-1.21.1-1.11.37.120.jar` | **[Lootr](https://modrinth.com/mod/lootr)** — mod id **`lootr`**. CurseForge modpack pins **`8041234`** (**`361276`**). | **Client + server** |
| `modernfix-neoforge-5.27.7+mc1.21.1.jar` | **[ModernFix](https://www.curseforge.com/minecraft/mc-mods/modernfix)** — startup / memory / bugfix patches (LGPL on CurseForge). Pin **`8055632`** (**`790626`**). No extra required mods for NeoForge beyond the loader. | **Client + server** |
| `mowziesmobs-1.21.1-1.8.2.jar` | **[Mowzie’s Mobs](https://modrinth.com/mod/mowzies-mobs)** — mod id **`mowziesmobs`**. | **Client + server** |
| `neoculus-mc1.21.1-1.8.7.jar` | **Neoculus** — Iris-style shaders for NeoForge ( **client** ). Pinned on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/neoculus) (**`6787071`**). | **Client** |
| `owo-lib-neoforge-0.12.15.5-beta.1+1.21.jar` | **[oωo (owo-lib)](https://modrinth.com/mod/owo-lib)** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/owo-lib), pin **`6785734`**) | **Client + server** |
| `paladins-neoforge-2.7.1+1.21.1.jar` | **[Paladins & Priests (RPG Series)](https://modrinth.com/mod/paladins-and-priests)** | **Client + server** |
| `player-animation-lib-forge-2.0.4+1.21.1.jar` | **[Player Animator](https://modrinth.com/mod/playeranimator)** | **Client + server** |
| `puffish_skills-0.17.3-1.21-neoforge.jar` | **[Pufferfish's Skills](https://modrinth.com/mod/skills)** | **Client + server** |
| `ranged_weapon_api-neoforge-2.3.3+1.21.1.jar` | **[Ranged Weapon API](https://modrinth.com/mod/ranged-weapon-api)** | **Client + server** |
| `realmrpg_balloons-0.9.1-neoforge-1.21.1.jar` | **[Realm RPG: Treasure Balloons](https://modrinth.com/mod/realm-rpg-treasure-balloons)** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/realm-rpg-treasure-balloons), pin **`7252486`**) — mod id **`realmrpg_balloons`**. PI clamps sky spawns above islands and **relocates** void-column spawns by searching nearby columns before canceling; **`floatingIslandsRealmrpgBalloons*`** in common config. | **Client + server** |
| `resourcefullib-neoforge-1.21-3.0.12.jar` | **Resourceful Lib** — shared library used by several tech / UI mods. Pinned on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/resourceful-lib) (**`5973188`**). | **Client + server** |
| `ResourcePackOverrides-v21.1.0-1.21.1-NeoForge.jar` | **[Resource Pack Overrides](https://www.curseforge.com/minecraft/mc-mods/resource-pack-overrides)** — pack-default resource pack list via **`config/resourcepackoverrides.json`** (**client-focused**; safe on server). CurseForge modpack pins **`5733968`** (**`832644`**). | **Client** *(optional on headless server)* |
| `rogues-neoforge-2.7.0+1.21.1.jar` | **[Rogues & Warriors (RPG Series)](https://modrinth.com/mod/rogues-and-warriors)** | **Client + server** |
| `runes-neoforge-1.2.1+1.21.1.jar` | **[Runes](https://modrinth.com/mod/runes)** | **Client + server** |
| `shield_api-neoforge-2.2.0.jar` | **[Shield API](https://modrinth.com/mod/shield-api)** | **Client + server** |
| `skill_tree-neoforge-1.4.4+1.21.1.jar` | **[Skill Tree (RPG Series)](https://modrinth.com/mod/skill-tree)** — mod id **`skill_tree_rpgs`**. | **Client + server** |
| `spell_engine-neoforge-1.9.9+1.21.1.jar` | **[Spell Engine](https://modrinth.com/mod/spell-engine)** | **Client + server** |
| `spell_power-neoforge-1.4.6+1.21.1.jar` | **[Spell Power](https://modrinth.com/mod/spell-power)** | **Client + server** |
| `structure_pool_api-neoforge-1.2.1+1.21.1.jar` | **[Structure Pool API](https://modrinth.com/mod/structure-pool-api)** | **Client + server** |
| `takesapillage-neoforge-1.0.10+mc1.21.1.jar` | **[It Takes a Pillage Continuation](https://www.curseforge.com/minecraft/mc-mods/it-takes-a-pillage-continuation)** ([Modrinth](https://modrinth.com/mod/it-takes-a-pillage-continuation)) — mod id **`takesapillage`**; PI has controlled-outpost integration when loaded. CurseForge modpack pins **`7417040`** (not the legacy [**it-takes-a-pillage**](https://www.curseforge.com/minecraft/mc-mods/it-takes-a-pillage) listing). | **Client + server** |
| `TerraBlender-neoforge-1.21.1-4.1.0.8 (1).jar` | **[TerraBlender](https://modrinth.com/mod/terrablender)** — biome layout for **Biomes O’ Plenty**. Prefer renaming to drop the **` (1)`** duplicate suffix when you next tidy `mods/`. | **Client + server** |
| `travelersbackpack-neoforge-1.21.1-10.1.35.jar` | **[Traveler's Backpack](https://www.curseforge.com/minecraft/mc-mods/travelers-backpack)** — upgradeable backpacks (optional **Curios** / **Accessories** on CurseForge relations). CurseForge modpack pins **`8040184`** (**`321117`**). | **Client + server** |
| `uranus-2.4.1-1.21.1-neoforge.jar` | **Uranus** — Sodium / Embeddium companion library. Pinned on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/uranus) (**`8008516`**). | **Usually client** |
| `villager-guard-autonomy.jar` | **Mob Conversion** — mod id **`mobconversion`** (villager threat / conversion behavior; pairs with Villager Guards in this pack). | **Client + server** |
| `villager-guards-v1.1.5-1.21.1.jar` | **[Villager Guards](https://modrinth.com/mod/villager-guards)** — mod id **`mr_villager_guards`** **v1.1.5** (NeoForge **1.21.1** filename). | **Client + server** |
| `waystones-neoforge-1.21.1-21.1.32.jar` | **[Waystones](https://modrinth.com/mod/waystones)** — mod id **`waystones`**; PI syncs island HUD after activation when loaded. CurseForge modpack pins **`8056467`** (**`245755`**); server zip omits this JAR (see **`build.gradle`** / **`overrides/SERVER_README.md`**). | **Client + server** |
| `Wings Of Fire V1.0 - NeoForge 1.21.1.jar` | **[Wings Of Fire!](https://www.curseforge.com/minecraft/mc-mods/the-wings-of-fire)** (CurseForge) · [Modrinth](https://modrinth.com/mod/wings-of-fire!) — mod id **`wings_of_fire`** (phoenix mounts / PI loot bridge). Uses slug **`the-wings-of-fire`**, not the unrelated [**wings-of-fire**](https://www.curseforge.com/minecraft/mc-mods/wings-of-fire) project. Pinned in **`modpack/curseforge/manifest.json`**. | **Client + server** |
| `wizards-neoforge-2.7.1+1.21.1.jar` | **[Wizards (RPG Series)](https://modrinth.com/mod/wizards)** | **Client + server** |
| `xaerominimap-neoforge-1.21.1-25.3.13.jar` | **Xaero’s Minimap** — CurseForge modpack pins **`8046966`** (**`263420`**). | **Client + server** |
| `xaeroworldmap-neoforge-1.21.1-1.40.16.jar` | **Xaero’s World Map** — CurseForge modpack pins **`8042208`** (**`317780`**). | **Client + server** |
| `yet_another_config_lib_v3-3.8.2+1.21.1-neoforge.jar` | **YetAnotherConfigLib (YACL)** — config UI library. | **Client + server** |

### RPG stack bumps

Update **Skill Tree** first, then re-resolve **required** Modrinth versions for sibling mods (**Spell Engine**, **Pufferfish's Skills**, class mods) so **1.21.1** + NeoForge stay aligned.

### Mowzie’s Mobs + Biomes O’ Plenty + **`cnb`**

Project Island **skips spawn thinning** for **`mowziesmobs`** and **`cnb`** by default (`floatingIslandsSpawnTuningBypassEntityNamespaces`). **Which biomes** mobs may **naturally** use is still defined by **each mod’s** spawn rules (often vanilla biome tags). Island tops that are mostly **`biomesoplenty:*`** can still look sparse for some mobs — that is **upstream** spawn data, not PI worldgen. Mitigate with per-mod configs or datapacks that extend biome tags (verify in each mod’s wiki or `data/` before shipping).

### Not in this `mods/` folder (by design or removed)

**[Create](https://modrinth.com/mod/create)** and **ProgressiveStages** are **not** in the current `run-server/mods` listing; they are omitted from the table above. **[Create Aeronautics](https://modrinth.com/mod/create-aeronautics)** / **[Valkyrien Skies](https://modrinth.com/mod/valkyrien-skies)** remain **out of scope** for the official pack direction — do not add unless you intentionally change that policy.

### Wings Of Fire + Lootr (unchanged behavior)

WoF adds global loot to many vanilla structure tables; Project Island can add **extra** egg rolls to island-common chests when **`wings_of_fire`** is loaded (see README). **Lootr** per-player chests — tune in **`config/lootr-common.toml`**.

## Optional / not in this list

- **Shader packs, HD resource packs** — player-installed; see [README.md](README.md) *Resource packs*. Dev trees may keep shaders under **`run-client/shaderpacks/`** (e.g. **Complementary Reimagined**); redistribution in a CurseForge pack requires checking the shader license — see [`modpack/curseforge/overrides/shaderpacks/README.md`](modpack/curseforge/overrides/shaderpacks/README.md).
- **Extra QoL** — add JARs to **`run-client/mods`** / **`run-server/mods`** **and** a row in the table above when they become part of the tracked set.

## Progression data (not mods)

Official pack progression still expects mirrored config from **`examples/dev-progression/`** (FTB Quests chapters, ProgressiveStages **when you use that mod**, etc.). If **ProgressiveStages** is absent from `mods/`, align quest copy and server config with what you actually ship.

## Updating this list

1. Change **`run-server/mods/`** (and keep **`run-client/mods/`** in sync).
2. Run **`ls run-server/mods`** and refresh **this file** so the table matches disk.
3. Note intentional pack changes in [CHANGELOG.md](CHANGELOG.md).

## License reminder

Third-party mods keep **their own licenses**. Most third-party JARs are **not** committed here (only **filenames** for parity); exceptions (**Traveler's Backpack**, **GraveStone Mod**, **FallingTree**) are vendored under **`modpack/curseforge/overrides/mods/`** so the **CurseForge server pack** zip always ships matching **`overrides/mods/`**—see **`build.gradle`** **`curseforgeServerPackModsBundledInRepoOverrides`** and **`modpack/curseforge/overrides/mods/README-SUPPLEMENT.md`**.

## FTB Library: pink “Hello from FTB Library!” in chat

Same as before: FTB Library sends **“Hello from FTB Library!”** when it believes the run is a **development** environment (`./gradlew runServer` / `runClient`). Production installs usually will not show it. Not controlled by `ftblibrary-server.snbt`.
