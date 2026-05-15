# CurseForge project page — setup checklist

Use this when creating or refreshing the **Project Island** **Modpack** listing on CurseForge. UI labels change occasionally; treat this as a field-by-field checklist.

**Related:** pack layout and Gradle zips — [`README.md`](README.md). **Logo file:** [`branding/README.md`](branding/README.md) (`Project_Island_logo.png`; upload on the site — it is not applied from the zip). **Per-release CurseForge copy text:** [`CURSEFORGE_UPDATE.md`](CURSEFORGE_UPDATE.md).

---

## Before you publish

| Item | Source of truth |
|------|-----------------|
| Minecraft version | **`gradle.properties`** → `minecraft_version` (currently **1.21.1**) |
| NeoForge | **`gradle.properties`** → `neo_version`; **`manifest.json`** → `modLoaders` id |
| Pack version | **`manifest.json`** `version` aligned with **`gradle.properties`** `mod_version` |
| Client zip | `./gradlew curseforgeModpackZip` → `build/dist/projectisland-modpack-<version>-curseforge.zip` |
| Server zip | `./gradlew curseforgeServerPackZip` → `build/dist/projectisland-modpack-<version>-curseforge-server.zip` |

---

## Project type and classification

- **Category:** Minecraft → **Modpacks** (not the standalone **Project Island** mod jar project, unless you maintain two separate CF projects).
- **Loader:** **NeoForge** (match `manifest.json`).
- **Game version:** **1.21.1** (or whatever you ship).

Pick reasonable **tags** (examples): `NeoForge`, `Multiplayer`, `Quests`, `RPG`, `Exploration`, `Biomes`, `Magic` — adjust to how you position the pack.

---

## Images

1. **Logo / thumbnail:** Upload **`modpack/curseforge/branding/Project_Island_logo.png`** (or your final square asset, often **≥ 400×400** px if the site asks). Importing the modpack zip **does not** set this automatically.
2. **Screenshots / gallery:** Add a few in-game shots (islands, UI, progression). Optional but improves discovery.

---

## Summary (short blurb)

CurseForge often shows a **short summary** next to the title. One or two sentences; sell the hook, not the manifest.

**Example:**

> **Sky-bound fantasy for NeoForge 1.21.1** — procedural **floating islands**, **FTB Quests**, Skill Tree & Spell Engine RPG progression, **Lootr**, **Waystones**, and **Biomes O’ Plenty**. **Server pack** on **Files → Additional files**.

---

## Description / overview (main page body)

**Canonical copy:** **[`DESCRIPTION_OVERVIEW.md`](DESCRIPTION_OVERVIEW.md)** — structured sections (hook, highlights, client/server install, version table, changelog pointers, license, bugs, credits). Paste from below that file’s horizontal rule into CurseForge.

**Why replace a thin page:** a strong listing uses **short paragraphs**, **bullet highlights**, **clear client vs server steps**, explicit **changelog** / **license** blocks, and **screenshots**—not one wall of prose. Refresh **`DESCRIPTION_OVERVIEW.md`** when positioning or mods change.

CurseForge may render Markdown inconsistently; if headers fail, use **bold** pseudo-headings or their rich-text toolbar.

---

## License field (CurseForge)

CurseForge asks for a **license** for the **project page**. Important distinction:

| What | Typical handling on the listing |
|------|--------------------------------|
| **Project Island mod** (source in this repo) | **MIT** — see repository [`LICENSE`](../../LICENSE) and `gradle.properties` `mod_license`. |
| **This modpack as a collection** | You are **not** relicensing third-party mods. The pack **bundles references** (`manifest.json`) and **overrides** (configs, bundled jar). Choose the option that matches **your pack metadata policy** (many authors pick **All Rights Reserved** for the *listing* and explain redistribution in the description, or use another SPDX id if CurseForge allows and your lawyer agrees). |
| **Third-party mods** | Each mod keeps **its own license**; confirm redistributability on CurseForge when adding jars under **`overrides/mods/`**. |

**Not legal advice.** If unsure, use your project’s legal counsel or CurseForge’s own guidance for modpack projects.

**Suggested description sentence for clarity:**

> The **Project Island** mod’s source code is **MIT**. Included mods are © their respective authors and subject to **their** licenses and CurseForge distribution rules.

---

## Files tab (releases)

For **each** pack version:

1. **Primary file:** `projectisland-modpack-<version>-curseforge.zip` — default player download.
2. **Additional file:** `projectisland-modpack-<version>-curseforge-server.zip` — dedicated server / host workflows.

Set **release type** (Release / Beta) and **changelog** notes per file. Maintain canonical bullets in the repo: **[`CHANGELOG_CLIENT.md`](CHANGELOG_CLIENT.md)** (primary zip) and **[`CHANGELOG_SERVER.md`](CHANGELOG_SERVER.md)** (additional server zip); copy or summarize into each CurseForge upload.

---

## Links (optional)

Add if you maintain them:

- **Source / issue tracker** — Git repository URL.
- **Discord** — community link if applicable.

---

## Post-publish sanity check

- [ ] Client install launches with correct MC + NeoForge.
- [ ] Quest book shows chapters (FTB quest SNBT ships in the zip).
- [ ] Server zip installs without client-only mods where intended (`server-pack-excluded-project-ids.json`).
- [ ] Logo and overview text render as expected on mobile and desktop.
