# Project Island — TODO / roadmap

Phased checklist for the NeoForge mod and dedicated server. Check items off as you complete them.

_Roadmap reviewed **2026-05-02** (**Phase 6** shelved — not planned ATM). Prior: **2026-05-03** Lootr manifest; **2026-05-02** RPG direction + WoF loot bridge; **2026-04-22** island HUD; **2026-04-30** rope surf + ore; **2026-04-25** FTB dev-progression._

When researching features, use **mods, datapacks, modpacks, and GitHub** as examples (see [README.md](README.md) — “Learning from existing work”); pin anything you depend on and respect licenses.

## Current focus

- **Phase 2 is closed.** **Phase 4** (live): shared starter hub, **rope / harpoon** ziplines (public links, tiered span/HP), **rope surfing**, anchor mining vs rope HP, island HUD + optional Waystones/Xaero, **larger islands** + **ore thinning**, **FTB Quests + ProgressiveStages** baseline (`examples/dev-progression/`). **Island “claims” / rope topology enforcement are retired** — see README / CHANGELOG. **Phase 6** (whole-island propulsion) is **shelved** — no active airship/propulsion roadmap.
- **Modpack direction:** position the official stack as a **high-fantasy RPG sky-islands** experience: magic + classes (pinned third-party mods), authored **FTB Quest** chapters, dungeon/content mods (including **non-primary** portal dimensions if needed), optional mounts (**Wings Of Fire** + GeckoLib in [MOD_LIST.md](MOD_LIST.md)), and UI for **player stats / overhead health** where a stable NeoForge 1.21.1 mod exists. **Implementation checklist:** [Modpack — High fantasy RPG](#modpack--high-fantasy-rpg) below.

## Out of scope for v1

- Full MMO-style matchmaking or cross-shard persistence.
- Portal-based lobby worlds as the **primary** way to reach gameplay (core loop is: join → overworld is already floating islands). **Exception:** optional **dungeon / instance dimensions** or modded portals for RPG content are **in scope** for the modpack as long as they are not the default route into the core island loop.
- Promising specific third-party mod versions before they are pinned and tested together.

---

## Phase 1 — Bootstrap

- [x] Pin **Minecraft** and **NeoForge** versions (document in `README.md` and Gradle).
- [x] Add NeoForge **MDK** / Gradle project to the repository.
- [x] Define mod id, Maven coordinates, and Java package namespace.
- [x] Author `mods.toml` (metadata, dependency block when integrations exist).
- [x] Verify `./gradlew build` from a clean clone (`runClient` / `runServer` available; run locally as needed).
- [x] (Optional) Minimal CI — run **`./gradlew build`** locally or add **`.github/workflows/*.yml`** on a fork; the public repo **gitignores** **`/.github/workflows/`** (see [AGENTS.md](AGENTS.md)).

## Phase 2 — Worldgen

- [x] Void-style world: no sea-level continent; islands suspended over void — **`minecraft:overworld`** uses `FloatingIslandsChunkGenerator` via built-in datapack (`data/minecraft/dimension/overworld.json`).
- [x] Procedural **floating islands** with spacing and size variance (tunable) — ellipsoid islands on a region grid (`FloatingIslandsChunkGenerator`).
- [x] **Biome-aware** surfaces: grass, sand, snow from biome **temperature**; trees/ores from vanilla **feature** step where it triggers; overworld datapack still uses **`minecraft:multi_noise`** preset **`minecraft:overworld`**, while **island land** biomes are **weighted per `FloatingIslandKey`** (see [docs — Island biome weights](docs/TECHNICAL_REFERENCE.md#island-biome-weights-common-config) and `islandBiomeWeight*` in `config/projectisland-common.toml`), not vanilla climate sectors.
- [x] No mandatory **portal**: players join **overworld** directly with floating-island terrain (Nether/End unchanged).
- [x] (Optional) **Spawn pregen:** common config `spawnPregenChunkRadius` (0 = off) + `spawnPregenChunksPerTick` — `FloatingIslandsSpawnPregen` loads a Chebyshev chunk neighborhood around shared spawn after level load.
- [x] **Void spawn mitigation:** `FloatingIslandsSpawnEvents` on **dimension change to overworld** and **player login** — nearest procedural island surface from XZ (`FloatingIslandsChunkGenerator.islandSurfaceBlockY`).
- [x] Document world type / dimension choice in `README.md`.

## Phase 3 — Island model

- [x] Stable **island ID** — `FloatingIslandKey` / `FloatingIslandLayout.islandOwningSurface` (coarse region grid aligned with worldgen). **Spatial bounds** still implicit from ellipsoid math (no separate AABB cache yet).
- [x] **Persistence** — `FloatingIslandSavedData` (`projectisland_floating_islands.dat`) on the overworld `ServerLevel` when it uses the floating chunk generator.
- [x] Island **states**: `AVAILABLE`, `CLAIMED` (owner UUID + time fields on `IslandRecord`), `CONTESTED` reserved.
- [x] Serialization **version** field on saved file (`Version` int); expand when migrating rows.
- [x] **Nearby island HUD** — server sync of island state for a radius around the player in the floating-islands overworld (`IslandHudServerSync` on **`ServerTickEvent.Post`**, `ProjectIslandDimensions.isFloatingIslandsGameplay`); client **`IslandHudRenderer`** + `IslandHudWorldBillboard` on **`AFTER_TRANSLUCENT_BLOCKS`**; common/client config (`islandHud*` keys). **Read-only** navigation / labels — **not** a claim UI (claiming removed).
- [x] **Island HUD v2 (2026-04-22):** procedural display names (`FloatingIslandDisplayName` + word lists, deterministic per region), billboard **panel** (translucent dark fill + tinted border + state **item icon** + title / status / id lines) via `IslandHudWorldBillboard` + extended `IslandHudBeacon` payload.
- [ ] **Island HUD polish (optional):** custom client font / RGB panel tint. **Done in-repo:** JSON **`adjectives` / `nouns`** via `data/projectisland/floating_island_display_names/names.json` + **`/reload`**; name-only billboard (**outline + shadow**, faint panel); **`islandHudPanelScale`** + **`islandHudPanelFillOpacity`** in `projectisland-client.toml`. ~~Icon resource pack~~ removed from active HUD (**`examples/island_hud_icons_resource_pack/`** is obsolete).

## Phase 4 — Spawn and claims

**Design intent**

- **Starter island (one per player, free):** on **first join** to the floating-islands overworld, pick an **`AVAILABLE`** `FloatingIslandKey`, **auto-claim** it (**CLAIMED** + owner) **once per UUID** (idempotent on relog—do not re-roll or overwrite others). **Placement search:** spiral in **region coordinates** `(regionX, regionZ)` from a configurable anchor (e.g. **world spawn / origin**), skip `!FloatingIslandLayout.regionHasIsland`, use **`FloatingIslandSavedData.peek` / `getOrCreate`** and require **`AVAILABLE`**. If two players race the same key, **atomic** transition (`AVAILABLE` → `CLAIMED` only if still free) and **retry** the next region. **Teleport target:** stand on a solid top **near the island's procedural center** — reuse **`FloatingIslandLayout.regionIsland`** horizontal **`centerX` / `centerZ`** and the same vertical basis as **`IslandHudServerSync`** (`peakSurfaceYAtIslandCenter` / `columnTopY`), so the starter spawn lines up under the **HUD beacon**, not the **rim** (today's **`FloatingIslandsSpawnEvents`** uses a **chunk** Chebyshev spiral + first hit at sample offsets, which is only a **void-rescue** heuristic). **Chunks:** surface YXZ can be resolved from **layout math** before chunks exist (`islandSurfaceBlockY` / `columnTopY`); optionally **load / ticket** chunks before teleport if you want decoration guaranteed immediately. **Dense servers:** config **max region search steps** (or radius); if exhausted, **documented fallback** (warn, kick with message, staff hook—pick one). Keep **`FloatingIslandsSpawnEvents`** for **void rescue** (bad `/tp`, dimension travel, starter failure).
- **Additional islands:** no free teleport-to-claim. ~~Prefer **airship / floating-base gameplay**~~ **Superseded:** **Phase 6** (moving assemblies) is **shelved**; ropes are **public ziplines** without topology claims — see completed Phase 4 items + CHANGELOG. _Historical:_ dock/link model — **[docs/phase4-dock-link-spec.md](docs/phase4-dock-link-spec.md)** (superseded).
- **Rope topology (v1 — design):** **Retired** with claims (`RopeTopology` removed). Hub/spoke rules were documented in **[docs/phase4-dock-link-spec.md](docs/phase4-dock-link-spec.md)** before ziplines-only; ignore for current implementation.

- [x] **New player → shared starter hub (or spiral after spawn moves):** `FloatingIslandStarterPlacement` on **`PlayerLoggedIn`** (before void rescue); **`FloatingIslandSavedData`** `StarterHomes` + optional **`SharedStarterHub`** / spawn baseline; **shared hub** default (**`starterIslandSharedHub`**); **per-player spiral** when **`starterIslandSplitWhenWorldSpawnMoves`** detects **`/setworldspawn`** XZ change or legacy multi-starter data; **`tryClaimStarterIsland`** / **`tryAssignStarterHomeAtSharedHub`**; spiral from **world spawn** (or join chunk); **`IslandChunkLoader`** sync-loads **3×3** **`ChunkStatus.FULL`** before teleports.
- [x] ~~**Secondary claims — dock / link model**~~ **Retired:** ropes are public ziplines; **`IslandSecondaryClaim`**, **`/projectisland island claim`**, topology, and auto-claim removed. Historical spec (superseded): **[docs/phase4-dock-link-spec.md](docs/phase4-dock-link-spec.md)**.
- [x] ~~**Rope topology enforcement**~~ **Retired:** **`RopeTopology`** deleted; harpoon only checks span / island surface / distinct regions.
- [x] **Rope stress + tiers (server):** per-link **health / tension / strain**. **Done:** health + strain snap; **advancement-driven rope tiers** (`RopeProgression`: `projectisland:progression/rope_reinforced` / `rope_steel` scale max length + max health); **existing links auto-upgrade** on a server interval (`RopeLinkProgressionUpgrade`, preserves health fraction; `ropeProgressionUpgradeExistingLinks` / `ropeProgressionUpgradeIntervalTicks`). **Still open:** extra tiered caps beyond length/health, item-tier hooks beyond advancements if desired.
- [x] **Player-facing progression (FTB Quests + ProgressiveStages) — baseline:** ship quest chapters **`project_island.snbt`** + **`rpg_series.snbt`** (Skill Tree / Spell Engine / class gear — gated from **Iron Age**) + **`pi_*` stage** TOMLs + **`triggers.toml`** (vanilla advancement hooks, harpoon item, rope-tier advancements → stages). **Source of truth:** `examples/dev-progression/` (copied into Gradle **`run-client`** / **`run-server`** `config/` for dev). **Still open:** call **ProgressiveStages** (or shared flags) from **Project Island Java** to gate PI mechanics; tune/remove stock **`iron_age` / `diamond_age`** demo stages when the RPG chapter set is finalized ([Modpack — High fantasy RPG](#modpack--high-fantasy-rpg)).
- [x] **Rope HUD (client) — MVP:** **`RopeLinkSyncPayload`** carries **health fraction**; **`RopeLinkHealthBarRenderer`** billboard bars at **both** anchor ends (`ropeLinkHealthBarsShow`). Optional numeric **tension** text / upgrades later.
- [x] **Rope surfing (server):** empty-hand use (non-sneak) on a linked anchor moves the player along **`RopeCurveUtil`** sag toward the peer anchor; config **`ropeTraversalSurf*`**; void rescue skips while surfing; **`RopeTraversalEvents`** registers tick + lifecycle.
- [x] **Linked anchor mining:** survival breaks on a linked anchor damage **`RopeLink`** HP (`RopeAnchorMining`, **`ropeAnchorLinkDamagePerDigTick`**); immediate **`RopeLinkServerSync.sendRopeLinkSyncForLevel`** for HUD.
- [x] **Larger procedural islands:** **`floatingIslandHorizontalRadiusBonus`** + base **`hr`** bump in **`FloatingIslandLayout`** (flatter tops for structures).
- [x] **Ore vein thinning (floating overworld):** post-decoration pass **`FloatingIslandsOreThinning`** + per-material **`floatingIslandsOreMultiplier*`** (0–1 keep probability).
- [x] **Client rope mesh:** **`RopeLinkSegmentRenderer`** uses **`RopeCurveUtil`** for sag/attachment (matches surf); UV constants tuned for readable chain texture.
- [x] ~~**Claim** action (non-starter)~~ **Retired** with topology / secondary-claim removal (see CHANGELOG \[Unreleased\]).
- [x] ~~**Interim command + anchor claim**~~ **Retired** — **`projectisland island claim`** removed.
- [ ] **Invite / team** access (if alliances share an island): allow list or team id on claim data.
- [x] **Config:** minimum distance between starter assignments (`starterIslandMinRegionSeparation`), Chebyshev **region** search cap (`starterIslandMaxRegionSearchRadius`), **fallback** when exhausted (`starterIslandFailureKickMessage` or warn), starter anchor (**world spawn** / **join chunk** / **world origin** via `starterIslandSearchFromWorldOrigin` + `starterIslandSearchFromWorldSpawn`), `debugLogging` for assignment trace. Rope **link** span remains `ropeLinkMaxLengthBlocks` / `ropeLinkRaycastRangeBlocks`.
- [ ] (Later) Client sync or UI for “your island” / borders — only after server rules are correct. _(Nearby island **state** labels already exist; dedicated “your island” / border UX still TBD.)_

## Phase 5 — Capture / PvP meta

- [ ] Design **win conditions** for capture (e.g. break anchor after siege timer, kill near flag, interact with capture block).
- [ ] Implement **ownership transfer** and audit trail (log or event).
- [ ] **Anti-abuse**: offline protection policy, raid windows, or explicit “steal allowed” server mode (document in README).
- [ ] **Alliances**: team IDs on claims; friendly fire and permission rules.
- [ ] **Travel mix:** pressure neighbors via **ropes**, **mounts**, **Create** builds, and future capture rules — **not** whole-island flight mods (**Create Aeronautics** / **Valkyrien Skies** are out of scope for the current pack). Tune so optional vanilla-adjacent mobility does not obsolete intentional progression.

## Phase 6 — Moving islands / propulsion *(shelved)*

_Not on the active roadmap._ The pack targets **RPG + floating islands** with **ropes**, **mounts**, **Create** contraptions, and **Lootr**—not whole-island airships. **Create Aeronautics**, **Sable**, and **Valkyrien Skies** stay out of scope ([MOD_LIST.md](MOD_LIST.md)).

- [ ] **Reopen only if** capture / territory design (Phase 5) explicitly needs **translating entire islands** and you budget for **custom** mechanics or a **deliberate new** mod choice (still unlikely to be VS/Aero unless plans change).
- **Design archive:** [README — Propulsion tiers](README.md#propulsion-tiers-design-reference-only--not-vs--create-aeronautics) is kept as **reference only** (sails → jets table), not a commitment to implement.

**Progression UI** lives in Phase 4 + [Modpack — High fantasy RPG](#modpack--high-fantasy-rpg) — expand **FTB Quests** / **ProgressiveStages** for **RPG and island hooks**, not propulsion-tier chapters.

## Phase 7 — Integrations (optional)

- [ ] **Pin + smoke-test** whatever optional mods you ship beyond [MOD_LIST.md](MOD_LIST.md) (**Create** stays; **not** VS / Aeronautics unless direction changes).
- [x] **Biomes O' Plenty:** optional weighted **`biomesoplenty:*`** island biomes when mod **`biomesoplenty`** is present — **`islandBiomeModIntegrationEnabled`**, **`islandBiomeModWeightedEntries`** ([docs — Island biome weights](docs/TECHNICAL_REFERENCE.md#island-biome-weights-common-config)).
- [ ] **README:** compatibility matrix + known issues for whatever you ship.
- [ ] **Defer** heavy optional integration until Phases **2–5** and **modpack** milestones feel stable.

## Phase 8 — Content

- [ ] Custom **items/blocks** as needed for **RPG / capture / islands** (e.g. claim token, harpoon-adjacent gear, fuels for Create)—**not** propulsion-airship parts unless Phase 6 is reopened.
- [ ] Resources and **lang** files for player-visible strings.
- [ ] **Advancement JSON** (if used) or equivalent unlock definitions; keep triggers **server-safe** where security matters.
- [ ] Server-driven behavior for any item that affects claims, capture, or **gated unlocks** (do not rely on datapack-only tricks for security-sensitive rules).

## Modpack — High fantasy RPG

_Checklist for the official NeoForge **1.21.1** modpack (see [MOD_LIST.md](MOD_LIST.md)). Pin versions only after multiplayer smoke tests; respect third-party licenses when copying quest SNBT or assets._

- [x] **Magic (baseline):** **Spell Engine** (+ RPG Series class mods) pinned in [MOD_LIST.md](MOD_LIST.md); FTB **`rpg_series`** chapter introduces binding table + spells. **Still open:** deeper balance vs **Create** / **WoF** / ropes; document conflicts when bumping versions.
- [x] **Classes / identity:** **Skill Tree (RPG Series)** stack pinned in [MOD_LIST.md](MOD_LIST.md) (see **RPG Series — Skill Tree stack**). **Still open:** align **server-side** gates with **ProgressiveStages** (or PI flags) if you lock classes/progression; balance vs **void / ropes / WoF** after combat smoke tests.
- [ ] **Combat & bosses:** shortlist encounter mods that behave well on **floating terrain** and with spawn tuning (`floatingIslandsSpawnTuning*` / bypass namespaces); avoid duplicate guard mods ([MOD_LIST.md](MOD_LIST.md) Villager Guards note).
- [ ] **Dungeons / dimensions:** optional portal mods, structure packs, or trial-adjacent content; gate entry via **FTB Quests** where appropriate.
- [ ] **FTB Quests:** grow beyond the **Project Island** + **`rpg_series`** baseline into a **full RPG arc** (tutorial → magic → islands → raids); reuse only **license-allowed** SNBT from other packs or write originals; sync rewards with **`pi_*`** / mod stages; add **server** stage checks tied to **RPG/island** milestones (not shelved propulsion tiers unless Phase 6 returns).
- [ ] **Loot curve:** align chest/table rewards and quest payouts with **ProgressiveStages** so power matches island PvE/PvP targets.
- [x] **Lootr (multiplayer chests):** pinned in [MOD_LIST.md](MOD_LIST.md); optional **`lootr`** in `neoforge.mods.toml` — **per-player** converted loot chests for RPG co-op (configure decay/refresh in Lootr config).
- [ ] **Player stats UI:** evaluate NeoForge **1.21.1** mods for **health / mana / attributes** display (HUD and/or **nametag-style** overhead bars); test on dedicated server + FTB Teams parties.
- [x] **Wings Of Fire mounts:** pin **GeckoLib** + **[Wings Of Fire!](https://modrinth.com/mod/wings-of-fire!)** in [MOD_LIST.md](MOD_LIST.md); optional dependency **`wings_of_fire`** in `neoforge.mods.toml`. **Loot bridge shipped in PI JAR:** extra global loot modifiers (conditional on WoF loaded) add WoF egg subtables to **village**, **simple_dungeon**, **jungle_temple**, and **trial_chambers** chests — see README bullet. **Still verify:** Nether routes for Red/Gilded eggs; with **Lootr**, egg chests should be **per-player** once converted (see Lootr docs).
- [ ] **Ambient creatures:** if design needs wild dragons/phoenixes (not only WoF summons), evaluate separate mob mods or datapack spawns; watch **`floatingIslandsSpawnTuningBypassEntityNamespaces`**. **Known:** [Mowzie's Mobs](https://modrinth.com/mod/mowzies-mobs) natural spawns often target **vanilla** biome rules — **BOP-only** island tops can look sparse for those mobs unless you extend tags in a datapack (see [MOD_LIST.md](MOD_LIST.md) *Mowzie's Mobs + Biomes O' Plenty*).
- [x] **Friends&Foes (optional content):** pinned row in [MOD_LIST.md](MOD_LIST.md); **`neoforge.mods.toml`** optional **`friendsandfoes`**. **Still open:** balance + structure oddities on floating terrain after field tests.

## Phase 9 — Ops

- [x] **Dev server OP:** document + ship **`dev-ops.example.json`** → **`run-server/ops.json`** for `./gradlew runServer` (game dir `run-server/`); console **`op <name>`** alternative.
- [ ] Document dedicated **server** setup: JVM flags, `server.properties` hints (view distance, simulation distance).
- [ ] **Backup** and restore procedure for world + config.
- [ ] Performance checklist: pregen, entity caps, mod count discipline, profiling passes (moving islands are especially sensitive).
- [ ] Update [CHANGELOG.md](CHANGELOG.md) for each release-worthy change set.
