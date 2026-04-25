# Project Island — TODO / roadmap

Phased checklist for the NeoForge mod and dedicated server. Check items off as you complete them.

_Roadmap reviewed **2026-04-22** (island HUD + config documentation). Updated **2026-04-23**: Phase 2 priority + claim design (starter vs dock). Updated **2026-04-25**: island HUD pipeline; **`noise_settings`** `size_horizontal` override reverted (must be int `1`–`4`; vanilla overworld already `1`). **2026-04-22** doc pass: README island biome weights table + dedicated-server `projectisland-common.toml` notes; Phase 4 starter placement spec (region spiral for `AVAILABLE`, **center / HUD-aligned** teleport, atomic claim, search cap + fallback)._

When researching features, use **mods, datapacks, modpacks, and GitHub** as examples (see [README.md](README.md) — “Learning from existing work”); pin anything you depend on and respect licenses.

## Current focus

- **Phase 2 is closed** (overworld floating islands, `multi_noise`, optional spawn pregen). Next: **Phase 4** claims when you are ready.

## Out of scope for v1

- Full MMO-style matchmaking or cross-shard persistence.
- Portal-based lobby worlds as the **primary** way to reach gameplay (core loop is: join → overworld or single dimension is already islands).
- Promising specific third-party mod versions before they are pinned and tested together.

---

## Phase 1 — Bootstrap

- [x] Pin **Minecraft** and **NeoForge** versions (document in `README.md` and Gradle).
- [x] Add NeoForge **MDK** / Gradle project to the repository.
- [x] Define mod id, Maven coordinates, and Java package namespace.
- [x] Author `mods.toml` (metadata, dependency block when integrations exist).
- [x] Verify `./gradlew build` from a clean clone (`runClient` / `runServer` available; run locally as needed).
- [x] (Optional) Minimal CI (Gradle build on push) — `.github/workflows/build.yml`.

## Phase 2 — Worldgen

