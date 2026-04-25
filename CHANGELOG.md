# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Documentation pass: **2026-04-22**._ _Phase 2 overworld + pregen: **2026-04-24**._ _README/TODO sync: **2026-04-25**._

### Added

- **Rope health & strain (Phase 4):** each **`RopeLink`** stores **health / maxHealth** (NBT + new harpoon links use **`ropeLinkMaxHealth`**). **`RopeLinkStress`** applies damage when span / max length exceeds **`ropeLinkStrainRatioThreshold`**; at **0** health anchors **`severLinkFromSavedData`**. **`RopeLinkSyncPayload`** includes **health fraction**; client **`RopeLinkHealthBarRenderer`** draws billboard bars at **both** anchor ends when **`ropeLinkHealthBarsShow`** is on. Common: **`ropeLinkMaxHealth`**, **`ropeLinkStressTickInterval`**, **`ropeLinkStrainRatioThreshold`**, **`ropeLinkStrainDamagePerTick`** (set damage to **0** to disable strain).

- **Secondary claim (Phase 4):** **`IslandSecondaryClaim`** centralizes rules; **`/projectisland island claim`** uses common config **`secondaryClaimCommandPermissionLevel`** (default **0**; use **2** for OP-only). **Sneak + use** (empty hand) on **your** linked **rope anchor** on an **AVAILABLE** island attempts the same claim (action-bar feedback). Lang keys under **`projectisland.claim.*`**.

### Changed

- **Void rescue flavor text:** random **action-bar** lines (same style as harpoon feedback) when you are moved by void rescue, starter void snap, nearest-island relocate, or unsafe floating respawn.

- **Void rescue (less “dungeon stairs”):** per-tick rescue and **starter snap on login** only run in the **void-floor band** (`minBuildHeight` + `voidRescueTriggerBlocksAboveMinY`; default **12** blocks above min, was 48). **`onGround()`** counts as supported (unless flying) so thin stairs / partial blocks rarely read as open void. **`solidFootingNearColumn`** tolerates slightly more vertical slop under the feet.

- **Rope visuals:** segments attach at the **top of the anchor loop** (`rope_anchor.json`), hang with **parabolic vertical slack** (tessellated), and render as a **square tube** (four chain-textured faces) using **vanilla** `minecraft:textures/block/chain.png`; UVs follow arc length. **Explicit outward normals** per face plus **full-bright** lighting avoid one tube side reading as a black “missing texture” strip from bad cross-product normals / diffuse. **Tighter UVs** and a **slimmer tube** reduce oversized dark link patches.

- **License metadata:** `gradle.properties` `mod_license` is now **`MIT`** to match the existing root [`LICENSE`](LICENSE) file (was **All Rights Reserved**). README license section updated accordingly.
- **Rope anchors:** breaking one end removes the saved `RopeLink` and restores **both** anchor positions to their stored original block states (restore runs next server tick so it is not clobbered by vanilla break). `RopeLink` exposes `otherAnchor(BlockPos)` for the paired endpoint.

### Documentation

- **README / TODO:** Full table of **`islandBiomeWeight*`** keys → Minecraft biomes; NeoForge **`ModConfig.Type.COMMON`** file path **`config/projectisland-common.toml`** on clients and dedicated servers; restart / **new chunks** behavior. TODO Phase 2 biome item links to that README anchor.
- **TODO / README (Phase 4 intent):** Starter island flow spelled out — **region** (not chunk) spiral for **`AVAILABLE`**, **atomic** claim + per-UUID idempotency, **center / HUD** spawn vs **`FloatingIslandsSpawnEvents`** rim rescue; **layout math** before chunks generate; search **cap** + fallback; void rescue kept. README **Current focus** updated to match.

### Added

- **Secondary claim gate:** when **`secondaryClaimRequiresRopeLink`** is true (default), **`/projectisland island claim`** fails unless the player **owns** a **`RopeLink`** between the target region and an island they already **CLAIM** (starter counts). Common config key in **`projectisland-common.toml`**.
- **Void / respawn footing:** shared **`FloatingIslandSurfaceSupport`** — procedural “top” allows **structures far above** the ellipsoid skin; **solid collision** under the feet catches dungeon roofs and similar. **Void rescue** tries **bed / respawn anchor** stand-up (same dimension) **before** starter-home teleport so sleeping on another island is respected when you fall into the void.

