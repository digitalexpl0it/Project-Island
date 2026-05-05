# Changelog — Project Island **mod** (CurseForge JAR)

Player- and host-facing notes for **`projectisland-<version>.jar`** from **`build/libs/`**. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

The repository root [`CHANGELOG.md`](../../CHANGELOG.md) is the full developer log (mod + modpack + docs). **Mirror or summarize** this file’s sections into each CurseForge **file** changelog when you upload a new JAR.

---

## [Unreleased]

_Planned changes after **0.1.0**._

---

## [0.1.0] — 2026-05-02

### Added

- **NeoForge 1.21.1** mod id **`projectisland`**: procedural **floating islands** overworld, **server-authoritative** island saved data, starter / rescue behavior tuned for void play.
- **Harpoon + rope anchors** — linking, tiered limits, stress/damage, **rope surfing**, linked-anchor mining vs rope HP; optional action-bar style toasts (server → client).
- **Island HUD** — server → client sync for beacons / navigation; optional **Waystones** activation merge and **Xaero** waypoint mirror behavior when those mods are installed.
- **Config** — `projectisland-common.toml` / `projectisland-client.toml` for worldgen, spawn tuning, HUD, rescue, and compat keys (see in-game or default configs on first run).
- **Optional integrations** — hooks for **Lootr**, **Wings Of Fire** (loot), **Realm RPG: Treasure Balloons**, **Friends&Foes**, **CNB**, **Skill Tree / Spell Engine** stack, etc., when corresponding mods are loaded (`neoforge.mods.toml` optional entries).
- **Bundled CC0 resource pack** — **Unshaded Blocks** (client; enable in **Resource packs**).

### Fixed

- **Dedicated server channel registration** for **`action_bar_toast`**: clientbound toast handler is dispatched without forcing the dedicated server classloader to load client-only UI classes during payload registration (same JAR must still be present on the server **`mods/`** folder).

### Notes

- **Modpack** releases, quest SNBT, and CurseForge **modpack** zips are separate from this **mod** project — see [`modpack/curseforge/`](../../modpack/curseforge/README.md).
