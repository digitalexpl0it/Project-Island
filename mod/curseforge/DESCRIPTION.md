# Project Island

**Survival in the sky** — a **void overworld** of **procedural floating islands** to explore, settle, and connect with **harpoons and ropes**. Built for **NeoForge 1.21.1**; world rules stay **fair in multiplayer** because the **server** is the source of truth.

---

## Quick facts

| | |
|:---|:---|
| **Minecraft** | **1.21.1** |
| **Mod loader** | **NeoForge** (use the file that matches your game) |
| **Where it runs** | **Single-player, LAN, and dedicated servers** — same mod on client and server |

---

## About

Forget the endless flat overworld. **Project Island** generates **floating islands** over the **void**: each island is its own chunk of terrain and biomes, ready for bases, farms, and adventure. Jump between peaks, bridge the gaps, or **ride the ropes** you and your friends string between anchors.

Whether you play solo or on a server, **islands, starters, rope links, and navigation hints** stay in sync so everyone sees the **same world** — no client-only ghost islands.

---

## Features

### Islands & world

- **Void overworld** — the overworld is **sky and islands**, not an infinite sea-level plane.
- **Procedural islands** — varied terrain and biomes per island; good footing for building and exploring.
- **Dramatic undersides** — islands can grow **root-like spikes** under the main mass for a sharper silhouette (hosts can turn this off for a classic smooth look in config).

### Harpoons, ropes & travel

- **Harpoon + anchors** — shoot anchors, **link** them with rope, and manage **span and wear**.
- **Rope surfing** — speed along linked lines for **zipline-style** travel.
- **Public rope links** — built for **shared** crossings, not a per-player land-claim layer.

### Help while you explore

- **Island HUD** — **server-synced** hints so you can tell where you are above the void. Works even better if you add **[Waystones](https://www.curseforge.com/minecraft/mc-mods/waystones)** or **[Xaero’s Minimap](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap)** (optional).

### Endgame & polish

- **Void rescue** — configurable safety net when players fall too far; hosts decide how forgiving it is.
- **Optional Ender Dragon flow** — server-tunable **respawn / countdown** style behavior for groups who want the End to stay lively (see the mod changelog for details).

### Look & feel (client)

- **Unshaded Blocks** — a **built-in resource pack** (flatter block shading, **CC0**) ships with the jar. Enable it under **Options → Resource packs** if you like that look.

---

## Works great with (optional)

These mods are **not** inside the Project Island jar. Install **NeoForge 1.21.1** builds from their own pages if you want the extras:

**Exploration & loot**

- [Lootr](https://www.curseforge.com/minecraft/mc-mods/lootr) — per-player chests
- [Wings Of Fire!](https://www.curseforge.com/minecraft/mc-mods/the-wings-of-fire) — use this listing (**not** the unrelated *wings-of-fire* project on CurseForge)
- [Realm RPG: Treasure Balloons](https://www.curseforge.com/minecraft/mc-mods/realm-rpg-treasure-balloons)

**Creatures & structures**

- [Friends&Foes](https://www.curseforge.com/minecraft/mc-mods/friends-and-foes-forge)
- [Creatures and Beasts: Continued](https://www.curseforge.com/minecraft/mc-mods/creatures-and-beasts-continued)

**RPG-style gameplay (examples)**

- [Skill Tree](https://www.curseforge.com/minecraft/mc-mods/skill-tree), [Spell Engine](https://www.curseforge.com/minecraft/mc-mods/spell-engine), [Spell Power Attributes](https://www.curseforge.com/minecraft/mc-mods/spell-power), [Pufferfish's Skills](https://www.curseforge.com/minecraft/mc-mods/puffish-skills)
- [Archers](https://www.curseforge.com/minecraft/mc-mods/archers), [Paladins & Priests](https://www.curseforge.com/minecraft/mc-mods/paladins-and-priests), [Rogues & Warriors](https://www.curseforge.com/minecraft/mc-mods/rogues-and-warriors), [Wizards](https://www.curseforge.com/minecraft/mc-mods/wizards)

**World variety**

- [Biomes O’ Plenty](https://www.curseforge.com/minecraft/mc-mods/biomes-o-plenty) — extra surface biomes on islands when the mod is present (host-tunable).

---

## Mod config files

After the first launch you get two TOML files in **`config/`**. **Every setting has an inline comment** next to it — that’s the full reference for names and defaults.

| File | Who uses it | What it’s for |
|:---|:---|:---|
| **`projectisland-common.toml`** | **Server** (including the host in single-player) | **World generation** — island size and frequency, biomes (including modded surfaces), structures and villages, ores and decoration, **mob spawn tuning**, **starter islands**, **void rescue**, **rope rules** (length, health, surfing, limits), **HUD sync** to clients, and optional hooks when other mods are installed. |
| **`projectisland-client.toml`** | **Your game client only** | **How things look** — island HUD style, optional **Xaero’s** waypoint mirroring, **rope line** and health-bar drawing. Ignored on a headless dedicated server. |

**Tip for hosts:** change **`common`** when you want the **world** or **rules** to change for everyone. Change **`client`** when you only want to adjust **your own** overlays and visuals.

---

## License

- **Project Island** (mod code): **MIT**
- **Unshaded Blocks** (bundled resource pack assets): **CC0**
