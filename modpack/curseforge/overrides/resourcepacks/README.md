# Resource packs (optional)

Drop **`.zip`** resource packs here so **`./gradlew curseforgeModpackZip`** includes them under the instance **`resourcepacks/`** folder (CurseForge merges **`overrides/`** on top of the profile).

## Title / menu packs

A pack that only replaces **title panorama**, **logo**, or **splash** text is still a normal resource pack: same **`pack.mcmeta`**, **`assets/minecraft/…`** layout.

**Default-on (CurseForge pack):** ship it as **`Project_Island_menu_assets.zip`** in this folder (exact name). The bundled **[Resource Pack Overrides](https://www.curseforge.com/minecraft/mc-mods/resource-pack-overrides)** config enables that file on first launch; change the filename in **`../config/resourcepackoverrides.json`** if you prefer another name.

## Redistribution

Confirm the author’s **license** before you ship the zip on CurseForge (same idea as [**`../shaderpacks/README.md`**](../shaderpacks/README.md)). If redistribution is not allowed, leave this folder empty and link players to the official download.

## Server

Dedicated servers do **not** need resource packs unless you use plugins/mod features that read pack data from disk; title/menu art is **client-only**.