- [x] Void-style world: no sea-level continent; islands suspended over void — **`minecraft:overworld`** uses `FloatingIslandsChunkGenerator` via built-in datapack (`data/minecraft/dimension/overworld.json`).
- [x] Procedural **floating islands** with spacing and size variance (tunable) — ellipsoid islands on a region grid (`FloatingIslandsChunkGenerator`).
- [x] **Biome-aware** surfaces: grass, sand, snow from biome **temperature**; trees/ores from vanilla **feature** step where it triggers; overworld datapack still uses **`minecraft:multi_noise`** preset **`minecraft:overworld`**, while **island land** biomes are **weighted per `FloatingIslandKey`** (see [README — Island biome weights](README.md#island-biome-weights-common-config) and `islandBiomeWeight*` in `config/projectisland-common.toml`), not vanilla climate sectors.
- [x] No mandatory **portal**: players join **overworld** directly with floating-island terrain (Nether/End unchanged).
- [x] (Optional) **Spawn pregen:** common config `spawnPregenChunkRadius` (0 = off) + `spawnPregenChunksPerTick` — `FloatingIslandsSpawnPregen` loads a Chebyshev chunk neighborhood around shared spawn after level load.
- [x] **Void spawn mitigation:** `FloatingIslandsSpawnEvents` on **dimension change to overworld** and **player login** — nearest procedural island surface from XZ (`FloatingIslandsChunkGenerator.islandSurfaceBlockY`).
- [x] Document world type / dimension choice in `README.md`.

## Phase 3 — Island model

- [x] Stable **island ID** — `FloatingIslandKey` / `FloatingIslandLayout.islandOwningSurface` (coarse region grid aligned with worldgen). **Spatial bounds** still implicit from ellipsoid math (no separate AABB cache yet).
- [x] **Persistence** — `FloatingIslandSavedData` (`projectisland_floating_islands.dat`) on the overworld `ServerLevel` when it uses the floating chunk generator.
- [x] Island **states**: `AVAILABLE`, `CLAIMED` (owner UUID + time fields on `IslandRecord`), `CONTESTED` reserved.
- [x] Serialization **version** field on saved file (`Version` int); expand when migrating rows.
- [x] **Nearby island HUD** — server sync of island state for a radius around the player in the floating-islands overworld (`IslandHudServerSync` on **`ServerTickEvent.Post`**, `ProjectIslandDimensions.isFloatingIslandsGameplay`); client **`IslandHudRenderer`** + `IslandHudWorldBillboard` on **`AFTER_TRANSLUCENT_BLOCKS`**; common/client config (`islandHud*` keys). Read-only until Phase 4 claim actions exist.
- [x] **Island HUD v2 (2026-04-22):** procedural display names (`FloatingIslandDisplayName` + word lists, deterministic per region), billboard **panel** (translucent dark fill + tinted border + state **item icon** + title / status / id lines) via `IslandHudWorldBillboard` + extended `IslandHudBeacon` payload.
- [ ] **Island HUD polish (optional):** datapack or JSON-driven name word lists; custom atlas icon instead of vanilla item stacks; panel size / colors in config.

## Phase 4 — Spawn and claims

**Design intent**

- **Starter island (one per player, free):** on **first join** to the floating-islands overworld, pick an **`AVAILABLE`** `FloatingIslandKey`, **auto-claim** it (**CLAIMED** + owner) **once per UUID** (idempotent on relog—do not re-roll or overwrite others). **Placement search:** spiral in **region coordinates** `(regionX, regionZ)` from a configurable anchor (e.g. **world spawn / origin**), skip `!FloatingIslandLayout.regionHasIsland`, use **`FloatingIslandSavedData.peek` / `getOrCreate`** and require **`AVAILABLE`**. If two players race the same key, **atomic** transition (`AVAILABLE` → `CLAIMED` only if still free) and **retry** the next region. **Teleport target:** stand on a solid top **near the island’s procedural center** — reuse **`FloatingIslandLayout.regionIsland`** horizontal **`centerX` / `centerZ`** and the same vertical basis as **`IslandHudServerSync`** (`peakSurfaceYAtIslandCenter` / `columnTopY`), so the starter spawn lines up under the **HUD beacon**, not the **rim** (today’s **`FloatingIslandsSpawnEvents`** uses a **chunk** Chebyshev spiral + first hit at sample offsets, which is only a **void-rescue** heuristic). **Chunks:** surface YXZ can be resolved from **layout math** before chunks exist (`islandSurfaceBlockY` / `columnTopY`); optionally **load / ticket** chunks before teleport if you want decoration guaranteed immediately. **Dense servers:** config **max region search steps** (or radius); if exhausted, **documented fallback** (warn, kick with message, staff hook—pick one). Keep **`FloatingIslandsSpawnEvents`** for **void rescue** (bad `/tp`, dimension travel, starter failure).
- **Additional islands:** no free teleport-to-claim. Prefer **airship / floating-base gameplay:** the player must **move** their owned island assembly into valid proximity of a target `AVAILABLE` island and satisfy a **dock / link** interaction (or equivalent server-checked state) before the extra region flips to `CLAIMED`. Document edge cases (void gaps, max link distance, unlink on abandon). _Depends on Phase 6 propulsion or a minimal MVP “nudge” placeholder until ships exist._

- [x] **New player → region-spiral `AVAILABLE` + atomic auto-claim + center teleport:** `FloatingIslandStarterPlacement` on **`PlayerLoggedIn`** (before void rescue); **`FloatingIslandSavedData`** `StarterHomes` map + **`tryClaimStarterIsland`**; spiral from **world spawn** (or join chunk); **center / HUD** teleport via `regionIsland` + `islandSurfaceBlockY`; config `starterIsland*`; **`IslandChunkLoader`** sync-loads a **3×3 chunk** neighborhood (`ChunkStatus.FULL`) before starter / void / respawn teleports.
- [ ] **Secondary claims — dock / link model:** design spec (distance, facing, blocks/entities, anti-exploit); server validation path; then implementation after Phase 2 + minimal movement MVP (or defer full rules until Phase 6).
- [ ] **Claim** action (non-starter): interaction after dock/link satisfied; record owner and timestamp; `setDirty()` on saved data; HUD refresh already exists.
- [x] **OP interim claim:** **`/projectisland island claim`** (permission 2) — **`trySecondaryClaim`** on feet **`AVAILABLE`** island (no `StarterHomes` entry); until dock/link + block interaction exist.
- [ ] **Invite / team** access (if alliances share an island): allow list or team id on claim data.
- [ ] **Config:** minimum distance between starter assignments, **max region-spiral steps** (or Chebyshev radius on **regions**) for starter search + **fallback** behavior when cap hit; starter anchor (spawn vs origin); link distance; debug logging.
- [ ] (Later) Client sync or UI for “your island” / borders — only after server rules are correct. _(Nearby island **state** labels already exist; dedicated “your island” / border UX still TBD.)_

## Phase 5 — Capture / PvP meta

- [ ] Design **win conditions** for capture (e.g. break anchor after siege timer, kill near flag, interact with capture block).
- [ ] Implement **ownership transfer** and audit trail (log or event).
- [ ] **Anti-abuse**: offline protection policy, raid windows, or explicit “steal allowed” server mode (document in README).
- [ ] **Alliances**: team IDs on claims; friendly fire and permission rules.
- [ ] **Travel mix:** **Primary** loop assumes players eventually **move their island** (airship) to pressure or capture neighbors. **Bridges** and early **limited mobility** items remain valid for **early game** or **very close** spawns—tune so they do not obsolete sails/propellers/jets unless that is an explicit server mode.

## Phase 6 — Propulsion, tech tree, and island airships

- [ ] **Design pillar:** mobility exists to enable **island-vs-island** positioning and **capture** gameplay, not only cosmetics.
- [ ] **Level 1 — Sails:** slow translation of the controlled island assembly; fuel or wind rules as designed.
- [ ] **Level 2 — Propellers:** higher speed band; **sub-tiers** that raise thrust **caps**, max modules, or efficiency.
- [ ] **Level 3 — Jet engines:** top speed band; **sub-tiers** for thrust; stronger fuel or heat tradeoffs if desired.
- [ ] **Parts vocabulary:** **helm** (pilot / assemble interaction), **sails**, **propellers**, **jets**, **cogwheels** / kinetic dressing (often with **Create** where integrated).
- [ ] **Unlock / advancement tree:** gate recipes, block placement, or thrust caps with **server-respected** flags (vanilla `Advancement` triggers, custom criteria, or modded research—pick one approach and document).
- [ ] **Parallel tech tracks:** defense (turrets, shields, walls), **docking / merge** with allies, power, automation—each with staged unlocks aligned to PvP risk.
- [ ] **Fuel economy:** non-infinite propulsion (e.g. coal or tiered fuels); balance vs raid windows.
- [ ] **Research — [Create Aeronautics](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics)** ([Modrinth](https://modrinth.com/mod/create-aeronautics)): **NeoForge** **1.21.1**, depends on **[Create](https://modrinth.com/mod/create)** + **[Sable](https://modrinth.com/mod/sable)** (moving assemblies / “sub-levels”). **Source / issues:** [Creators-of-Aeronautics/Simulated-Project](https://github.com/Creators-of-Aeronautics/Simulated-Project) — read [LICENSE.md](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/main/LICENSE.md) before copying or redistributing; not the same repo as [Modders-of-Create/Create-Aeronautics](https://github.com/Modders-of-Create/Create-Aeronautics). Skim for **contraption + flight** patterns; decide fit vs **Valkyrien Skies** for **whole-island** translation and server load.
- [ ] Prototype against **Valkyrien Skies** and/or **Sable + Create (+ Aeronautics)** (or another chosen stack): what counts as one “island ship,” max block count, merge rules, **Iris** / shader caveats if relevant.

## Phase 7 — Integrations (optional)

- [ ] Once **Phase 6** narrows the stack, **pin** compatible mod versions and smoke-test (e.g. **Valkyrien Skies** + helm add-ons, **Create / Sable / Create Aeronautics**—research and prototype bullets live in **Phase 6**).
- [ ] **README:** compatibility matrix + known issues for whatever you ship.
- [ ] **Defer** heavy pack integration until Phases 2–5 and a **Phase 6** playable prototype exist.

## Phase 8 — Content

- [ ] Custom **items/blocks** as needed: fuels, **helm**, claim anchor, capture token, **sail / propeller / jet** blocks or entities (IDs, recipes, creative tabs).
- [ ] Resources and **lang** files for player-visible strings.
- [ ] **Advancement JSON** (if used) or equivalent unlock definitions; keep triggers **server-safe** where security matters.
- [ ] Server-driven behavior for any item that affects claims, capture, or **thrust unlocks** (do not rely on datapack-only tricks for security-sensitive rules).

## Phase 9 — Ops

- [ ] Document dedicated **server** setup: JVM flags, `server.properties` hints (view distance, simulation distance).
- [ ] **Backup** and restore procedure for world + config.
- [ ] Performance checklist: pregen, entity caps, mod count discipline, profiling passes (moving islands are especially sensitive).
- [ ] Update [CHANGELOG.md](CHANGELOG.md) for each release-worthy change set.