- **Harpoon / rope link tuning (common config):** **`ropeLinkRaycastRangeBlocks`** (each shot’s reach) and **`ropeLinkMaxLengthBlocks`** (max anchor–anchor span and value stored on **`RopeLink`**) — **`HarpoonGunItem`** reads both from **`Config`** (`projectisland-common.toml`).

- **Rope link rendering:** server **`RopeLinkServerSync`** sends **`RopeLinkSyncPayload`** (packed anchor positions) on a configurable interval in the floating-islands overworld; client **`RopeLinkClientCache`** + **`RopeLinkSegmentRenderer`** draws rope between anchors. Common config: **`ropeLinkSyncEnabled`**, **`ropeLinkSyncIntervalTicks`**, **`ropeLinkSyncCullRadiusBlocks`**. Client config: **`ropeLinksShow`**.

- **Phase 4 (starter island + claims):** **`FloatingIslandStarterPlacement`** assigns first **`AVAILABLE`** island in a **region Chebyshev spiral** from overworld shared spawn (configurable), **`tryClaimStarterIsland`** on **`FloatingIslandSavedData`** (atomic **CLAIMED** + **`StarterHomes`** UUID map), teleport to **`FloatingIslandLayout.regionIsland`** center surface. **`PlayerLoggedIn`:** starter placement runs **before** void rescue; optional **kick** if search fails (`starterIslandFailureKickMessage`). Common config: **`starterIslandAutoAssignEnabled`**, **`starterIslandSearchFromWorldSpawn`**, **`starterIslandMaxRegionSearchRadius`**, **`starterIslandMinRegionSeparation`**, **`starterIslandFailureKickMessage`**. **`IslandChunkLoader`:** sync **3×3** **`ChunkStatus.FULL`** load before starter / void / respawn teleports and **`findNearestIslandFeet`**. **`trySecondaryClaim`** + **`/projectisland island claim`** (OP 2) for interim non-starter **`AVAILABLE`** claims.
- **Void / respawn safety (floating overworld):** **`FloatingIslandVoidRescue`** on **`PlayerTickEvent.Pre`** (when **`voidRescueEachTick`**): **once per void fall** when Y reaches **`minBuildHeight` + `voidRescueTriggerBlocksAboveMinY`** (not mid-air), then starter / nearest island; **NBT** tracks open-void fall. **`FloatingIslandRespawnHandler`** on **`PlayerRespawnPositionEvent`**: unsafe overworld void respawn → starter or nearest island (valid **bed** unchanged). **Rim-safe** support uses **hitbox XZ ± 1**; respawn **unsafe** uses **3×3** columns. **`relocatePlayerFromVoid`** no-ops if already supported (join on solid ground).

### Changed

- **Overworld biomes on floating islands:** **`FloatingIslandsChunkGenerator`** no longer uses vanilla **`multi_noise` climate** for **solid island** columns (it often resolved to **river/ocean** for void-style worlds at every Y). Each **`FloatingIslandKey`** gets **one** biome from **config weights** (`islandBiomeWeightRiver`, `islandBiomeWeightPlains`, …), rolled with **`RandomState.getOrCreateRandomFactory`** at region coordinates (stable per seed). **Void** columns use **plains**. **`createBiomes`** remains synchronous. **`fillFromNoise`** / **`getBaseColumn`** use the same picker. **Removed** `floatingIslandsBiomeHorizontalScale`. **Mushroom fields** → **mycelium** tops. **Extra surface features:** spruce/pine on snow, huge mushrooms on mycelium, acacia/oak on sand.
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
- **Floating islands tuning:** common config `floatingIslandsRareStructureKeepChance` (thins `minecraft:monster_room` and `minecraft:trial_chambers` after worldgen) and `floatingIslandsExtraSurfaceTreesPerChunk` (extra configured features on compatible tops after `applyBiomeDecoration`; see README). README documents both.
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
