# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Documentation pass: **2026-04-22**._ _Phase 2 overworld + pregen: **2026-04-24**._

### Changed

- **Phase 2 (world entry):** built-in datapack replaces **`minecraft:overworld`** terrain with **`FloatingIslandsChunkGenerator`** and **`minecraft:multi_noise`** preset **`minecraft:overworld`** (vanilla Nether/End unchanged). Removed the extra **`projectisland:floating_islands` dimension** JSON so island **`FloatingIslandSavedData`** is not split across two `ServerLevel`s.
- **Spawn safety:** `FloatingIslandsSpawnEvents` now also runs on **player login** and when **changing dimension to overworld** (nearest-island spiral search unchanged). **`FloatingIslandsSpawnPregen`** optionally loads a Chebyshev chunk neighborhood around overworld spawn (`spawnPregenChunkRadius`, default `0` = off; `spawnPregenChunksPerTick`).
- **Dimension checks:** server gameplay uses **`ProjectIslandDimensions.isFloatingIslandsGameplay(ServerLevel)`** (chunk generator type). **`ProjectIslandDimensions.FLOATING_ISLANDS`** is **`Level.OVERWORLD`**.
- **Client island HUD:** renderer gates on **`minecraft:overworld`** (client `ChunkCache` has no `getGenerator()` in 1.21.1); the server only syncs HUD payloads when the overworld generator matches.

### Added

- **Island HUD (floating islands dimension):** server builds nearby-island labels from `FloatingIslandSavedData` + `FloatingIslandLayout` (anchor uses `peakSurfaceYAtIslandCenter` + configurable height), syncs via `IslandHudSyncPayload`, client cache + `IslandHudRenderer` (`DebugRenderer.renderFloatingText`, see-through option, night color boost). **Common** config: `islandHudSyncEnabled`, `islandHudSyncIntervalTicks`, `islandHudRegionScanRadius`, `islandHudHeightAbovePeakBlocks`. **Client** config: `islandHudShow`, `islandHudTextScale`, `islandHudSeeThroughText`, `islandHudNightColorBoost` (`config/projectisland-common.toml` / `config/projectisland-client.toml`).
- **Island HUD v2:** deterministic procedural names (`FloatingIslandDisplayName`), extended beacon payload (title / status / grid id / colors / state), client `IslandHudWorldBillboard` — translucent panel + border (`RenderType.debugQuads`), **custom 64×64 PNG icons** (`assets/projectisland/textures/gui/island_hud/floating-island.png` claimed, available alternates with `floating-island_ex.png`), contested still uses vanilla torch item; larger default `islandHudTextScale`.
- **Island HUD billboard:** wider Z separation between border, fills, icon, and text to reduce z-fighting while moving; icon uses `RenderType.entityCutoutNoCullZOffset` and **pose-space normals** on the textured quad for more stable tinting.
- **Island HUD distance:** client culls each beacon unless its chunk is loaded and the anchor is within ~90% of `renderDistance × 16` blocks from the camera (avoids orphan tags past island mesh range).
- **Phase 3 (initial):** `FloatingIslandKey` + `FloatingIslandLayout` (shared math with chunk generator), `FloatingIslandSavedData` / `IslandRecord` / `IslandState`, `IslandWorld` helpers, OP command `/projectisland island here`. README “Phase 3” subsection documents IDs and save file.
- **Floating islands tuning:** common config `floatingIslandsRareStructureKeepChance` (thins `minecraft:monster_room` and `minecraft:trial_chambers` after worldgen) and `floatingIslandsExtraSurfaceTreesPerChunk` (extra oak/fancy oak/birch on grass tops after `applyBiomeDecoration`). README documents both.
- **Bundled client resource pack (legal to redistribute):** [Unshaded Blocks](https://modrinth.com/resourcepack/unshaded-blocks) v2.1 (CC0) as unpacked `resourcepacks/bundled_unshaded_blocks/` (NeoForge `addPackFinders` uses `PathPackResources`, which does not load a `.zip` root). Registered via `AddPackFindersEvent` in `ProjectIslandClient`. Attribution in [`licenses/UNSHADED_BLOCKS_ATTRIBUTION.txt`](licenses/UNSHADED_BLOCKS_ATTRIBUTION.txt). (New Default+ and similar ARR packs cannot be vendored inside the JAR.)
- **Safe entry into floating islands:** `FloatingIslandsSpawnEvents` listens for `PlayerChangedDimensionEvent` into `projectisland:floating_islands` and teleports the player to the **nearest island surface** (spiral search from arrival XZ, multi-sample per chunk). `FloatingIslandsChunkGenerator.islandSurfaceBlockY` exposes the same top Y logic used by worldgen.
- `FloatingIslandsChunkGenerator`: **structures + trim** — call `super.createStructures`, then remove structure blocks in void columns or above the island surface (`trimFloatingStructureBlocks`) so mineshafts / ruined portals hug terrain instead of hanging in empty sky.
- `FloatingIslandsChunkGenerator`: **smoother tops** (remove blocky `topRelief` / stepped `hrWobble`; smooth sin wobble + shared `verticalHill`; flatter plateau via `TOP_HORIZ_POWER`).
- `FloatingIslandsChunkGenerator`: **less spherical** islands (asymmetric top/bottom ellipsoids, rim-weighted underside, horizontal silhouette wobble) and **wider spacing** (8-chunk regions, lower spawn rate, centers biased inside each cell).
- README: **dedicated server OP / cheats** notes for `/execute` (Brigadier “unknown command” on `/` when not OP); example [`dev-ops.example.json`](dev-ops.example.json) for offline player `Dev`.
- **Phase 2 (prototype):** `projectisland:floating_islands` dimension datapack and `FloatingIslandsChunkGenerator` (void + ellipsoid islands, temperature-based tops). Chunk generator codec registered on the mod bus (`ProjectIslandWorldgen`).
- NeoForge **ModDevGradle** project for **Minecraft 1.21.1** / **NeoForge 21.1.227**: `build.gradle`, Gradle wrapper, `net.projectisland` entrypoint, generated `neoforge.mods.toml` from templates, GitHub Actions build workflow.
- Initial project documentation: `README.md`, `TODO.md`, and this changelog.
- Cursor project rule `.cursor/rules/project-island.mdc` for consistent AI and contributor context.
- README: **game pillars**, **propulsion tier table** (sails / propellers / jets), **tech tree** intent, architecture diagram nodes for unlocks and propulsion, and **Learning from existing work** (mods, datapacks, modpacks, GitHub; licenses and pinning).
- TODO: **Phase 6** (propulsion, advancement tree, parallel tech tracks, fuel), clarified Phase 5 travel vs **airship** core loop, renumbered later phases; pointer to use online examples when researching.
- Cursor rule: airship capture loop, **server-side unlocks**, explicit encouragement to reuse **mods / datapacks / modpacks / GitHub** with license awareness.
