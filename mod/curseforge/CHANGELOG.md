# Changelog — Project Island **mod** (CurseForge JAR)

Player- and host-facing notes for **`projectisland-<version>.jar`** from **`build/libs/`**. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

The repository root [`CHANGELOG.md`](../../CHANGELOG.md) is the full developer log (mod + modpack + docs). **Mirror or summarize** this file into each CurseForge **file** changelog when you upload a new JAR.

**Version:** set **`mod_version`** in **`gradle.properties`** (or **`-Pmod_version=`**). Optional dev-only suffix: **`append_dev_timestamp_to_mod_version`** → **`mod_version+t<millis>`** on the output jar and **`mods.toml`**.

---

## [Unreleased]

_Planned changes after **0.1.2**._

---

## [0.1.2] — 2026-05-09

### Added

- **Starter supply chest** on first successful starter-home assignment (loot table **`projectisland:chests/starter_supply`**; config **`starterIslandSupplyChestEnabled`**). **`FloatingIslandSavedData`** persists **`StarterSupplyChests`** per island region.

### Fixed

- **FTB Quests (canonical `examples/dev-progression/ftbquests/`):** **WoF** dark/gilded bound lanterns use **`tempered_*_phoenixlantern`** ids (per **Wings Of Fire V1.0**); **Ride the Zipline** rewards use **1.21.1**-valid items (**breeze rod** + **feather**) instead of **wind charge**.

### Build

- **CurseForge modpack zips** run **`verifyDevProgressionFtbQuests`** first so missing chapter SNBT fails the build instead of shipping an empty quest book.

---

## [0.1.0] — 2026-05-02

### Added

- **NeoForge 1.21.1** mod id **`projectisland`**: procedural **floating islands** overworld, **server-authoritative** island saved data, starter / void-rescue / respawn behavior tuned for void play.
- **Harpoon + rope anchors** — linking, tiered span and rope health, stress/damage, **rope surfing**, linked-anchor mining vs rope HP; optional action-bar style toasts (server → client).
- **Island HUD** — server → client sync for nearby island beacons / navigation hints; optional **Waystones** integration and **Xaero’s Minimap** waypoint mirror when those mods are installed.
- **Config** — **`projectisland-common.toml`** (server + shared rules) and **`projectisland-client.toml`** (HUD / rope visuals). See in-game generated TOML comments or the mod’s **CurseForge** **`DESCRIPTION.md`** for a topic map.
- **Optional integrations** — hooks when **Lootr**, **Wings Of Fire** (loot), **Realm RPG: Treasure Balloons**, **Friends&Foes**, **CNB**, **Skill Tree / Spell Engine** stack, etc., are loaded (`neoforge.mods.toml` optional entries).
- **Bundled CC0 resource pack** — **Unshaded Blocks** (client; enable under **Resource packs**).

### Fixed

- **Dedicated server:** **`projectisland:action_bar_toast`** registers without loading client-only UI classes during payload setup (toast handling uses a reflective client path).
- **Island HUD + Waystones + Xaero:** waypoint mirror persistence (**gold** vs **gray** pins) stays consistent with **Waystones** activation data after reconnect; empty beacon payloads no longer wipe valid **`[Island]`** pins; **Xaero** waypoint list mutations are deferred while the waypoint GUI is open to avoid client crashes; duplicate / wrong-gold **`[Island]`** pins from relaxed waystone search or merged terrain are reduced (caps + server-side region keys).

### Notes

- **Modpack** releases (manifest, third-party pins, FTB SNBT in **`overrides/`**) are separate from this **Mods** project — see [`modpack/curseforge/`](../../modpack/curseforge/README.md).
