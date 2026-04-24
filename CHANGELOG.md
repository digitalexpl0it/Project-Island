# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Documentation pass: **2026-04-22**._ _Phase 2 overworld + pregen: **2026-04-24**._ _README/TODO sync: **2026-04-25**._

### Changed

- **Phase 2 (world entry):** built-in datapack replaces **`minecraft:overworld`** terrain with **`FloatingIslandsChunkGenerator`** and **`minecraft:multi_noise`** preset **`minecraft:overworld`** (vanilla Nether/End unchanged). Removed the extra **`projectisland:floating_islands` dimension** JSON so island **`FloatingIslandSavedData`** is not split across two `ServerLevel`s.
- **Spawn safety:** `FloatingIslandsSpawnEvents` now also runs on **player login** and when **changing dimension to overworld** (nearest-island spiral search unchanged). **`FloatingIslandsSpawnPregen`** optionally loads a Chebyshev chunk neighborhood around overworld spawn (`spawnPregenChunkRadius`, default `0` = off; `spawnPregenChunksPerTick`).
- **Dimension checks:** server gameplay uses **`ProjectIslandDimensions.isFloatingIslandsGameplay(ServerLevel)`** — overworld plus **`FloatingIslandsChunkGenerator`** (`instanceof`) or a **shallow unwrap** of a delegate `ChunkGenerator` field when present. **`ProjectIslandDimensions.FLOATING_ISLANDS`** is **`Level.OVERWORLD`**.
- **Client island HUD:** renderer gates on **`minecraft:overworld`** (client `ChunkCache` has no `getGenerator()` in 1.21.1); the server only syncs HUD payloads when the overworld generator matches.
- **Island HUD reliability (2026-04-24):** server sync runs on **`ServerTickEvent.Post`** (per-player interval) instead of **`PlayerTickEvent.Post`**; **`isFloatingIslandsGameplay`** also unwraps a shallow **delegate `ChunkGenerator`** when present. Client relaxes **`hasChunk`** culling when the beacon column is still within a few chunks of the player. **Rendering** stays on **`AFTER_TRANSLUCENT_BLOCKS`** (world-space pose); **`AFTER_LEVEL` + frustum cull** was dropping all labels because the stage’s frustum is not valid for world AABBs.
- **Biome / noise_settings:** removed an attempted bundled **`minecraft:worldgen/noise_settings/overworld`** override: **`noise.size_horizontal`** must be an **integer `1`–`4`** (vanilla overworld is **`1`**); fractional values **fail registry load** (`Value … outside of range [1:4]`). Smaller biome patches are **not** achievable by lowering this field alone.

### Added

