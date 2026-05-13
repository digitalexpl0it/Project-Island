# Extra mods for parity with dev installs

The **CurseForge `manifest.json`** pins a **small** set of mods as **`projectID`/`fileID`** pairs. Everything else from your **`run-server/mods/`** (and **`run-client/mods/`** for client-only extras) must be copied here **unless** you add them to the manifest instead.

**Inventory docs:** [`MOD_LIST.md`](../../../../MOD_LIST.md) should stay aligned with **`run-server/mods/`** (refresh the table when the folder changes).

### RPG stack + worldgen in `manifest.json`

**Spell Engine**, **Spell Power**, **Skill Tree**, **Pufferfish's Skills**, **Archers** / **Paladins & Priests** / **Rogues & Warriors** / **Wizards**, **Accessories**, **AzureLib Armor**, **Bundle API**, **playerAnimator**, **Ranged Weapon API**, **Shield API**, **Structure Pool API**, and **[Wings Of Fire!](https://www.curseforge.com/minecraft/mc-mods/the-wings-of-fire)** (phoenix mounts; depends on **GeckoLib**) are pinned as CurseForge **`projectID`/`fileID`** pairs (NeoForge **`1.21.1`** files matching **`MOD_LIST.md`** where filenames align). Worldgen / libraries also pinned: **[Biomes O' Plenty](https://www.curseforge.com/minecraft/mc-mods/biomes-o-plenty)** + **[TerraBlender](https://www.curseforge.com/minecraft/mc-mods/terrablender-neoforge)**; **[GlitchCore](https://www.curseforge.com/minecraft/mc-mods/glitchcore)** + **[Embeddium](https://www.curseforge.com/minecraft/mc-mods/embeddium)**; **[Mowzie's Mobs](https://www.curseforge.com/minecraft/mc-mods/mowzies-mobs)**; **[Better Villages (NeoForge)](https://www.curseforge.com/minecraft/mc-mods/better-village-neoforge)** (**`bettervillage-neoforge-…`** — not **[better-village-forge](https://www.curseforge.com/minecraft/mc-mods/better-village-forge)** **`…-all.jar`**, which NeoForge **21.1+** rejects); **[It Takes a Pillage Continuation](https://www.curseforge.com/minecraft/mc-mods/it-takes-a-pillage-continuation)** (not the stale **[it-takes-a-pillage](https://www.curseforge.com/minecraft/mc-mods/it-takes-a-pillage)** listing); **[Library Ferret (NeoForge)](https://www.curseforge.com/minecraft/mc-mods/library-ferret-neoforge)**; **[Balm](https://www.curseforge.com/minecraft/mc-mods/balm)** (pinned **`21.0.56`** for **`1.21.1`** — newer patches may be Modrinth-only); **[oωo (owo-lib)](https://www.curseforge.com/minecraft/mc-mods/owo-lib)**; **[Resourceful Lib](https://www.curseforge.com/minecraft/mc-mods/resourceful-lib)**; **[Realm RPG: Treasure Balloons](https://www.curseforge.com/minecraft/mc-mods/realm-rpg-treasure-balloons)**; **[Jupiter](https://www.curseforge.com/minecraft/mc-mods/jupiter)** / **[Uranus](https://www.curseforge.com/minecraft/mc-mods/uranus)** / **[NeOculus](https://www.curseforge.com/minecraft/mc-mods/neoculus)**. The launcher downloads them automatically — do **not** duplicate those JARs under **`overrides/mods/`**.

**Exception (server fat pack):** **[Traveler's Backpack](https://www.curseforge.com/minecraft/mc-mods/travelers-backpack)**, **[GraveStone Mod](https://www.curseforge.com/minecraft/mc-mods/gravestone-mod)**, **[FallingTree](https://www.curseforge.com/minecraft/mc-mods/falling-tree)**, and **[Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)** are also manifest-pinned but their JARs are **committed here** so **`curseforgeServerPackZip`** always ships them under **`overrides/mods/`** without relying on a local **`run-server/mods/`** tree. Root **`build.gradle`** lists the same filenames in **`curseforgeServerPackModsBundledInRepoOverrides`**: they are **excluded from `curseforgeModpackZip`** (avoids duplicate mods next to the manifest) and **skipped when staging from `run-server/mods`** (avoids duplicate zip entries).

### JARs on disk still outside `manifest.json` pins

Copy into **`overrides/mods/`** when building the zip if you keep them:

- **`balm-neoforge-1.21.1-21.0.57.jar`** — optional **ahead-of-CF** patch; manifest pins **[Balm](https://www.curseforge.com/minecraft/mc-mods/balm)** **`21.0.56`**. Drop from overrides if you match the pin exactly.
- **`bettervillage-forge-…-all.jar`** — wrong artifact for **NeoForge 21.1+** (loader warning). Use the manifest pin **[Better Villages (NeoForge)](https://www.curseforge.com/minecraft/mc-mods/better-village-neoforge)** (`bettervillage-neoforge-…`) instead.
- **`villager-guard-autonomy.jar`**, **`villager-guards-*.jar`** — **Mob Conversion** + **[Villager Guards](https://modrinth.com/mod/villager-guards)**.
- **`TerraBlender-*.jar`** only if you keep a duplicate rename like **` (1)`** — delete the extra copy.

**Slug trap:** the phoenix-mount mod is **[the-wings-of-fire](https://www.curseforge.com/minecraft/mc-mods/the-wings-of-fire)**. CurseForge’s shorter [**wings-of-fire**](https://www.curseforge.com/minecraft/mc-mods/wings-of-fire) listing is a **different** mod.

Typical sources:

1. **Modrinth** — most rows in `MOD_LIST.md` link directly to Modrinth project pages. Download the **NeoForge 1.21.1** file whose **filename** matches the table (or the newest compatible release).
2. **CurseForge** — for mods also on CurseForge, add `projectID` / `fileID` pairs to `manifest.json` (then you can remove the JAR from overrides).

## Strongly recommended next additions (worldgen + RPG stack)

| Role | Example JAR (see `MOD_LIST.md`) |
|------|----------------------------------|
| Biomes + blending | **Biomes O' Plenty** + **TerraBlender** are in the manifest (`MOD_LIST.md` filenames) |
| Library stack | **GlitchCore**, **Balm**, **Library Ferret**, **oωo-lib**, **Resourceful Lib** are pinned (optional newer **Balm** patch via overrides) |
| RPG Series | Skill Tree, Spell Engine, class mods, Pufferfish's Skills, supporting APIs |
| Content | Mowzie's Mobs, Wings Of Fire, Treasure Balloons, Better Villages, It Takes a Pillage Continuation, etc. |

## Server vs client

- **Dedicated server:** omit optional **client-only** performance/visual mods (e.g. Neoculus, shader companions) if you want a minimal `mods/` folder.
- **CurseForge App client:** use the **same** zip / manifest + overrides so versions stay aligned.

## Licensing

Third-party mods keep their own licenses. Do not redistribute their JARs on CurseForge unless their license and CurseForge’s rules allow it; `overrides/` is for **your** pack zip build that players download from **your** distribution channel.
