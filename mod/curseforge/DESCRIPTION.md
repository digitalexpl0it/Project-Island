# Project Island

> **NeoForge 1.21.1** — **Floating-island world generator** for a **void overworld** of procedural islands. Island rules and sync are **server-driven**.

---

## At a glance

| | |
|---|---|
| **Minecraft** | **1.21.1** |
| **Loader** | **NeoForge** (match the file you download) |
| **Sides** | **Client + dedicated server** — use the **same** JAR everywhere |

---

## What you get

**Project Island** replaces the usual overworld with **floating islands** over the void: each island is procedural terrain you can explore, build on, and wire together with **rope** systems. World layout, starters, links, and HUD data are handled **authoritatively on the server** so multiplayer stays consistent.

---

## Features

- **Void sky overworld** — islands as the main survival surface; void below.
- **Sculpted island silhouettes** — each procedural island grows a small set of **stalactite root** spikes under its rounded body for a more dramatic skyline; one toggle (**`floatingIslandBottomSpikesEnabled`**) restores the legacy smooth bottom.
- **Harpoon + rope anchors** — link anchors, manage span and rope health, **rope surfing**, **public** zipline-style links between anchors (no per-player “claim” layer in current design).
- **Island HUD** — server-synced hints for navigation; smoother play when [Waystones](https://www.curseforge.com/minecraft/mc-mods/waystones) or [Xaero’s Minimap](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap) are installed (optional).
- **Tuning** — hundreds of knobs live in **`projectisland-common.toml`** and **`projectisland-client.toml`**; see **Configuration** for what you can change without digging blindly.
- **Optional mod hooks** — extra behavior when you install other mods yourself. **None** of these are bundled inside the Project Island JAR; grab compatible **1.21.1 / NeoForge** builds from their own pages:
  - [Lootr](https://www.curseforge.com/minecraft/mc-mods/lootr)
  - [Wings Of Fire!](https://www.curseforge.com/minecraft/mc-mods/the-wings-of-fire) (not the unrelated `wings-of-fire` slug on CurseForge)
  - [Realm RPG: Treasure Balloons](https://www.curseforge.com/minecraft/mc-mods/realm-rpg-treasure-balloons)
  - [Friends&Foes](https://www.curseforge.com/minecraft/mc-mods/friends-and-foes-forge)
  - [Creatures and Beasts: Continued](https://www.curseforge.com/minecraft/mc-mods/creatures-and-beasts-continued)
  - **RPG-style stack (examples):** [Skill Tree](https://www.curseforge.com/minecraft/mc-mods/skill-tree), [Spell Engine](https://www.curseforge.com/minecraft/mc-mods/spell-engine), [Spell Power Attributes](https://www.curseforge.com/minecraft/mc-mods/spell-power), [Pufferfish's Skills](https://www.curseforge.com/minecraft/mc-mods/puffish-skills), [Archers](https://www.curseforge.com/minecraft/mc-mods/archers), [Paladins & Priests](https://www.curseforge.com/minecraft/mc-mods/paladins-and-priests), [Rogues & Warriors](https://www.curseforge.com/minecraft/mc-mods/rogues-and-warriors), [Wizards](https://www.curseforge.com/minecraft/mc-mods/wizards)
- **Built-in look (client)** — **Unshaded Blocks** (flat block shading, CC0) ships inside the jar as a **resource pack** entry; enable it under **Options → Resource packs** if you want that style.

---

## Configuration

After the first run you get:

| File | Who reads it |
|------|----------------|
| **`config/projectisland-common.toml`** | **Server** (including integrated single-player). Worldgen, spawn, ropes, rescue, HUD **sync rates**, starters, compat. |
| **`config/projectisland-client.toml`** | **Client only**. How the HUD and ropes **draw** on your machine; ignored on a headless dedicated server. |

Every option has a **comment in the TOML** next to the key. Use the tables below as a **map**; names are stable across releases unless a changelog calls out a rename.

### `projectisland-common.toml` — topic map

| Topic | What you can adjust (representative keys) |
|-------|---------------------------------------------|
| **Structures & settlements** | Per-region weights for dungeons, trials, pyramids, mineshafts (`islandRegionRareStructureWeight*`), controlled villages/outposts (`floatingIslandsControlledSettlementPlacement`, `controlledSettlementWeight*`, `controlledSettlementPlaceTryChance`, anchor jitter/tries), snapping rare structures to island columns (`floatingIslandsSnapRareStructuresToIslandColumn`, `floatingIslandsRareStructurePlacementMode`, …), **It Takes a Pillage** outpost branch (`floatingIslandsTakesapillageControlledOutpost`), `/locate` search cap (`floatingIslandsLocateStructureMaxRingRadius`). |
| **Carving & caves** | Masked overworld carvers inside islands (`floatingIslandsEnableMaskedOverworldCarvers`, `floatingIslandsMaskedCarverNeighborChunkRadius`, …). |
| **Island shape & biomes** | How often an 8×8 region rolls an island (`floatingIslandRegionSpawnChance`), vanilla-style **surface biome** weights (`islandBiomeWeightPlains`, `islandBiomeWeightRiver`, …), **mod biome** surfaces such as [Biomes O’ Plenty](https://www.curseforge.com/minecraft/mc-mods/biomes-o-plenty) (`islandBiomeModIntegrationEnabled`, `islandBiomeModDiscoverAllRegistered`, weighted id lines), plateau width (`floatingIslandHorizontalRadiusBonus`, `floatingIslandHorizontalRadiusVillageExtraBlocks`, …), generator sea level (`floatingIslandsChunkGeneratorSeaLevel`). |
| **Decoration & resources** | Extra surface trees, snow trees, water pools, stripping exterior fluids, stronghold/mineshaft overlap rules, **ore vein density multipliers** (`floatingIslandsOreMultiplierCoal` through `floatingIslandsOreMultiplierEmerald`). |
| **Mob spawning** | Global thinning toggle (`floatingIslandsSpawnTuningEnabled`), keep chances for monsters/illagers/creepers/creatures/ambient/villagers, creature multiplier, **daytime passive spawn boost** on island tops (`floatingIslandsDaytimeCreatureSpawnBoost*`), **pack spawn boost** (`floatingIslandsPackSpawnBoost*`), namespaces that **skip** thinning (`floatingIslandsSpawnTuningBypassEntityNamespaces`). |
| **Optional third-party compat** | e.g. **Realm RPG: Treasure Balloons** spawn fix when that mod is present (`floatingIslandsRealmrpgBalloons*`). |
| **Island HUD (server)** | Send HUD/beacon updates (`islandHudSyncEnabled`, `islandHudSyncIntervalTicks`, `islandHudRegionScanRadius`, `islandHudHeightAbovePeakBlocks`, …), Waystone-driven titles (`islandHudWaystoneTitleWhenLoaded`, …). |
| **Starter placement** | Auto-assign, shared hub vs split, search origin and radius, min region separation, kick message (`starterIsland*`). |
| **Void rescue** | Rescue tick mode, trigger depth, snap-back-to-last-safe (`voidRescue*`). |
| **Ropes & harpoon** | Shot reach vs max span (`ropeLinkRaycastRangeBlocks`, `ropeLinkMaxLengthBlocks`), rope HP, strain/stress damage, anchor mining vs link HP, server→client rope packets (`ropeLinkSync*`), tier upgrades (`ropeProgressionUpgradeExistingLinks`, …), topology caps for how graphs may grow (`ropeTopology*`, `ropeMainDirectSpokeCap`, `ropeSisterOutboundCap`, …), **rope surfing** speed/cooldown/duration (`ropeTraversalSurf*`). |
| **Diagnostics** | `debugLogging` plus a few subsystem debug flags (e.g. masked carver logging). |

### `projectisland-client.toml` — topic map

| Topic | What you can adjust (representative keys) |
|-------|---------------------------------------------|
| **Island HUD appearance** | Show world labels (`islandHudShow`), scale, see-through text, night color boost, title color mode (`islandHudTitleColorMode`), panel opacity/outline, void “navigation” multi-label mode (`islandHudWorldBillboardVoidNavigation`). |
| **Xaero mirrors** | Mirror HUD beacons to waypoints (`islandHudXaeroWaypointSync`), temporary gray pins vs persistent gold after waystone use (`islandHudXaeroWaypointTemporary`), color enum names (`islandHudXaeroWaypointColorDefault`, `islandHudXaeroWaypointColorHit`). |
| **Rope rendering** | Draw rope segments and anchor health bars (`ropeLinksShow`, `ropeLinkHealthBarsShow`). |

---

## Installation

1. Install **NeoForge** for **Minecraft 1.21.1** (same build band as the JAR release).
2. Drop **`projectisland-<version>.jar`** into the **`mods`** folder on **every client** and on the **dedicated server**.
3. **Version match matters** — mismatched mod or game versions are the most common cause of disconnect or “missing channel” errors.

---

## License

- **Project Island** mod code: **MIT**
- **Unshaded Blocks** (bundled resource pack assets): **CC0**