- **Island HUD (overworld floating islands):** server builds nearby-island labels from `FloatingIslandSavedData` + `FloatingIslandLayout` (anchor uses `peakSurfaceYAtIslandCenter` + configurable height), syncs via `IslandHudSyncPayload`, client cache + `IslandHudRenderer` / `IslandHudWorldBillboard` (see-through option, night color boost). **Common** config: `islandHudSyncEnabled`, `islandHudSyncIntervalTicks`, `islandHudRegionScanRadius`, `islandHudHeightAbovePeakBlocks`. **Client** config: `islandHudShow`, `islandHudTextScale`, `islandHudSeeThroughText`, `islandHudNightColorBoost` (`config/projectisland-common.toml` / `config/projectisland-client.toml`).
- **Island HUD v2:** deterministic procedural names (`FloatingIslandDisplayName`), extended beacon payload (title / status / grid id / colors / state), client `IslandHudWorldBillboard` — translucent panel + border (`RenderType.debugQuads`), **custom 64×64 PNG icons** (`assets/projectisland/textures/gui/island_hud/floating-island.png` claimed, available alternates with `floating-island_ex.png`), contested still uses vanilla torch item; larger default `islandHudTextScale`.
- **Island HUD billboard:** wider Z separation between border, fills, icon, and text to reduce z-fighting while moving; icon uses `RenderType.entityCutoutNoCullZOffset` and **pose-space normals** on the textured quad for more stable tinting.
- **Island HUD distance:** client culls by **camera distance** (~90% of `renderDistance × 16`); **`hasChunk`** is relaxed when the beacon column is within a few chunks of the player so labels are not dropped spuriously. **Frustum culling** for these billboards was removed (unsafe outside the main world pass).
- **Phase 3 (initial):** `FloatingIslandKey` + `FloatingIslandLayout` (shared math with chunk generator), `FloatingIslandSavedData` / `IslandRecord` / `IslandState`, `IslandWorld` helpers, OP command `/projectisland island here`. README “Phase 3” subsection documents IDs and save file.
- **Floating islands tuning:** common config `floatingIslandsRareStructureKeepChance` (thins `minecraft:monster_room` and `minecraft:trial_chambers` after worldgen) and `floatingIslandsExtraSurfaceTreesPerChunk` (extra oak/fancy oak/birch on grass tops after `applyBiomeDecoration`). README documents both.
- **Bundled client resource pack (legal to redistribute):** [Unshaded Blocks](https://modrinth.com/resourcepack/unshaded-blocks) v2.1 (CC0) as unpacked `resourcepacks/bundled_unshaded_blocks/` (NeoForge `addPackFinders` uses `PathPackResources`, which does not load a `.zip` root). Registered via `AddPackFindersEvent` in `ProjectIslandClient`. Attribution in [`licenses/UNSHADED_BLOCKS_ATTRIBUTION.txt`](licenses/UNSHADED_BLOCKS_ATTRIBUTION.txt). (New Default+ and similar ARR packs cannot be vendored inside the JAR.)
- **Safe entry into floating islands:** `FloatingIslandsSpawnEvents` teleports the player to the **nearest island surface** on **login** and when **changing dimension to `minecraft:overworld`** if the arrival column is void (spiral search from arrival XZ, multi-sample per chunk). `FloatingIslandsChunkGenerator.islandSurfaceBlockY` exposes the same top Y logic used by worldgen.
- `FloatingIslandsChunkGenerator`: **structures + trim** — call `super.createStructures`, then remove structure blocks in void columns or above the island surface (`trimFloatingStructureBlocks`) so mineshafts / ruined portals hug terrain instead of hanging in empty sky.
- `FloatingIslandsChunkGenerator`: **smoother tops** (remove blocky `topRelief` / stepped `hrWobble`; smooth sin wobble + shared `verticalHill`; flatter plateau via `TOP_HORIZ_POWER`).
- `FloatingIslandsChunkGenerator`: **less spherical** islands (asymmetric top/bottom ellipsoids, rim-weighted underside, horizontal silhouette wobble) and **wider spacing** (8-chunk regions, lower spawn rate, centers biased inside each cell).
- README: **dedicated server OP / cheats** notes for `/execute` (Brigadier “unknown command” on `/` when not OP); example [`dev-ops.example.json`](dev-ops.example.json) for offline player `Dev`.
- **Phase 2 (prototype):** `FloatingIslandsChunkGenerator` (void + ellipsoid islands, temperature-based tops) and overworld dimension override via datapack. Chunk generator codec registered on the mod bus (`ProjectIslandWorldgen`).
- NeoForge **ModDevGradle** project for **Minecraft 1.21.1** / **NeoForge 21.1.227**: `build.gradle`, Gradle wrapper, `net.projectisland` entrypoint, generated `neoforge.mods.toml` from templates, GitHub Actions build workflow.
- Initial project documentation: `README.md`, `TODO.md`, and this changelog.
- Cursor project rule `.cursor/rules/project-island.mdc` for consistent AI and contributor context.
- README: **game pillars**, **propulsion tier table** (sails / propellers / jets), **tech tree** intent, architecture diagram nodes for unlocks and propulsion, and **Learning from existing work** (mods, datapacks, modpacks, GitHub; licenses and pinning).
- TODO: **Phase 6** (propulsion, advancement tree, parallel tech tracks, fuel), clarified Phase 5 travel vs **airship** core loop, renumbered later phases; pointer to use online examples when researching.
- Cursor rule: airship capture loop, **server-side unlocks**, explicit encouragement to reuse **mods / datapacks / modpacks / GitHub** with license awareness.
