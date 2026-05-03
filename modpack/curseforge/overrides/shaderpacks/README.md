# Shader packs (optional)

Shader packs are **not** part of the default **CurseForge** zip build: licenses vary (many are **not** redistributable on CurseForge without explicit permission).

## Dev install on this repo

Your **`run-client/shaderpacks/`** currently includes **Complementary Reimagined** (`ComplementaryReimagined_r5.7.1.zip`). That matches the stack described in the root [**README.md**](../../../../README.md) (Embeddium + Neoculus–class loaders).

## Shipping in a pack zip

1. Read the shader author’s **license** (Complementary has its own terms on the official download page).
2. If allowed, copy the `.zip` into **`overrides/shaderpacks/`** before running **`./gradlew curseforgeModpackZip`** (the Gradle task copies all of **`modpack/curseforge/`**, including overrides).
3. If **not** allowed to redistribute, leave this folder empty and tell players to install the same pack manually under **Options → Video Settings → Shader packs**.

There is **no** `shaderpacks` folder on the dedicated server; shaders are **client-only**.
