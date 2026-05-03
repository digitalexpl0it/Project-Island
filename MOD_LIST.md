# Project Island — required mod list (modpack)

This file is the **authoritative manifest** for the **official Project Island modpack** on **NeoForge 1.21.1**. Match **Minecraft**, **NeoForge**, and **every JAR below** (same filenames / versions) on **dedicated server** and **every client**, plus the **Project Island** build JAR from this repository.

- **Loader / game version:** see [README.md](README.md) — *Pinned toolchain*.
- **Where to put JARs for Gradle dev runs:** your local **`run-client/mods/`** and **`run-server/mods/`** (those game dirs are **gitignored**). Keep those folders **in sync** with this list when you bump versions.
- **Project Island itself:** `build/libs/projectisland-<version>.jar` from `./gradlew build` (not always copied into `run-*/mods`; the MDK run injects the mod from sources).
- **Villager defense:** **[Villager Guards](https://modrinth.com/mod/villager-guards)** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/villager-guards)), NeoForge **1.21.1**, is listed **first** below. *(**Guard Villagers – Autonomous Villager Defense** was dropped from the manifest — it did not work in this pack stack; avoid redundant guard/conversion mods alongside Villager Guards.)*

## Required third-party mods (pinned filenames)

| JAR (exact) | Role | Side |
|-------------|------|------|
| `villager-guards-v1.1.5.jar` | **[Villager Guards](https://modrinth.com/mod/villager-guards)** **v1.1.5** — armed guards defend villagers (configurable; includes player-attack options in recent releases). License: **AGPL-3.0** (see Modrinth). | **Client + server** |
| `architectury-13.0.8-neoforge.jar` | Shared API / bridge for several mods | **Client + server** |
| `cloth-config-15.0.140-neoforge.jar` | Config screens / API for mods that depend on it | **Client + server** |
| `create-1.21.1-6.0.10.jar` | **[Create](https://modrinth.com/mod/create)** (kinetic / contraptions / tech fantasy) — **not** paired with **Create Aeronautics** in this pack | **Client + server** |
| `ftb-library-neoforge-2101.1.31.jar` | FTB Library (base for FTB mods) | **Client + server** |
| `ftb-teams-neoforge-2101.1.10.jar` | FTB Teams | **Client + server** |
| `ftb-xmod-compat-neoforge-21.1.8.jar` | Cross-mod compatibility layer for FTB stack | **Client + server** |
| `ftb-quests-neoforge-2101.1.24.jar` | FTB Quests (progression UI) | **Client + server** |
| `progressivestages-1.4.jar` | ProgressiveStages (server-checkable stages; used with quests) | **Client + server** |
| `jei-1.21.1-neoforge-19.27.0.340.jar` | Just Enough Items (recipe / item lookup) | **Client + server** *(dedicated server may omit only if you accept missing recipe UI parity; official pack keeps it on both.)* |
| `xaerominimap-neoforge-1.21.1-25.3.10.jar` | Xaero’s Minimap | **Client + server** *(official pack ships both; many servers keep it for version parity.)* |
| `xaeroworldmap-neoforge-1.21.1-1.40.11.jar` | Xaero’s World Map | **Client + server** *(same as minimap.)* |
| `lootr-neoforge-1.21.1-1.11.37.119.jar` | **[Lootr](https://www.curseforge.com/minecraft/mc-mods/lootr)** ([Modrinth](https://modrinth.com/mod/lootr)) **1.11.37.119** — **per-player** loot for converted chests/barrels/carts (multiplayer-friendly dungeon loot); configure decay/refresh in `config/lootr-common.toml` / wiki. | **Client + server** |
| `geckolib-neoforge-1.21.1-4.8.4.jar` | **[GeckoLib](https://modrinth.com/mod/geckolib)** **4.8.4** — animation/render library (**Wings Of Fire** expects a compatible 1.21.1 NeoForge build; bump only after smoke-testing WoF). | **Client + server** |
| `Wings Of Fire V1.0 - NeoForge 1.21.1.jar` | **[Wings Of Fire!](https://modrinth.com/mod/wings-of-fire!)** **v1.0** — phoenix mounts from **petrified eggs** (structure chest loot + **Inferno Cradle** / **Tempered Lantern**); not ambient mob spawns. License: **ARR** (Modrinth). Mod id: **`wings_of_fire`**. | **Client + server** |

**Note:** When bumping Villager Guards, pick the **NeoForge** build for **1.21.1** from the **[Modrinth versions page](https://modrinth.com/mod/villager-guards/versions)** and update this table’s filename if it changes.

### RPG Series — Skill Tree stack (classes + skills + spells)

**Pinned together for NeoForge 1.21.1** (resolved from [Skill Tree (RPG Series)](https://modrinth.com/mod/skill-tree) **1.4.4+1.21.1** plus required class mods and transitive libraries via Modrinth **required** dependencies). **Skill Tree** mod id: **`skill_tree_rpgs`**. Several RPG Series mods are **ARR**; **Spell Engine** is **GPL-3.0** — see each Modrinth page before redistributing.

**Also required from the core table:** **`cloth-config-15.0.140-neoforge.jar`** (already listed above).

| JAR (exact) | Role | Side |
|-------------|------|------|
| `skill_tree-neoforge-1.4.4+1.21.1.jar` | **[Skill Tree (RPG Series)](https://modrinth.com/mod/skill-tree)** — skill trees for the classes below. | **Client + server** |
| `puffish_skills-0.17.3-1.21-neoforge.jar` | **[Pufferfish's Skills](https://modrinth.com/mod/skills)** — XP / skill points foundation. | **Client + server** |
| `archers-neoforge-2.7.0+1.21.1.jar` | **[Archers (RPG Series)](https://modrinth.com/mod/archers)** | **Client + server** |
| `paladins-neoforge-2.7.1+1.21.1.jar` | **[Paladins \& Priests (RPG Series)](https://modrinth.com/mod/paladins-and-priests)** | **Client + server** |
| `rogues-neoforge-2.7.0+1.21.1.jar` | **[Rogues \& Warriors (RPG Series)](https://modrinth.com/mod/rogues-and-warriors)** | **Client + server** |
| `wizards-neoforge-2.7.1+1.21.1.jar` | **[Wizards (RPG Series)](https://modrinth.com/mod/wizards)** | **Client + server** |
| `spell_engine-neoforge-1.9.9+1.21.1.jar` | **[Spell Engine](https://modrinth.com/mod/spell-engine)** — spell runtime (GPL-3.0). | **Client + server** |
| `spell_power-neoforge-1.4.6+1.21.1.jar` | **[Spell Power](https://modrinth.com/mod/spell-power)** | **Client + server** |
| `ranged_weapon_api-neoforge-2.3.3+1.21.1.jar` | **[Ranged Weapon API](https://modrinth.com/mod/ranged-weapon-api)** | **Client + server** |
| `shield_api-neoforge-2.2.0.jar` | **[Shield API](https://modrinth.com/mod/shield-api)** | **Client + server** |
| `runes-neoforge-1.2.1+1.21.1.jar` | **[Runes](https://modrinth.com/mod/runes)** | **Client + server** |
| `structure_pool_api-neoforge-1.2.1+1.21.1.jar` | **[Structure Pool API](https://modrinth.com/mod/structure-pool-api)** | **Client + server** |
| `bundle-api-neoforge-1.1.0.jar` | **[Bundle API](https://modrinth.com/mod/bundle-api)** | **Client + server** |
| `accessories-neoforge-1.1.0-beta.53+1.21.1.jar` | **[Accessories](https://modrinth.com/mod/accessories)** | **Client + server** |
| `azurelibarmor-neo-1.21.1-3.1.3.jar` | **[AzureLib Armor](https://modrinth.com/mod/azurelib-armor)** | **Client + server** |
| `player-animation-lib-forge-2.0.4+1.21.1.jar` | **[Player Animator](https://modrinth.com/mod/playeranimator)** | **Client + server** |
| `forgified-fabric-api-0.116.7+2.2.4+1.21.1.jar` | **[Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api)** | **Client + server** |
| `owo-lib-neoforge-0.12.15.5-beta.1+1.21.jar` | **[oωo (owo-lib)](https://modrinth.com/mod/owo-lib)** | **Client + server** |

**RPG stack bumps:** update **Skill Tree** first, then re-resolve **required** versions on Modrinth for each sibling mod (classes + `spell_engine` + `puffish_skills`) so game versions and loaders stay aligned.

**Not in the official pack (by design):** **[Create Aeronautics](https://modrinth.com/mod/create-aeronautics)** (and its **Sable** dependency) and **[Valkyrien Skies](https://modrinth.com/mod/valkyrien-skies)** — whole-island flight / rigid assemblies are **not** targeted with those mods right now. Remove their JARs from `run-*/mods` if you still have old copies.

**Wings Of Fire + Project Island:** the WoF mod adds its own global loot modifiers for vanilla tables (mansions, outposts, mineshafts, nether structures, etc.). This repository also ships **optional bridge loot modifiers** (under `data/projectisland/loot_modifiers/`, registered when **`wings_of_fire`** is loaded) that add the same egg loot rolls to **village** chests, **`minecraft:chests/simple_dungeon`** (monster-room style dungeons), **jungle temples**, and **trial chambers** chests — structures that tend to appear on floating islands. Red / Gilded eggs still primarily come from **Nether** loot as in WoF’s docs.

**Lootr:** converts eligible vanilla loot containers to **per-player** chests (multiplayer RPG-friendly). Loot rolls when **each** player opens; tune decay/refresh in **`config/lootr-common.toml`**. New chunks / conversions apply going forward — existing worlds may need exploration or **`/lootr`** commands; verify pacing with WoF + FTB rewards after enabling.

## Optional / not in this list

- **Shader packs, HD resource packs** — player-installed; see [README.md](README.md) *Resource packs*.
- **Extra QoL** (e.g. inventory tweaks, map alternatives) — **not** part of the official manifest unless added here and to `run-*/mods`.

## Progression data (not mods)

Official pack expects the mirrored config from **`examples/dev-progression/`** — FTB **`quests/chapters/project_island.snbt`** (islands, WoF, harpoon) plus **`quests/chapters/rpg_series.snbt`** (Skill Tree **K**, Spell Engine **`spell_engine:spell_binding`**, starter class weapons/armor), and ProgressiveStages TOML. See [README.md](README.md) *Progress UI* and *Dev runs*.

## Updating this list

1. Add or bump JARs in your local **`run-client/mods`** and **`run-server/mods`** (not tracked in git).
2. Update **this file** and [README.md](README.md) if the pack intent changes.
3. Note the change in [CHANGELOG.md](CHANGELOG.md).

## License reminder

Third-party mods are subject to **their own licenses** (CurseForge / Modrinth / upstream repos). This repository **does not** redistribute their sources; only filenames and versions are recorded here for pack parity.

## FTB Library: pink “Hello from FTB Library!” in chat

That line is **not** controlled by `ftblibrary-server.snbt` / client config. In current FTB Library sources it is sent **only in dev** (`playerJoined` → `if (Platform.INSTANCE.isDev()) { … sendSystemMessage("Hello from FTB Library!") }`). So:

- **`./gradlew runServer`** / **`runClient`** (ModDevGradle / IDE): you will typically see it **every join** — expected.
- **Production** installs (launcher or dedicated server **not** flagged as a dev environment): you should **not** see it.

If it appears on a non-dev server, check that nothing is forcing a **development** environment (unusual). Disabling it without changing FTB Library would require a **mixin** or a **fork** of that mod — not recommended for a one-line dev ping.
