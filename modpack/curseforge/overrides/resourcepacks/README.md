# Resource packs (optional)

Drop **`.zip`** resource packs here so **`./gradlew curseforgeModpackZip`** includes them under the instance **`resourcepacks/`** folder (CurseForge merges **`overrides/`** on top of the profile).

## Title / menu packs

A pack that only replaces **title panorama**, **logo**, or **splash** text is still a normal resource pack: same **`pack.mcmeta`**, **`assets/minecraft/…`** layout.

**Default-on (CurseForge pack):**

- **Bundled override:** **`Project_Island_menu_assets.zip`** in this folder (exact name).
- **Manifest downloads:** **[Dramatic Skys](https://www.curseforge.com/minecraft/texture-packs/dramatic-skys)** and **[Mandala's GUI - Dark mode](https://www.curseforge.com/minecraft/texture-packs/mandalas-gui-dark-mode)** are listed in **`manifest.json`**; the CurseForge app installs their zips into **`resourcepacks/`** (not the server pack).
- **[Resource Pack Overrides](https://www.curseforge.com/minecraft/mc-mods/resource-pack-overrides)** (**`../config/resourcepackoverrides.json`**) enables all three on first launch. If you bump a manifest **`fileID`**, update the **`file/…zip`** ids in that config to match the new **`fileName`** from CurseForge.

**Local dev:** **`./gradlew syncManifestModJarsToDevRuns`** downloads the two manifest zips into **`run-client/resourcepacks/`** and copies **`Project_Island_menu_assets.zip`** from this folder.

## Redistribution

Confirm the author’s **license** before you ship the zip on CurseForge (same idea as [**`../shaderpacks/README.md`**](../shaderpacks/README.md)). If redistribution is not allowed, leave this folder empty and link players to the official download.

## Server

Dedicated servers do **not** need resource packs unless you use plugins/mod features that read pack data from disk; title/menu art is **client-only**.
