# Island HUD icons — example resource pack

Vanilla **datapacks** (`data/…`) cannot replace **client textures** for every player. To customize the two island HUD sprites:

1. Copy this folder to `.minecraft/resourcepacks/` (or zip it; the root must contain `pack.mcmeta` and `assets/`).
2. Replace the two **64×64 PNG** files (keep filenames and paths):
   - `assets/projectisland/textures/gui/island_hud/floating-island.png` — used for **claimed** islands and as one frame for **available**.
   - `assets/projectisland/textures/gui/island_hud/floating-island_ex.png` — second frame for **available** (slow blink).
3. Enable the pack **above** Default in **Options → Resource Packs** on **each client** (and on the dedicated machine if you run a client there).

The copies shipped here match the mod’s built-in placeholders; swap in your own art and bump `pack.mcmeta` if your format target differs.
