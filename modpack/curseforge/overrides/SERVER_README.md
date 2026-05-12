# Project Island — dedicated server pack notes

This zip is built as a **fat** server layout: third-party mods live under **`overrides/mods/`** (plus **`projectisland-*.jar`**). **`manifest.json`** uses **`files`: `[]`** so you do not resolve the CurseForge client manifest on the host.

**`overrides/config/fml.toml`** sets NeoForge **`versionCheck = false`** so the loader’s optional global “newer mod on CurseForge” hint system does not run ([docs](https://docs.neoforged.net/docs/misc/updatechecker)). Merge **`overrides/`** into your server instance so that file lands under **`config/`**. Individual mods may still ship their own update settings under **`config/`**—adjust only if you know what you are changing.

## Match the client release

Use the **server** **Additional file** from the **same** CurseForge release as the **primary** client modpack zip so Minecraft, NeoForge, and mod versions align.

## Mods you must add manually (not shipped in this zip)

The pack build **omits** some JARs from **`overrides/mods/`** when we cannot redistribute them in our server zip, or when the filename is a known bad duplicate. **Download the same NeoForge 1.21.1 build your client profile uses** (see the client **`manifest.json`** pins or **`MOD_LIST.md`** in the repository) and place the **`.jar`** next to the other mods (e.g. merge into **`mods/`** after unzip, depending on your host layout).

| File you need | Notes | Where to get it |
|---------------|--------|-----------------|
| `GlitchCore-neoforge-1.21.1-2.1.0.0.jar` | Embeddium / rendering stack dependency | [GlitchCore (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/glitchcore) |
| `bettervillage-neoforge-1.21.1-3.3.1.jar` | **Better Villages (NeoForge)** — confirm license on the mod page | [Better Villages - NeoForge](https://www.curseforge.com/minecraft/mc-mods/better-village-neoforge) |
| `waystones-neoforge-1.21.1-21.1.32.jar` | **Waystones** | [Waystones (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/waystones) · [Modrinth](https://modrinth.com/mod/waystones) |
| `balm-neoforge-1.21.1-21.0.57.jar` | Optional **ahead-of-manifest** **Balm** patch; the client manifest may pin **`.56`** instead | [Balm](https://www.curseforge.com/minecraft/mc-mods/balm) |
| `TerraBlender-neoforge-1.21.1-4.1.0.8 (1).jar` | **Not a separate mod** — remove this duplicate rename; use a single **TerraBlender** jar matching the pack | [TerraBlender](https://modrinth.com/mod/terrablender) / [CurseForge listing](https://www.curseforge.com/minecraft/mc-mods/terrablender-neoforge) |

If this table is empty in a future release, the build no longer excludes any mods from the server zip—see root **`build.gradle`** (`curseforgeServerPackUndistributableModJars`) and repository **`MOD_LIST.md`** for the current list.

## Performance mods shipped with the pack (when present)

**ModernFix** and **FerriteCore** are LGPL / MIT on CurseForge and are **included** in the client manifest and in the server **`overrides/mods/`** tree when your maintainer build copies them from **`run-server/mods/`** (same filenames as **`MOD_LIST.md`**).
