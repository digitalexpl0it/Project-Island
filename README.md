# Project Island

A NeoForge Minecraft **server + client** mod project: a void world of **procedurally generated floating islands** with biome-appropriate surfaces, **per-island ownership** (available, claimed, capturable), and progression toward **mobile island-bases** used to **contest and capture** other islands.

Players join a world that is **already** the island dimension (no portal lobby for the core experience). Island rules, **propulsion unlocks**, and capture outcomes are **server-authoritative** and persisted with the world.

## Game pillars

1. **Survival on an island** — Resources, farming, mining, and building on a bounded floating landmass.
2. **Territory** — Claim, defend, lose, or **steal** islands; alliances optional.
3. **Island as airship** — Players upgrade their island into a **slowly moving base** (sails → propellers → jet-style thrust), then use mobility to **raid or capture** neighbors—not only bridges or static skybases.
4. **Tech progression** — An **advancement / unlock tree** (vanilla-style advancements, custom research, or both) gates better propulsion, utility, and defense. Higher tiers allow **more thrust modules** or efficiency where design requires it.

## Propulsion tiers (design target)

| Stage | Role | Notes |
|-------|------|--------|
| **Level 1** | **Sails** | Slow island movement; cheap or early unlock. |
| **Level 2** | **Propellers** | Faster than sails; **sub-tiers** increase allowed thrust or count. |
| **Level 3** | **Jet engines** | Top-end speed band; **sub-tiers** increase thrust caps or fuel tradeoffs. |

Other branches (defense, docking / merge, power, automation) should get **their own tier tracks** in design docs and [TODO.md](TODO.md). Visual and mechanical flavor can include **helm**, **cogwheels** (often paired with **Create**-style motion), **sails**, **propellers**, and **jets**, depending on era unlocked.

## Roadmap and history

- [TODO.md](TODO.md) — phased checklist from bootstrap through ops.
- [CHANGELOG.md](CHANGELOG.md) — notable changes ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/) style).

**Current focus:** **Phase 2 is done**; **Phase 4** starter + dock/link + rope topology + rope tiers are in. **Player progression UI:** **FTB Quests** + **ProgressiveStages** starter pack under **`examples/dev-progression/`** (mirrored into `run-client` / `run-server` `config/` for dev). **Void rescue** teleports to **procedural island centers** first (same anchor as starter/HUD) to avoid rim loops. Roadmap detail: [TODO.md](TODO.md).

## Target stack

- **Loader:** NeoForge (pinned in [`gradle.properties`](gradle.properties)).
- **Distribution:** matching mod JAR on dedicated server and every client.
- **Integrations (later, version-locked):** **Valkyrien Skies** (and related ship mods) for **rigid moving assemblies**; **Create** for **cogwheels, kinetic contraptions**, and polish on moving platforms. Document exact versions in this README when they are chosen. Custom code may still wrap fuel, thrust caps, and unlock checks.

## Resource packs (what ships in the JAR)

**New Default+** (CurseForge / Modrinth) and many other artist packs are **not** redistributable inside this mod’s JAR unless their license explicitly allows it (New Default+ is typically **all rights reserved**). Players can still install those packs **manually** in `.minecraft/resourcepacks` and enable them above vanilla.

This repository **does** ship a small **CC0** pack bundled in the mod file:

- **[Unshaded Blocks](https://modrinth.com/resourcepack/unshaded-blocks)** (v2.1 for Minecraft 1.21.1) — removes legacy **block shading** on most blocks for a **flatter, cleaner** look. It is **not** a full HD overhaul like New Default+. Attribution: [`licenses/UNSHADED_BLOCKS_ATTRIBUTION.txt`](licenses/UNSHADED_BLOCKS_ATTRIBUTION.txt).

The mod registers it as a **built-in client resource pack** (`AddPackFindersEvent` in `ProjectIslandClient`). Enable or reorder it under **Options → Resource Packs** like any other pack (place **above** vanilla if you combine it with other packs).

For **glowing ores / neon** style, you usually want a **shader** (e.g. Complementary, BSL) in addition to or instead of only a texture pack.

### Progress UI (preferred for modpacks)

- **FTB Quests** — the primary “modern tree” UI (tasks + rewards).
- **ProgressiveStages** — a stage/level system the **server** can check to gate mechanics (items/recipes/tiers), independent of client UI.

Vanilla advancements can still exist for lightweight toasts/milestones, but they’re not the core progression UI for Project Island.

**Dev runs:** a starter quest chapter and Project Island–specific stage files live under `examples/dev-progression/`; the Gradle `runClient` / `runServer` directories mirror those into `config/ftbquests/quests/` and `config/ProgressiveStages/`. **Quest/chapter files** are picked up on the next start (or your FTB Quests reload path if you use it); use **`/progressivestages reload`** after editing stage TOML (and **`/progressivestages validate`** to catch typos).

## Learning from existing work

When designing worldgen, ships, progression, or UX, **actively reuse ideas and patterns** from public sources (always respect **licenses** and **attribution**):

- **Mods** (CurseForge, Modrinth, GitHub)—e.g. movement/ship libraries, tech mods, claim mods (for anti-patterns as well as features).
- **Datapacks** and **world presets**—quick iteration on loot, advancements, and experimental rules.
- **Modpacks**—see how authors balance gating, grind, and multiplayer load.
- **GitHub**—reference implementations, NeoForge MDKs, and small MIT/BSD examples; do not copy GPL code into incompatible targets without legal review.

Prefer **pinned** versions and a short **compatibility / inspiration** note in this README when a third-party mod becomes a hard dependency.

This project was bootstrapped from the official **[ModDevGradle MDK for Minecraft 1.21.1](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle)** template (NeoForged). See NeoForge docs: [Getting Started](https://docs.neoforged.net/docs/1.21.1/gettingstarted/).

### Pinned toolchain (bootstrap)

| Piece | Version |
|-------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.227 |
| Parchment (MC / mappings) | 1.21.1 / 2024.11.17 |
| ModDevGradle plugin | 2.0.141 (`build.gradle`) |
| Gradle (wrapper) | 9.2.1 |
| Java | **21** |

## Architecture (high level)

```mermaid
flowchart LR
  subgraph client [Client]
    ModJar[Mod JAR]
  end
  subgraph server [Dedicated Server]
    Worldgen[Chunk generator]
    IslandDB[Island SavedData]
    Unlocks[Tech unlock state]
    Rules[Claim capture rules]
    Propulsion[Propulsion caps]
  end
  ModJar -->|same mod id| server
  Worldgen --> IslandDB
  Unlocks --> Propulsion
  Rules --> IslandDB
  IslandDB --> Propulsion
```

## World (Phase 2)

The mod registers **`projectisland:floating_islands`** as a **chunk generator type** and ships a built-in **datapack override** for the overworld dimension under `data/minecraft/` (same pattern as shipping JSON in the mod JAR—**no separate download**):

| Piece | What it is |
|-------|------------|
| `minecraft:overworld` | Same dimension id players always join; **terrain** is replaced by **void + scattered ellipsoid islands** (stone/dirt subsurface; tops **grass / sand / snow / mycelium** from overworld biome, including **mushroom fields**). Nether and End are unchanged. |
| `projectisland:floating_islands` | **Chunk generator codec id** (not a separate dimension). |

Details:

- **Dimension type** stays `minecraft:overworld` (sky, day cycle, monster ranges) so the sky feels familiar while terrain is custom.
- **Generator** is Java (`FloatingIslandsChunkGenerator`): islands sit on a **sparse** coarse grid (8×8 chunks per cell, ~17% spawn). Each mass uses an **asymmetric** profile (short **vrTop** dome + longer **vrBottom** tail with **extra depth under the rim**), **smooth** horizontal scaling (no block-step noise), a **wider flat-ish plateau** on top (`horiz^0.72` inside the cap), and a **low-frequency** vertical hill so surfaces read as gentle terrain rather than Swiss cheese. **Vanilla structures** (mineshafts, ruined portals, etc.) still run, then a **trim pass** removes structure blocks in **void columns** or **floating above** the island top so pieces tend to **cling to land** where they intersect it; **biome decoration** (trees, flowers, ores) still runs. (Shader lighting in reference art is separate from this terrain pass.)
- **Biomes:** overworld dimension JSON still uses **`minecraft:multi_noise`** preset **`minecraft:overworld`** (structures / compatibility). **Island land** does **not** use vanilla climate sectors for the chunk biome palette: each **`FloatingIslandKey`** (8×8-chunk region that owns an island) gets **one** overworld biome from **weighted random**, deterministic from **world seed + region X/Z** via `RandomState.getOrCreateRandomFactory`. Tune with **`islandBiomeWeight*`** in **`config/projectisland-common.toml`** — full key list and dedicated-server notes under **[Island biome weights (common config)](#island-biome-weights-common-config)**. **Void** sky columns use **plains** so F3 is not stuck on river/ocean. **Note:** `noise_settings` **`size_horizontal`** is an **integer `1`–`4`** in 1.21.1 (vanilla overworld is **`1`**).
- **Tuning** (`config/projectisland-common.toml` on client or server): `floatingIslandsRareStructureKeepChance` — each **monster room** or **trial chamber** that appears in a chunk is **kept** with this probability after trim (default `0.12`; use `1.0` to disable thinning). **Per-island biome weights** — see **[Island biome weights (common config)](#island-biome-weights-common-config)**. `floatingIslandsExtraSurfaceTreesPerChunk` — extra vanilla **surface features** after decoration (default `5`; `0` disables): **oak / fancy oak / birch** on **grass**, **spruce / pine** on **snow**, **huge mushrooms** on **mycelium**, **acacia / oak** on **sand**. **Spawn pregen (optional):** `spawnPregenChunkRadius` (Chebyshev chunk radius around overworld spawn, **`0` = off**) and `spawnPregenChunksPerTick`. **Starter island (Phase 4):** `starterIslandAutoAssignEnabled`, `starterIslandSearchFromWorldOrigin` (optional anchor at world **0, 0**; overrides spawn/join), `starterIslandSearchFromWorldSpawn` (spawn vs join chunk when origin is off), `starterIslandMaxRegionSearchRadius` (Chebyshev **region** rings), `starterIslandMinRegionSeparation` (minimum region distance between different players’ starters), `starterIslandFailureKickMessage` (non-empty = disconnect if no candidate in radius; empty = warn log only). First join assigns one **`AVAILABLE`** region as **CLAIMED** and teleports to **region center** (HUD-aligned); void rescue still runs afterward if needed. **`voidRescueEachTick`** (default **true**): while falling with **no** island support, **`voidRescueSnapToLastSafeEnabled`** (default **true**) teleports you back to the **last feet position that was on solid / island surface** once you are **`voidRescueSnapToLastSafeMinFallBlocks`** (default **20**) below that Y (skips elytra / creative flight). Then, if still unsupported, rescue runs **once** when Y reaches **`minBuildHeight` + `voidRescueTriggerBlocksAboveMinY`** (default **12** → **Y ≤ −52** when min is **−64**), then **bed / starter / nearest island**. **`voidRescueSnapToLastSafeCooldownTicks`** spaces repeat snaps. Join / dimension change still uses **immediate** relocation if you are not on a surface. If anyone still hits vanilla **“Flying is not enabled”** on long falls, set **`allow-flight=true`** in **`server.properties`** as a last resort. **Death respawn:** unsafe overworld void positions (missing bed, etc.) are redirected to **starter** or nearest surface via **`PlayerRespawnPositionEvent`**; a valid **bed** in the overworld is kept. **Island HUD (server):** `islandHudSyncEnabled`, `islandHudSyncIntervalTicks`, `islandHudRegionScanRadius`, `islandHudHeightAbovePeakBlocks` — sync is driven from **`ServerTickEvent.Post`** (per-player interval). **`ProjectIslandDimensions.isFloatingIslandsGameplay`** gates sync (direct `FloatingIslandsChunkGenerator` or shallow **delegate** on `ChunkGenerator`). **Island HUD (client only):** `config/projectisland-client.toml` — `islandHudShow`, `islandHudTextScale`, `islandHudSeeThroughText`, `islandHudNightColorBoost`, `islandHudPanelFillOpacity`, `islandHudPanelBorderOpacity`, `islandHudPanelScale`.

### Island biome weights (common config)

NeoForge registers these as **`ModConfig.Type.COMMON`** → **`config/projectisland-common.toml`** in the instance directory (single-player save folder **or** dedicated server root—**this is the file operators edit on a server**, alongside structure thinning and island HUD server options). **Rope links:** **`ropeLinkMaxHealth`**, **`ropeLinkStressTickInterval`**, **`ropeLinkStrainRatioThreshold`**, **`ropeLinkStrainDamagePerTick`** (set damage to **0** to disable overstretch strain while keeping health bars full). **Rope topology (harpoon):** **`ropeTopologyEnabled`**, **`ropeTopologyMaxDepthFromStarter`** (up to **8** when tertiary is allowed), **`ropeAllowTertiaryIslandLinks`** (default **false** = only starter + one ring; set **true** for full configured depth, e.g. tertiary at depth **2**), **`ropeMainDirectSpokeCap`** (**1–4**, default **1**), **`ropeSisterOutboundCap`** (**1–3**, default **1**). **`autoClaimIslandOnRopeLink`** (default **true**) claims an **AVAILABLE** island when you complete a link from your starter or an island you already own. **Client** `projectisland-client.toml`: **`ropeLinkHealthBarsShow`** toggles anchor-end health bars (requires **`ropeLinksShow`**). Island HUD billboard: **`islandHudPanelFillOpacity`**, **`islandHudPanelBorderOpacity`**, **`islandHudPanelScale`** (see tuning bullet above for the full island HUD key list).

After changing weights, **restart** the server or client session so worldgen reads values consistently. **Chunks already generated** keep their stored biome palette; **new** chunks (or a **new** world) pick up the new distribution.

Each key is an integer weight **`0`–`1000`**. **`0`** removes that biome from the pool; weights are **relative** (the roll normalizes by the sum of all positive weights). Keep **at least one** weight **greater than `0`** (otherwise the implementation falls back to plains).

| TOML key | Minecraft biome |
|----------|------------------|
| `islandBiomeWeightRiver` | `minecraft:river` |
| `islandBiomeWeightPlains` | `minecraft:plains` |
| `islandBiomeWeightForest` | `minecraft:forest` |
| `islandBiomeWeightTaiga` | `minecraft:taiga` |
| `islandBiomeWeightDesert` | `minecraft:desert` |
| `islandBiomeWeightSnowyPlains` | `minecraft:snowy_plains` |
| `islandBiomeWeightJungle` | `minecraft:jungle` |
| `islandBiomeWeightMushroomFields` | `minecraft:mushroom_fields` |
| `islandBiomeWeightBadlands` | `minecraft:badlands` |
| `islandBiomeWeightWindsweptForest` | `minecraft:windswept_forest` |
| `islandBiomeWeightSwamp` | `minecraft:swamp` |

Try it in a dev world (you are already in overworld):

```text
/tp @s 0 120 0
```

If you land in void sky, the mod **moves you onto the nearest procedural island** on **login**, **return to overworld**, or **dimension change** to overworld (spiral search from arrival XZ; multi-sample per chunk). Prefer a **new world** when upgrading from older builds that used a **separate dimension id**—overworld terrain may not match old vanilla slices.

**Migration:** older Project Island builds used `projectisland:floating_islands` as a **dimension**. That entry is removed so **island saved data** (`projectisland_floating_islands.dat`) lives only on **overworld**. Delete leftover `DIM1` (or similar) folders from old test worlds if present.

(`teleport` and `tp` are equivalent in Java Edition; use a leading **`/`** so the game treats it as a command, not chat.)

### Dedicated server (`./gradlew runServer`): you must be OP

The dev server (`./gradlew runServer`, game dir `run-server/`) may have an **empty** [`run-server/ops.json`](run-server/ops.json). Without operator permission, Brigadier often reports **`Unknown or incomplete command`** with the caret under the first **`/`** even though the syntax is fine. The repo keeps [`dev-ops.example.json`](dev-ops.example.json) (offline UUID for user **Dev**); copy it to `run-server/ops.json` and **restart** if you are not an OP.

Pick one:

1. **From the server console** (no `/` prefix): `op Dev` — use the **exact** name shown when you join (default dev client user is often `Dev`).
2. **Copy the example ops file** from the repo root: [`dev-ops.example.json`](dev-ops.example.json) → `run-server/ops.json`, then **restart** the server (the workspace copy targets the **Dev** offline UUID; change name/UUID if you use a different test account).

Single-player worlds need **Allow Cheats** (or **Open to LAN** with cheats) instead.

Fly around (`F3` debug lists `ChunkGenerator: projectisland:floating_islands` on the debug pie when relevant). Vanilla **feature decoration** (trees/ores) runs on solid surfaces after noise fills, plus optional **bonus trees** from config; **each island region** uses one rolled overworld biome (see **[Island biome weights (common config)](#island-biome-weights-common-config)**).

### Phase 3 — Island identity and persistence (initial)

- **Stable id:** `FloatingIslandKey` = coarse grid `(regionX, regionZ)` — the same cell the procedural RNG uses (`FloatingIslandLayout.REGION_CHUNKS`). For a block column, `FloatingIslandLayout.islandOwningSurface` picks the neighbor region whose ellipsoid **wins** the surface height (ties broken deterministically).
- **Saved data:** overworld file `projectisland_floating_islands.dat` via `FloatingIslandSavedData` (`IslandState`: `AVAILABLE`, `CLAIMED`, `CONTESTED` placeholder). Rows are created on demand.
- **Starter claim (Phase 4):** on **`PlayerLoggedIn`**, players without a **`StarterHomes`** entry get the first **`AVAILABLE`** island in a **region** spiral from shared spawn (configurable); **`FloatingIslandSavedData`** records **CLAIMED** + starter map; returning players in the **void** are moved back to that island’s **center** surface when possible. **`PlayerTickEvent.Pre`:** **`FloatingIslandVoidRescue`** — last-safe mid-void snap, then rescue **once per void fall** when Y nears **`minBuildHeight` + `voidRescueTriggerBlocksAboveMinY`** (bed, starter, nearest island) if **`voidRescueEachTick`** is on. **`relocatePlayerFromVoid` / `findNearestIslandFeet`** spiral **region centers** (via **`FloatingIslandStarterPlacement.optionalFeetAtIslandCenter`**) before legacy chunk-edge samples, and only accept fallbacks if the player is **actually supported** after teleport — reduces rim → fall → repeat → disconnect loops. **`PlayerRespawnPositionEvent`:** **`FloatingIslandRespawnHandler`** fixes respawns that would land in void (prefers **starter**, else nearest surface); **bed** anchors that are already safe are unchanged.
- **Island commands:** `/projectisland island here` — any player; prints the island key and `IslandRecord` state at your feet (void columns report none). **`/projectisland island claim`** — **AVAILABLE → CLAIMED** for the island under your feet when `secondaryClaimRequiresRopeLink` is satisfied (see [TODO.md](TODO.md)); permission level **`secondaryClaimCommandPermissionLevel`** in **`projectisland-common.toml`** (default **`0`** = survival-friendly; set **`2`** to restrict to operators). If `secondaryClaimCommandMaxDistanceBlocks` > 0, command claims also require you to be within that horizontal distance of your rope anchor endpoint on the target island (prevents remote claims). **In-world:** **sneak + use** (empty hand) on **your** linked **rope anchor** on the target island runs the same claim rules (no Brigadier permission gate).
- **Rope network (Phase 4):** **Starter = main hub**. **Server enforcement** on the **second harpoon anchor** (`RopeTopology`): stay connected to starter, **depth** capped by **`ropeTopologyMaxDepthFromStarter`** and **`ropeAllowTertiaryIslandLinks`** (default **off** = hub + one ring only; turn on for tertiary at depth **2**), **direct spoke** cap off starter (default **1**, up to **4** in config), **sister outbound** cap per claimed non-starter (default **1**, up to **3**). **Abandon** by **breaking ropes**; with **`secondaryClaimRequiresRopeLink`**, orphan **non-starter** claims revert to **AVAILABLE**. Advancement-driven caps can replace the tertiary toggle later (Phase 6). **Dock / link design (secondary claims):** [docs/phase4-dock-link-spec.md](docs/phase4-dock-link-spec.md) — claim surfaces, logical rope gate, known MVP gaps, future hardening.
- **Rope tier progression (MVP):** new rope links scale **max span** and **max health** based on advancements: **Reinforced Rope** (`projectisland:progression/rope_reinforced`, triggered by obtaining a **chain**) and **Steel Rope** (`projectisland:progression/rope_steel`, triggered by obtaining a **netherite ingot**). This is server-authoritative (`RopeProgression`) and only affects **new** links.
- **Existing rope upgrades:** when `ropeProgressionUpgradeExistingLinks` is **true**, existing links you own are periodically upgraded to match your current tier (`ropeProgressionUpgradeIntervalTicks`), preserving health fraction.
- **Nearby island HUD (read-only):** while you are in the **floating-islands overworld** (server uses `ProjectIslandDimensions.isFloatingIslandsGameplay`), the server periodically sends **`IslandHudBeacon`** rows for a configurable **region radius** around you (`IslandHudSyncPayload` → `IslandHudClientCache`). The client draws **`IslandHudWorldBillboard`** panels on **`RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`** so the **pose stack matches world space** (do **not** drive these billboards from **`AFTER_LEVEL`**: that stage’s **frustum is not valid** for world-space AABBs and will cull everything). Culling is by **camera distance** (~90% of `renderDistance × 16`) plus a **relaxed `ClientLevel.hasChunk` check** (beacon chunk within a few chunks of the player if the column is not yet flagged loaded). **Icons:** two **64×64** PNGs in `assets/projectisland/textures/gui/island_hud/`: **`floating-island.png`** for **claimed** and as one frame for **available**; **`floating-island_ex.png`** alternates with it for **available** (slow blink). **Contested** uses a vanilla **torch** item render. Operators cannot ship those textures through a **datapack** alone—players need a **resource pack** (or a forked mod JAR) at the same paths; see **`examples/island_hud_icons_resource_pack/`** in the repo. **Names:** server reads **`data/<namespace>/floating_island_display_names/names.json`** on **`/reload`** (built-in copy ships under **`projectisland`**; datapacks can override). JSON shape: **`adjectives`** and **`nouns`** as string arrays (max 400 entries each, 48 chars per word). **`FloatingIslandDisplayName`** picks **adjective + noun** deterministically from region X/Z. **Client** config: see-through text, night color boost, **panel opacity**, **`islandHudPanelScale`** (padding + icon slot + title ellipsis width). Not a claim UI or world border—see [TODO.md](TODO.md) Phase 4.

## Building

Requires **JDK 21**. From the repository root:

```bash
./gradlew build
```

Outputs: `build/libs/projectisland-<version>.jar` (use the JAR **without** `-sources` or `-javadoc` on servers and clients).

```bash
./gradlew runClient
./gradlew runServer
```

The first Gradle run downloads Minecraft and NeoForge artifacts and can take several minutes.

## License

**Project Island mod code** in this repository is released under the **MIT License** — see [`LICENSE`](LICENSE). Mod metadata uses SPDX **`MIT`** in [`gradle.properties`](gradle.properties) (`mods.toml`).

Bundled or third-party assets (resource packs, textures copied from other mods, etc.) keep **their own licenses**; see files under [`licenses/`](licenses/) and notes in this README. If you vendor assets under **CC-BY-NC-SA** (for example), you must still follow **attribution**, **non-commercial**, and **ShareAlike** terms for those assets even though this project’s source is MIT.

---

_Documentation last revised **25 April 2026** (FTB Quests / ProgressiveStages dev pack; void rescue center-first; `run-server/ops.json`; **Current focus** + Phase 3 void-rescue bullet)._
