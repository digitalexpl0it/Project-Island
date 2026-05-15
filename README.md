# Project Island

**Minecraft 1.21.1 · NeoForge** — a **void overworld** of **procedural floating islands**, built for **multiplayer-friendly survival** and a **mythical RPG** vibe (quests, loot, mounts, optional classes/skills when you use the official modpack). The **Project Island** mod drives **worldgen**, **harpoons & public rope ziplines**, **starter hubs**, **void rescue**, and an **island HUD** — with the **server** as the source of truth for anything that affects the world.

This repo contains the **mod source** and the **CurseForge modpack** layout. You can play from a **released pack**, run the mod alone in dev, or fork and tune configs.

---

## Who this README is for

| If you… | Start here |
|--------|------------|
| **Play** the official pack | Install from **CurseForge** (Modpacks). Use the **server additional file** if you host. |
| **Host** a server | [MOD_LIST.md](MOD_LIST.md) (what mods match the pack), [modpack/curseforge/README.md](modpack/curseforge/README.md) (zip layout), [modpack/curseforge/overrides/SERVER_README.md](modpack/curseforge/overrides/SERVER_README.md) (JARs you may need to add yourself). |
| **Tweak** quests / progression in dev | [examples/dev-progression/](examples/dev-progression/) — Gradle copies the FTB Quests tree into `run-client` / `run-server` when you use `./gradlew runClient` or `runServer`. |
| **Build** the mod or pack from source | [Building](#building) below. JDK **21** required. |
| **Go deep** on worldgen, every config key, ropes, biomes | [docs/TECHNICAL_REFERENCE.md](docs/TECHNICAL_REFERENCE.md) (long-form maintainer doc). |

---

## What you get (short)

- **Sky islands over the void** — explore, build, bridge the gaps.
- **Harpoon + rope links** — shared ziplines; **rope surfing** for fast travel.
- **Server-driven** starters, HUD hints, and rescue when you fall — consistent for everyone on the world.
- **Optional integrations** (Lootr, Waystones, Wings Of Fire, RPG-series mods, etc.) when you use the curated **modpack** — see [MOD_LIST.md](MOD_LIST.md).

**Roadmap & design phases:** [TODO.md](TODO.md). **What changed in each release:** [CHANGELOG.md](CHANGELOG.md).

---

## Modpack vs standalone mod

| Artifact | What it is |
|----------|------------|
| **`./gradlew jar`** → `build/libs/projectisland-*.jar` | The **mod** only (worldgen + features). Use on clients **and** dedicated servers. |
| **`./gradlew curseforgeModpackZip`** | **Client modpack** zip for CurseForge — `manifest.json` pins exact mod **file** versions; see [modpack/curseforge/README.md](modpack/curseforge/README.md). |
| **`./gradlew curseforgeServerPackZip`** | **Server pack** zip — fat `overrides/mods/` for hosts; read [modpack/curseforge/README.md](modpack/curseforge/README.md) for what is omitted vs client. |

**Paste text for CurseForge upload notes:** [modpack/curseforge/CURSEFORGE_UPDATE.md](modpack/curseforge/CURSEFORGE_UPDATE.md).

---

## Dev quick start

1. Clone the repo (JDK **21** installed).
2. **`./gradlew build`** — produces the mod JAR under `build/libs/`.
3. **`./gradlew runClient`** or **`./gradlew runServer`** — first run downloads game assets (slow once).

To match the **official modpack mod list** in your local `run-client/mods` and `run-server/mods` folders:

```bash
./gradlew syncManifestModJarsToDevRuns
```

That **downloads** whatever is pinned in `modpack/curseforge/manifest.json`. It does **not** remove extra JARs you dropped in by hand, and when CurseForge **renames** a file between versions (e.g. two **Balm** jars), **both** can sit in `mods/` until you delete the old one — see [Duplicate mods in `run-*/mods`](#duplicate-mods-in-runmods).

---

## Duplicate mods in `run-*/mods`

You might see **two** files for the “same” mod (e.g. `balm-neoforge-1.21.1-21.0.56.jar` and `…21.0.57.jar`). That usually means:

1. The **manifest** still pins one CurseForge **file** (one exact filename — e.g. **.56** for Balm project **531761**, file **7420963**), **and**
2. A **newer** build was copied in manually (e.g. from **Modrinth** as **.57**), or an **old** file was left behind after a pin bump.

Minecraft will try to load **both**; remove the jar you are **not** intentionally using so only one remains. Align optional extras with [MOD_LIST.md](MOD_LIST.md). Maintainers: after bumping pins, run sync and **delete** stale filenames.

---

## Building

```bash
./gradlew build          # mod JAR
./gradlew runClient      # dev client
./gradlew runServer      # dev server — see docs/TECHNICAL_REFERENCE.md if commands say “unknown” (OP / ops.json)
./gradlew curseforgeModpackAll   # client + server pack zips → build/dist/
```

---

## Learning from other mods & packs

When you add mechanics or UI, borrow ideas from **public** mods, datapacks, and packs — **check licenses** and pin versions. This project started from the [NeoForge 1.21.1 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle). NeoForge docs: [Getting Started](https://docs.neoforged.net/docs/1.21.1/gettingstarted/).

---

## License

**Mod code:** [MIT](LICENSE). Bundled assets (e.g. **Unshaded Blocks**) keep their own licenses under [licenses/](licenses/). Third-party mods in the pack keep **their** licenses — see each mod’s page.

---

_For propulsion tiers, biome weight tables, rope config keys, architecture diagram, and FTB dev notes, see **[docs/TECHNICAL_REFERENCE.md](docs/TECHNICAL_REFERENCE.md)**._
