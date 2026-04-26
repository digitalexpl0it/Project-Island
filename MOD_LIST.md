# Project Island — required mod list (modpack)

This file is the **authoritative manifest** for the **official Project Island modpack** on **NeoForge 1.21.1**. Match **Minecraft**, **NeoForge**, and **every JAR below** (same filenames / versions) on **dedicated server** and **every client**, plus the **Project Island** build JAR from this repository.

- **Loader / game version:** see [README.md](README.md) — *Pinned toolchain*.
- **Where to put JARs for Gradle dev runs:** your local **`run-client/mods/`** and **`run-server/mods/`** (those game dirs are **gitignored**). Keep those folders **in sync** with this list when you bump versions.
- **Project Island itself:** `build/libs/projectisland-<version>.jar` from `./gradlew build` (not always copied into `run-*/mods`; the MDK run injects the mod from sources).

## Required third-party mods (pinned filenames)

| JAR (exact) | Role | Side |
|-------------|------|------|
| `architectury-13.0.8-neoforge.jar` | Shared API / bridge for several mods | **Client + server** |
| `cloth-config-15.0.140-neoforge.jar` | Config screens / API for mods that depend on it | **Client + server** |
| `create-1.21.1-6.0.10.jar` | Create (kinetic / contraptions) | **Client + server** |
| `sable-neoforge-1.21.1-1.1.3.jar` | Sable (moving assemblies; Create Aeronautics dependency) | **Client + server** |
| `create-aeronautics-bundled-1.21.1-1.1.3.jar` | Create Aeronautics (bundled) — flight / assemblies stack | **Client + server** |
| `ftb-library-neoforge-2101.1.31.jar` | FTB Library (base for FTB mods) | **Client + server** |
| `ftb-teams-neoforge-2101.1.10.jar` | FTB Teams | **Client + server** |
| `ftb-xmod-compat-neoforge-21.1.8.jar` | Cross-mod compatibility layer for FTB stack | **Client + server** |
| `ftb-quests-neoforge-2101.1.24.jar` | FTB Quests (progression UI) | **Client + server** |
| `progressivestages-1.4.jar` | ProgressiveStages (server-checkable stages; used with quests) | **Client + server** |
| `jei-1.21.1-neoforge-19.27.0.340.jar` | Just Enough Items (recipe / item lookup) | **Client + server** *(dedicated server may omit only if you accept missing recipe UI parity; official pack keeps it on both.)* |
| `xaerominimap-neoforge-1.21.1-25.3.10.jar` | Xaero’s Minimap | **Client + server** *(official pack ships both; many servers keep it for version parity.)* |
| `xaeroworldmap-neoforge-1.21.1-1.40.11.jar` | Xaero’s World Map | **Client + server** *(same as minimap.)* |

## Optional / not in this list

- **Shader packs, HD resource packs** — player-installed; see [README.md](README.md) *Resource packs*.
- **Extra QoL** (e.g. inventory tweaks, map alternatives) — **not** part of the official manifest unless added here and to `run-*/mods`.

## Progression data (not mods)

Official pack expects the mirrored config from **`examples/dev-progression/`** (FTB Quests chapters + ProgressiveStages TOML). See [README.md](README.md) *Progress UI* and *Dev runs*.

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
