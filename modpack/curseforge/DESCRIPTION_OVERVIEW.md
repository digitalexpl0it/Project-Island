# CurseForge description — paste-ready overview

Copy everything **below the horizontal rule** into your modpack’s **Description** field on CurseForge (skip repo-only notes above the rule). If headers break in their editor, flatten `####` to bold lines.

Keep this file in sync when you change the pack; use **[`CHANGELOG_CLIENT.md`](CHANGELOG_CLIENT.md)** and **[`CHANGELOG_SERVER.md`](CHANGELOG_SERVER.md)** for per-release **Files** tab changelogs.

---

### Project Islands: Sky Bound

**Void islands · quests · RPG progression · NeoForge 1.21.1**

Survive and build on **procedural floating islands** in an empty overworld. Follow guided chapters in **FTB Quests**, unlock **skills and spells**, explore richer biomes, and travel with **Waystones**—solo or with friends. Built around the **Project Island** mod (worldgen + pack-aware QoL).

---

#### Highlights

- **Sky-world fantasy** — islands float in the void; no traditional overworld landmass.
- **Quest-led onboarding** — FTB Quests includes a **foundation** path plus an **RPG / Skill Tree / Spell Engine** branch after you hit the iron tier.
- **Loot that respects multiplayer** — **Lootr** for per-player dungeon chests where configured.
- **Exploration & identity** — **Biomes O’ Plenty**, **Wings Of Fire** mounts, class-flavored RPG mods (archers, rogues, paladins, wizards, etc.—see in-game **JEI**).
- **Travel & utility** — **Waystones**; optional performance/visual stack on **client** (Embeddium ecosystem, shader-ready stack—see client manifest).

---

#### Install — client

1. Open the **CurseForge App** (or compatible launcher), find **this pack**, install for **Minecraft 1.21.1** + **NeoForge**.
2. Allocate enough RAM for a medium RPG pack (often **6–8 GB** client as a starting point; tune for your machine).
3. **First launch:** open the **Quests** book—quest data ships with the pack. If something looks empty, confirm you updated to the latest **primary** file.

---

#### Install — dedicated server

1. On **Files**, open the same release version.
2. Download the **Additional file**: **`projectisland-modpack-<version>-curseforge-server.zip`** (not the primary client zip).
3. That archive uses a **trimmed manifest** (no JEI, minimap/world map, Embeddium/NeOculus stack, or Resource Pack Overrides—pure headless friendly).
4. Match **Minecraft** and **NeoForge** versions **exactly** to what the client pack uses for that release.

---

#### Version & compatibility

| | |
|--|--|
| **Minecraft** | **1.21.1** |
| **Loader** | **NeoForge** (see file label for exact loader build) |

Always install **matching** client + server files from the **same** release.

**Connection errors (“Project Island channel missing on the server”):** the dedicated server must load the **same** **Project Island** mod jar as the client. Use the official **server** additional zip (it merges **`overrides/mods/`**) or copy **`projectisland-*.jar`** from the client profile’s **`mods/`** into the server’s **`mods/`**.

---

#### Changelog

- **Client pack:** repo file **`modpack/curseforge/CHANGELOG_CLIENT.md`** (canonical bullets—mirror into each **primary** file changelog on CurseForge when you publish).
- **Server pack:** repo file **`modpack/curseforge/CHANGELOG_SERVER.md`** (same for the **Additional file**).

Players usually read changelogs on the **Files** tab per download; keep those short and dated.

---

#### License & attribution

- **Project Island** mod (bundled jar): source licensed **MIT** — see the project repository **`LICENSE`** if linked.
- **This modpack listing** curates third-party mods from **CurseForge** via **`manifest.json`**; each mod remains **© its authors** under **their** licenses. Nothing here relicenses those mods.
- Redistributing extra jars under **`overrides/mods/`** is subject to each author’s terms and CurseForge rules.

---

#### Bugs & feedback

Use your linked **Issues** / Discord / forum thread for pack bugs (link them in the project sidebar). For **crash logs**, attach **`latest.log`** from the failing side (client or server) and note **pack version** + **launcher**.

---

### Credits

**Project Island** — world concept, mod, and pack maintenance.

**Mod authors** — thank you for the libraries and content mods that make this pack possible; support them on their CurseForge pages.
