package net.projectisland;

import java.util.List;
import java.util.regex.Pattern;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Optional per-id weight overrides when {@link #ISLAND_BIOME_MOD_DISCOVER_ALL_REGISTERED} lists every BOP biome;
     * leave empty to weight them all equally ({@link #ISLAND_BIOME_MOD_DISCOVERED_DEFAULT_WEIGHT}).
     */
    private static final List<String> DEFAULT_ISLAND_BIOME_MOD_WEIGHTED_ENTRIES = List.of();

    private static final Pattern ISLAND_BIOME_MOD_WEIGHTED_ENTRY =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_.-]+=[1-9][0-9]{0,6}$");

    private static final Pattern SPAWN_TUNING_BYPASS_ENTITY_NAMESPACE =
            Pattern.compile("^[a-z0-9_]+$");

    private static final List<String> DEFAULT_SPAWN_TUNING_BYPASS_ENTITY_NAMESPACES = List.of("mowziesmobs", "cnb");

    private static boolean validateIslandBiomeModWeightedEntry(Object o) {
        return o instanceof String s && ISLAND_BIOME_MOD_WEIGHTED_ENTRY.matcher(s).matches();
    }

    private static boolean validateSpawnTuningBypassEntityNamespace(Object o) {
        return o instanceof String s && SPAWN_TUNING_BYPASS_ENTITY_NAMESPACE.matcher(s).matches();
    }

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Log extra diagnostics during client and common setup (off by default for quieter logs).")
            .define("debugLogging", false);

    public static final ModConfigSpec.IntValue ISLAND_REGION_RARE_STRUCTURE_WEIGHT_NONE = BUILDER
            .comment(
                    "Weighted roll **together with** islandRegionRareStructureWeightMonsterRoom/TrialChambers/DesertPyramid/JunglePyramid/Mineshaft:",
                    "exclusive **one** outcome per **8×8-chunk island region** (same grid as island biomes). When rolled, monster rooms, trial chambers, and pyramids placed in that region are stripped.",
                    "Raising **monster room** / **trial** / pyramid weights gives more regions a matching dungeon or temple slot (vanilla must still attempt placement).")
            .defineInRange("islandRegionRareStructureWeightNone", 380, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ISLAND_REGION_RARE_STRUCTURE_WEIGHT_MONSTER_ROOM = BUILDER
            .comment("Relative weight for {@code minecraft:monster_room} as this region’s rare-structure slot.")
            .defineInRange("islandRegionRareStructureWeightMonsterRoom", 55, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ISLAND_REGION_RARE_STRUCTURE_WEIGHT_TRIAL_CHAMBERS = BUILDER
            .comment("Relative weight for {@code minecraft:trial_chambers}.")
            .defineInRange("islandRegionRareStructureWeightTrialChambers", 45, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ISLAND_REGION_RARE_STRUCTURE_WEIGHT_DESERT_PYRAMID = BUILDER
            .comment("Relative weight for {@code minecraft:desert_pyramid} (also requires desert/badlands island biome).")
            .defineInRange("islandRegionRareStructureWeightDesertPyramid", 35, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ISLAND_REGION_RARE_STRUCTURE_WEIGHT_JUNGLE_PYRAMID = BUILDER
            .comment("Relative weight for {@code minecraft:jungle_pyramid} (also requires jungle-family island biome).")
            .defineInRange("islandRegionRareStructureWeightJunglePyramid", 30, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ISLAND_REGION_SETTLEMENT_STRUCTURE_WEIGHT_ALLOW = BUILDER
            .comment(
                    "Second roll per island region, independent of rare slot: relative weight to **allow** {@code village_*} and {@code pillager_outpost}",
                    "starts that survive land-contact checks. When denied, those starts are removed like wrong-slot dungeons.",
                    "Default pairs with **Deny** so some regions skip settlements entirely — tune for pack density (e.g. Better Villages).")
            .defineInRange("islandRegionSettlementStructureWeightAllow", 72, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ISLAND_REGION_SETTLEMENT_STRUCTURE_WEIGHT_DENY = BUILDER
            .comment(
                    "Paired with islandRegionSettlementStructureWeightAllow — relative weight to **skip** controlled settlements in this region.",
                    "Higher **Deny** thins village/outpost frequency across islands.")
            .defineInRange("islandRegionSettlementStructureWeightDeny", 28, 0, 1_000_000);

    public static final ModConfigSpec.BooleanValue ISLAND_REGION_VILLAGE_REQUIRE_BIOME_MATCH = BUILDER
            .comment(
                    "When **true**, {@code village_*} starts are removed if the island biome does not fit that village id (e.g. **village_plains** only on plains/meadow/forest-family).",
                    "When **false**, only the settlement allow/deny roll applies to villages (useful with **Multi Village Selector** or other mods that place villages across biomes). **Pillager outpost** biome checks are unchanged.")
            .define("islandRegionVillageRequireBiomeMatch", true);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_CONTROLLED_SETTLEMENT_PLACEMENT = BUILDER
            .comment(
                    "When **true**, the floating-islands generator **removes** all vanilla {@code village_*} and {@code pillager_outpost} starts, then places **at most one** controlled jigsaw settlement per **inhabited** island region (same 8×8 grid as biomes).",
                    "Anchor is randomized near the procedural island center on solid columns so **/locate** and gameplay see predictable surface settlements. Uses **islandRegionSettlement** allow/deny, then weighted **controlledSettlementWeightVillage** / **Outpost** / **None**.",
                    "When **false**, vanilla structure-set placement runs (still subject to island gating and land-contact rules).")
            .define("floatingIslandsControlledSettlementPlacement", true);

    public static final ModConfigSpec.IntValue CONTROLLED_SETTLEMENT_WEIGHT_VILLAGE = BUILDER
            .comment(
                    "After a region passes **islandRegionSettlementStructureWeightAllow/Deny**: relative weight to place a **village** variant matching the rolled island biome (vs outpost / none).")
            .defineInRange("controlledSettlementWeightVillage", 42, 0, 1_000_000);

    public static final ModConfigSpec.IntValue CONTROLLED_SETTLEMENT_WEIGHT_OUTPOST = BUILDER
            .comment(
                    "Relative weight for the **outpost** branch ( **minecraft:pillager_outpost** or **takesapillage:*** pillager structures when that mod is loaded — see **floatingIslandsTakesapillageControlledOutpost**).")
            .defineInRange("controlledSettlementWeightOutpost", 13, 0, 1_000_000);

    public static final ModConfigSpec.IntValue CONTROLLED_SETTLEMENT_WEIGHT_NONE = BUILDER
            .comment(
                    "Relative weight for **no** controlled settlement this region (even if vanilla would have tried).",
                    "Raise this (and **controlledSettlementPlaceTryChance** down) when packs add oversized villages / castles so settlements feel less crowded.")
            .defineInRange("controlledSettlementWeightNone", 45, 0, 1_000_000);

    public static final ModConfigSpec.IntValue CONTROLLED_SETTLEMENT_ANCHOR_JITTER_BLOCKS = BUILDER
            .comment(
                    "Max horizontal jitter in blocks from the procedural island **center** when picking the jigsaw anchor (rejected if the column has no island stone). **0** = always center column.",
                    "Lower jitter keeps large jigsaw footprints nearer the plateau center (less rim overhang).")
            .defineInRange("controlledSettlementAnchorJitterBlocks", 8, 0, 48);

    public static final ModConfigSpec.IntValue CONTROLLED_SETTLEMENT_ANCHOR_TRIES = BUILDER
            .comment("How many random jitter samples to try before giving up on placing a controlled settlement in this region.")
            .defineInRange("controlledSettlementAnchorTries", 18, 1, 64);

    public static final ModConfigSpec.DoubleValue CONTROLLED_SETTLEMENT_PLACE_TRY_CHANCE = BUILDER
            .comment(
                    "After the village/outpost/none weight roll **chose** a village or outpost (not **none**): probability to **actually** generate that controlled settlement (**0–1**).",
                    "**1** = always place when chosen; lower values thin settlements across regions; **0** never places (strip-only until you turn off **floatingIslandsControlledSettlementPlacement**).")
            .defineInRange("controlledSettlementPlaceTryChance", 0.38d, 0.0d, 1.0d);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_TAKESAPILLAGE_CONTROLLED_OUTPOST = BUILDER
            .comment(
                    "When **true** (default) and NeoForge mod **takesapillage** (**It Takes a Pillage**) is loaded: controlled **outpost** rolls place **takesapillage:bastille** or **pillager_camp** (weights **1**:**2**, matching the mod’s structure set) via generic structure placement instead of **minecraft:pillager_outpost**.",
                    "Those mod structures are also stripped before controlled placement (like vanilla settlements) and skip aggressive surface trimming / void land-contact stripping while controlled settlements are enabled.",
                    "When **false**, outpost branch keeps **minecraft:pillager_outpost** even if the mod is present.")
            .define("floatingIslandsTakesapillageControlledOutpost", true);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_SNAP_RARE_STRUCTURES_TO_ISLAND_COLUMN = BUILDER
            .comment(
                    "When **true**, after vanilla places **minecraft:monster_room**, **minecraft:trial_chambers**, or **minecraft:mineshaft**, vertically shift **all** pieces of that start so the bounding-box **top** sits at **columnBottomY − 1** at the box center (hang under procedural island stone).",
                    "Skips starts that already intersect island stone in that column, void columns, or when the shift would exceed **floatingIslandsSnapRareStructureMaxVerticalBlocks**.")
            .define("floatingIslandsSnapRareStructuresToIslandColumn", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_SNAP_RARE_STRUCTURE_MAX_VERTICAL_BLOCKS = BUILDER
            .comment("Max absolute vertical shift (blocks) for **floatingIslandsSnapRareStructuresToIslandColumn**; larger moves are skipped.")
            .defineInRange("floatingIslandsSnapRareStructureMaxVerticalBlocks", 96, 8, 256);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_SNAP_RARE_STRUCTURE_INVALIDATE_ON_FAIL = BUILDER
            .comment(
                    "When **true**, **minecraft:monster_room**, **trial_chambers**, or **minecraft:mineshaft** starts that cannot be snapped (no land column in the footprint search, horizontal shift over cap, vertical move over cap, or interior fit failure) are **removed** and overlapping blocks in this chunk cleared.",
                    "When **false**, failed snaps are left unchanged (may leave floating void junk).")
            .define("floatingIslandsSnapRareStructureInvalidateOnFail", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_SNAP_RARE_STRUCTURE_MAX_HORIZONTAL_MANHATTAN_BLOCKS = BUILDER
            .comment(
                    "Max **Manhattan** horizontal shift (|dx|+|dz| in blocks) when snapping rare structures to a chosen anchor column; **0** disables lateral correction.")
            .defineInRange("floatingIslandsSnapRareStructureMaxHorizontalManhattanBlocks", 24, 0, 64);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_SNAP_RARE_STRUCTURE_ANCHOR_GRID_STEP_BLOCKS = BUILDER
            .comment("Footprint sampling step (blocks) when searching for a valid island column under a rare structure bounding box (corners/center always included).")
            .defineInRange("floatingIslandsSnapRareStructureAnchorGridStepBlocks", 6, 2, 16);

    public static final ModConfigSpec.ConfigValue<String> FLOATING_ISLANDS_RARE_STRUCTURE_PLACEMENT_MODE = BUILDER
            .comment(
                    "Rare-structure vertical placement after horizontal anchoring: **under_bottom** = hang with BB top at columnBottomY−1 (legacy); **interior** = align BB vertical center near island stone mid-depth (falls back to under_bottom if fit sampling fails).")
            .define("floatingIslandsRareStructurePlacementMode", "under_bottom");

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_RARE_STRUCTURE_INTERIOR_MIN_COLUMN_FIT_FRACTION = BUILDER
            .comment(
                    "Minimum fraction of footprint / vertical probe samples that must lie inside procedural island stone for **interior** placement and BB fit checks (0–1).")
            .defineInRange("floatingIslandsRareStructureInteriorMinColumnFitFraction", 0.35d, 0.0d, 1.0d);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_ENABLE_MASKED_OVERWORLD_CARVERS = BUILDER
            .comment(
                    "When **true**, **FloatingIslandsChunkGenerator** runs overworld-style noise carvers **masked** to procedural island columns so caves pocket inside islands instead of carving open void.",
                    "Uses **minecraft:overworld** noise settings only for carving context (aquifer / surface sampling); terrain fill stays procedural.")
            .define("floatingIslandsEnableMaskedOverworldCarvers", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_MASKED_CARVER_NEIGHBOR_CHUNK_RADIUS = BUILDER
            .comment(
                    "Chebyshev radius (chunks) around each chunk when running masked carvers. Vanilla overworld uses **8** (a **17×17** area).",
                    "Lower values reduce worldgen CPU; **5** is a practical default (**11×11**). **8** matches vanilla cave reach at chunk seams.")
            .defineInRange("floatingIslandsMaskedCarverNeighborChunkRadius", 5, 2, 8);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_LOCATE_STRUCTURE_MAX_RING_RADIUS = BUILDER
            .comment(
                    "Caps **`/locate structure`** search rings on the floating-islands generator (vanilla passes **100**). Each candidate ring can synchronously force chunk generation and may watchdog-kill the server on heavy jigsaw structures (e.g. trial chambers).",
                    "Sparse structures (**minecraft:mansion**, ocean monuments, …) may need a **higher** cap or several locate attempts. Raise toward **100** only if you accept longer locate stalls.")
            .defineInRange("floatingIslandsLocateStructureMaxRingRadius", 32, 4, 100);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_MASKED_CARVERS_DEBUG_LOGGING = BUILDER
            .comment("When **true** with **debugLogging**, log when masked carving is skipped or fails (normally silent).")
            .define("floatingIslandsMaskedCarversDebugLogging", false);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_CONTROLLED_RARE_DUNGEON_PLACEMENT = BUILDER
            .comment(
                    "When **true**, strips vanilla **minecraft:monster_room** and **minecraft:trial_chambers** starts in each chunk, then (on the region owner chunk only) regenerates **at most one** matching structure per inhabited island region when the rare-structure slot roll matches **controlledRareDungeonPlaceTryChance**.",
                    "Runs before vertical snap; turn **false** for vanilla dungeon placement only.")
            .define("floatingIslandsControlledRareDungeonPlacement", false);

    public static final ModConfigSpec.DoubleValue CONTROLLED_RARE_DUNGEON_PLACE_TRY_CHANCE = BUILDER
            .comment(
                    "After **floatingIslandsControlledRareDungeonPlacement** and the region rare slot is monster room or trial chambers: probability **0–1** to actually generate the controlled dungeon (**0** = strip-only).")
            .defineInRange("controlledRareDungeonPlaceTryChance", 0.55d, 0.0d, 1.0d);

    public static final ModConfigSpec.IntValue ISLAND_REGION_RARE_STRUCTURE_WEIGHT_MINESHAFT = BUILDER
            .comment(
                    "Relative weight for **minecraft:mineshaft** in the same exclusive island-region rare slot as dungeons/trials/pyramids (**0** = never selected — vanilla mineshafts still generate unless removed by other rules).")
            .defineInRange("islandRegionRareStructureWeightMineshaft", 0, 0, 1_000_000);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_RARE_STRUCTURE_DECORATIVE_CHAINS = BUILDER
            .comment(
                    "After biome decoration gating: for **monster_room**, **trial_chambers**, and **minecraft:mineshaft** starts still valid in this chunk, fill a vertical **minecraft:chain** column between structure top and procedural island underside when the gap is small enough.")
            .define("floatingIslandsRareStructureDecorativeChains", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_RARE_STRUCTURE_CHAIN_MAX_GAP_BLOCKS = BUILDER
            .comment("Max vertical gap (blocks) for decorative chains (**0** disables length limit only by this cap).")
            .defineInRange("floatingIslandsRareStructureChainMaxGapBlocks", 48, 0, 256);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLAND_REGION_SPAWN_CHANCE = BUILDER
            .comment(
                    "Per **8×8-chunk** island region: probability that a procedural floating island exists there.",
                    "Higher values shrink void gaps between land (~**0.34** ≈ twice as dense as legacy **0.17**). Range **0.05–1**.")
            .defineInRange("floatingIslandRegionSpawnChance", 0.34d, 0.05d, 1.0d);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_RIVER = BUILDER
            .comment("Weight for minecraft:river when rolling per-island biome (0 = never). Land uses procedural biomes, not vanilla multi_noise sectors.")
            .defineInRange("islandBiomeWeightRiver", 28, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_PLAINS = BUILDER
            .comment("Weight for minecraft:plains (0 = exclude). Slightly higher default helps vanilla villages find suitable surfaces.")
            .defineInRange("islandBiomeWeightPlains", 20, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_FOREST = BUILDER
            .comment("Weight for minecraft:forest (0 = exclude).")
            .defineInRange("islandBiomeWeightForest", 14, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_TAIGA = BUILDER
            .comment("Weight for minecraft:taiga (0 = exclude).")
            .defineInRange("islandBiomeWeightTaiga", 10, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_DESERT = BUILDER
            .comment("Weight for minecraft:desert (0 = exclude).")
            .defineInRange("islandBiomeWeightDesert", 8, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_SNOWY_PLAINS = BUILDER
            .comment("Weight for minecraft:snowy_plains (0 = exclude).")
            .defineInRange("islandBiomeWeightSnowyPlains", 8, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_JUNGLE = BUILDER
            .comment("Weight for minecraft:jungle (0 = exclude).")
            .defineInRange("islandBiomeWeightJungle", 6, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_MUSHROOM_FIELDS = BUILDER
            .comment("Weight for minecraft:mushroom_fields (0 = exclude).")
            .defineInRange("islandBiomeWeightMushroomFields", 2, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_BADLANDS = BUILDER
            .comment("Weight for minecraft:badlands (0 = exclude).")
            .defineInRange("islandBiomeWeightBadlands", 4, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_WINDSWEPT_FOREST = BUILDER
            .comment("Weight for minecraft:windswept_forest (0 = exclude).")
            .defineInRange("islandBiomeWeightWindsweptForest", 4, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_SWAMP = BUILDER
            .comment("Weight for minecraft:swamp (0 = exclude).")
            .defineInRange("islandBiomeWeightSwamp", 6, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_DARK_FOREST = BUILDER
            .comment(
                    "Weight for minecraft:dark_forest (0 = exclude). Required for vanilla **minecraft:mansion** placement;",
                    "mansions stay rare due to vanilla spacing, not this weight alone.")
            .defineInRange("islandBiomeWeightDarkForest", 5, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_SNOWY_TAIGA = BUILDER
            .comment(
                    "Weight for minecraft:snowy_taiga (0 = exclude). Improves **minecraft:igloo** eligibility alongside snowy_plains.")
            .defineInRange("islandBiomeWeightSnowyTaiga", 6, 0, 1000);

    public static final ModConfigSpec.BooleanValue ISLAND_BIOME_MOD_INTEGRATION_ENABLED = BUILDER
            .comment(
                    "When **true** and **Biomes O' Plenty** is installed ({@code biomesoplenty}), merge **islandBiomeModWeightedEntries** into the",
                    "per-island biome pool next to **islandBiomeWeight***. When BOP is absent, those entries are ignored and behavior matches vanilla-only pools.",
                    "Entries that do not resolve in the biome registry are skipped at runtime.")
            .define("islandBiomeModIntegrationEnabled", true);

    public static final ModConfigSpec.DoubleValue ISLAND_BIOME_MOD_PREFERRED_ROLL_FRACTION = BUILDER
            .comment(
                    "Used only when **Biomes O' Plenty** is loaded, **islandBiomeModIntegrationEnabled**, and the mod biome pool is non-empty.",
                    "**Default 0.7** — **70%** of island regions roll **only** among mod biomes; **30%** roll **only** among **islandBiomeWeight*** (vanilla).",
                    "**0** — single combined pool (legacy): vanilla and mod weights compete by raw totals.",
                    "**1** — always mod subset when it is non-empty (no vanilla-only regions).")
            .defineInRange("islandBiomeModPreferredRollFraction", 0.7d, 0.0d, 1.0d);

    public static final ModConfigSpec.BooleanValue ISLAND_BIOME_MOD_DISCOVER_ALL_REGISTERED = BUILDER
            .comment(
                    "When **true** (default) and BOP is loaded: every **`biomesoplenty:*`** biome in the biome registry is eligible on the mod branch",
                    "(overworld / nether / end ids BOP registers — disabled BOP biomes are usually absent from the registry).",
                    "**islandBiomeModWeightedEntries** then acts as **per-id weight overrides**; ids not listed use **islandBiomeModDiscoveredDefaultWeight**.",
                    "When **false**: only lines in **islandBiomeModWeightedEntries** are used (pack-maker curated list).")
            .define("islandBiomeModDiscoverAllRegistered", true);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_MOD_DISCOVERED_DEFAULT_WEIGHT = BUILDER
            .comment(
                    "Used when **islandBiomeModDiscoverAllRegistered** is **true**: relative weight for each discovered **biomesoplenty:*** biome",
                    "that does not appear in **islandBiomeModWeightedEntries**.")
            .defineInRange("islandBiomeModDiscoveredDefaultWeight", 5, 1, 1000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ISLAND_BIOME_MOD_WEIGHTED_ENTRIES = BUILDER
            .comment(
                    "When **islandBiomeModDiscoverAllRegistered** is **true**: optional **`biomesoplenty:path=weight`** overrides (empty = equal weights via **islandBiomeModDiscoveredDefaultWeight**).",
                    "When **false**: required explicit list of **`namespace:path=weight`** lines for mod island biomes (**empty** = no mod biomes).",
                    "Format: weight **1**–**9999999**. Unresolved ids are skipped.")
            .defineListAllowEmpty(
                    "islandBiomeModWeightedEntries",
                    () -> List.copyOf(DEFAULT_ISLAND_BIOME_MOD_WEIGHTED_ENTRIES),
                    Config::validateIslandBiomeModWeightedEntry);

    /**
     * Added to each island’s horizontal ellipsoid radius ({@link net.projectisland.worldgen.FloatingIslandLayout}).
     * Larger islands leave more flat-ish surface for vanilla villages (paths extend beyond tight stone blobs).
     */
    public static final ModConfigSpec.IntValue FLOATING_ISLAND_HORIZONTAL_RADIUS_BONUS = BUILDER
            .comment(
                    "Extra horizontal radius in blocks for procedural floating islands (added on top of the random base).",
                    "Increase if villages or large structures clip off the rim; 0 yields smaller legacy-sized masses.")
            .defineInRange("floatingIslandHorizontalRadiusBonus", 18, 0, 48);

    /**
     * Added when {@link net.projectisland.worldgen.IslandRegionSettlementRoll#controlledSettlementSizing} is
     * {@link net.projectisland.worldgen.IslandRegionSettlementRoll.ControlledSettlementSizing#OUTPOST} for the region.
     */
    public static final ModConfigSpec.IntValue FLOATING_ISLAND_HORIZONTAL_RADIUS_OUTPOST_EXTRA_BLOCKS = BUILDER
            .comment(
                    "Extra horizontal radius (blocks) when the region rolls a controlled pillager outpost settlement.",
                    "Stacks with floatingIslandHorizontalRadiusBonus; 0 disables this bump.")
            .defineInRange("floatingIslandHorizontalRadiusOutpostExtraBlocks", 52, 0, 96);

    /**
     * Added when {@link net.projectisland.worldgen.IslandRegionSettlementRoll#controlledSettlementSizing} is
     * {@link net.projectisland.worldgen.IslandRegionSettlementRoll.ControlledSettlementSizing#VILLAGE} — larger footprints
     * from packs like Better Villages need a wider plateau so jigsaw pieces do not hang past the rim.
     */
    public static final ModConfigSpec.IntValue FLOATING_ISLAND_HORIZONTAL_RADIUS_VILLAGE_EXTRA_BLOCKS = BUILDER
            .comment(
                    "Extra horizontal radius (blocks) when the region rolls a controlled **village** settlement.",
                    "Stacks with floatingIslandHorizontalRadiusBonus; 0 disables. Tune up if modded villages still overhang.")
            .defineInRange("floatingIslandHorizontalRadiusVillageExtraBlocks", 46, 0, 96);

    /**
     * Pointy undersides ("stalactite roots"): scale the per-column bottom ellipsoid radius by smooth
     * Gaussian bumps inside the disk so each island grows 2–5 hanging spikes instead of one rounded blob.
     * Implemented in {@link net.projectisland.worldgen.FloatingIslandLayout}; affects both
     * {@code columnContains} and {@code columnBottomY} so worldgen, carvers, structures, and gameplay agree.
     */
    public static final ModConfigSpec.BooleanValue FLOATING_ISLAND_BOTTOM_SPIKES_ENABLED = BUILDER
            .comment(
                    "Master toggle for pointy island undersides. When **true**, each island gets 3–5 deterministic Gaussian downward bumps that extend the bottom ellipsoid into stalactite-like roots. When **false**, the legacy smooth bottom is used (existing worlds keep the same silhouette).")
            .define("floatingIslandBottomSpikesEnabled", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLAND_BOTTOM_SPIKE_COUNT_MIN = BUILDER
            .comment(
                    "Minimum number of stalactite-root spikes per island (inclusive). Clamped against floatingIslandBottomSpikeCountMax.")
            .defineInRange("floatingIslandBottomSpikeCountMin", 3, 0, 6);

    public static final ModConfigSpec.IntValue FLOATING_ISLAND_BOTTOM_SPIKE_COUNT_MAX = BUILDER
            .comment(
                    "Maximum number of stalactite-root spikes per island (inclusive). Hard cap matches the internal spike array length (6).")
            .defineInRange("floatingIslandBottomSpikeCountMax", 5, 0, 6);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLAND_BOTTOM_SPIKE_MAX_MULTIPLIER = BUILDER
            .comment(
                    "Upper cap on the per-column bottom-radius multiplier at a spike center (1.0 = no extension, 2.8 ≈ tap-root depth nearly 3× the smooth bottom). Higher values stretch spikes further into the void; effective cap is also limited by floatingIslandBottomSpikeMaxDepthBelowCenterBlocks / vrBottom.")
            .defineInRange("floatingIslandBottomSpikeMaxMultiplier", 2.8d, 1.0d, 4.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLAND_BOTTOM_SPIKE_FALLOFF_BLOCKS = BUILDER
            .comment(
                    "Approximate horizontal radius (in blocks) where a spike's depth boost falls to roughly half. Smaller = sharper points; larger = broader hanging lobes.")
            .defineInRange("floatingIslandBottomSpikeFalloffBlocks", 7.0d, 2.0d, 64.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLAND_BOTTOM_SPIKE_MAX_ANCHOR_HORIZ = BUILDER
            .comment(
                    "Maximum sqrt(horiz) at which a spike anchor may sit (fraction of horizontal radius). Keeps roots under the island body rather than the rim; lower = tighter cluster near center, higher = spikes reach toward the rim so the disk is covered evenly on both sides.")
            .defineInRange("floatingIslandBottomSpikeMaxAnchorHoriz", 0.82d, 0.1d, 0.95d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLAND_BOTTOM_SPIKE_MIN_ANCHOR_HORIZ = BUILDER
            .comment(
                    "Minimum sqrt(horiz) at which a spike anchor may sit (fraction of horizontal radius). Pushes anchors outward so K=3-4 spikes do not clump near the island center, leaving the rest of the underside smooth.")
            .defineInRange("floatingIslandBottomSpikeMinAnchorHoriz", 0.35d, 0.0d, 0.9d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLAND_BOTTOM_SPIKE_ANGLE_JITTER_FRACTION = BUILDER
            .comment(
                    "Fraction of an angular slot (2π / spikeCount) by which each spike's polar angle may jitter inside its slot. 0 = perfectly evenly spaced (looks too symmetric); 1 = spikes can land anywhere in their slot. Lower values guarantee disk coverage with small spike counts.")
            .defineInRange("floatingIslandBottomSpikeAngleJitterFraction", 0.55d, 0.0d, 1.0d);

    public static final ModConfigSpec.IntValue FLOATING_ISLAND_BOTTOM_SPIKE_MAX_DEPTH_BELOW_CENTER_BLOCKS = BUILDER
            .comment(
                    "Hard cap (blocks) on how far below an island's centerY the spike-extended bottom may reach. Prevents extreme vrBottom + spike combinations from poking into the void-floor band used by FloatingIslandVoidRescue (see voidRescueTriggerBlocksAboveMinY).")
            .defineInRange("floatingIslandBottomSpikeMaxDepthBelowCenterBlocks", 128, 16, 256);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_CHUNK_GENERATOR_SEA_LEVEL = BUILDER
            .comment(
                    "Returned only by **ChunkGenerator#getSeaLevel()** for the floating-islands overworld (not the same as **Level#getSeaLevel()**, which still comes from the overworld **dimension type**, usually **~63**).",
                    "Many structure placement helpers and some mods anchor vertical placement to generator sea level. The legacy **−63** value misaligned Y relative to island tops; set this near your typical **surface Y** (often **~90–120**). Tune when adding structure/worldgen mods.")
            .defineInRange("floatingIslandsChunkGeneratorSeaLevel", 100, -64, 512);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_REMOVE_STRUCTURES_WITH_NO_LAND_CONTACT = BUILDER
            .comment(
                    "After structures generate, remove any **start** in this chunk whose horizontal footprint does not intersect procedural island columns (void-only). Cleared blocks are wiped for this chunk only; multi-chunk pieces behave like other partial trims.",
                    "Improves packs that add villages/buildings so void debris is less common. Set **false** if a mod requires structures that intentionally float with no land.")
            .define("floatingIslandsRemoveStructuresWithNoLandContact", true);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_MINESHAFT_STRICT_ISLAND_OVERLAP = BUILDER
            .comment(
                    "For **minecraft:mineshaft** only: require the structure bounding-box **center** to sit on an island column and at least **floatingIslandsMineshaftMinIslandColumnFraction** of sampled footprint columns to have land.",
                    "Vanilla’s huge mineshaft box often **barely touched** one rim while corridors and chains filled void sky — the loose test still kept the whole start. Set **false** to restore the legacy **any-corner touch** rule (more void junk).")
            .define("floatingIslandsMineshaftStrictIslandOverlap", true);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_MINESHAFT_MIN_ISLAND_COLUMN_FRACTION = BUILDER
            .comment(
                    "Used only when **floatingIslandsMineshaftStrictIslandOverlap** is **true**: minimum fraction (0–1) of horizontal BB samples that must have procedural island stone beneath.",
                    "**0** = center-column check only; **~0.12** rejects skinny edge grazing.")
            .defineInRange("floatingIslandsMineshaftMinIslandColumnFraction", 0.12d, 0.0d, 1.0d);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_TRIM_STRIP_MINESHAFT_THROUGH_VOID = BUILDER
            .comment(
                    "When **true**, void-column structure trimming also removes **minecraft:mineshaft** blocks in open-sky columns (fragments corridors under floating islands).",
                    "When **false** (default), mineshaft planks/fences/logs spanning void **between** island columns are kept so halls stay connected; floating junk above the surface is still trimmed.")
            .define("floatingIslandsTrimStripMineshaftThroughVoid", false);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_STRONGHOLD_STRICT_ISLAND_OVERLAP = BUILDER
            .comment(
                    "For **minecraft:stronghold** only: same idea as mineshafts — vanilla’s huge ring-spread box often touches one island column while most of the fortress floats in void.",
                    "When **true**, require BB **center** on island and **floatingIslandsStrongholdMinIslandColumnFraction** horizontal overlap. Does **not** re-place the stronghold inside stone (that would need custom placement); it **removes** bad starts. Set **false** for legacy loose touch tests.")
            .define("floatingIslandsStrongholdStrictIslandOverlap", true);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_STRONGHOLD_MIN_ISLAND_COLUMN_FRACTION = BUILDER
            .comment(
                    "Used when **floatingIslandsStrongholdStrictIslandOverlap** is **true** — often slightly **higher** than the mineshaft fraction because stronghold footprints are enormous.")
            .defineInRange("floatingIslandsStrongholdMinIslandColumnFraction", 0.18d, 0.0d, 1.0d);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_RUINED_PORTAL_CHUNK_LOCAL_LAND_ANCHOR = BUILDER
            .comment(
                    "For **minecraft:ruined_portal** only: vanilla’s search bounding box can span many chunks while the placed fragment sits in pure void.",
                    "When **true**, this chunk **keeps** the start only if the **intersection** of the structure BB with this chunk has procedural island land under its horizontal midpoint (not merely “any sample anywhere in the global BB”).",
                    "Set **false** for the legacy whole-BB touch rule (more void rubble).")
            .define("floatingIslandsRuinedPortalChunkLocalLandAnchor", true);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_TRIM_STRUCTURE_VOID_BLOCKS_AFTER_FEATURES = BUILDER
            .comment(
                    "Run structure void-column trimming again at the **start** of biome decoration so blocks placed **after** the STRUCTURE_STARTS trim pass are cleared from open void.",
                    "Helps **ruined_portal** fragments and similar late-written pieces. Set **false** only if something must leave structure blocks floating with no island column.")
            .define("floatingIslandsTrimStructureVoidBlocksAfterFeatures", true);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_CAVE_STRUCTURE_REQUIRE_STONE_Y_OVERLAP = BUILDER
            .comment(
                    "For **minecraft:monster_room**, **minecraft:trial_chambers**, and **minecraft:stronghold**: remove the start if its bounding box does not intersect the procedural island **stone column** (by Y) at the box center.",
                    "Vanilla anchoring uses **sea level** / spread logic, so dungeons and fortress pieces often appear **above or below** the island mass in open air. This does **not** reposition structures — it only strips bad placements.")
            .define("floatingIslandsCaveStructureRequireStoneYOverlap", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_EXTRA_SURFACE_TREES_PER_CHUNK = BUILDER
            .comment(
                    "After normal biome decoration, try to place this many extra trees on grass / sand / mycelium island tops per chunk.",
                    "Attempts pick random **surface columns that have land** (void columns are skipped), so small islands still get coverage.")
            .defineInRange("floatingIslandsExtraSurfaceTreesPerChunk", 8, 0, 64);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_EXTRA_SURFACE_TREES_SNOW_PER_CHUNK = BUILDER
            .comment(
                    "Same as floatingIslandsExtraSurfaceTreesPerChunk but for snow-block tops (cold islands). Usually higher than grass so taiga-style islands are not bare.")
            .defineInRange("floatingIslandsExtraSurfaceTreesSnowPerChunk", 14, 0, 64);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_SURFACE_WATER_POOL_CHUNK_CHANCE = BUILDER
            .comment(
                    "Fraction of chunks that run **any** surface water pool placement (0–1). Combined with a low per-chunk cap, keeps pools rare on large islands.",
                    "**1** = every chunk may get pools; **0.25** ≈ **75%** fewer chunk placements than always-on.")
            .defineInRange("floatingIslandsSurfaceWaterPoolChunkChance", 0.25d, 0.0d, 1.0d);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_SURFACE_WATER_POOLS_PER_CHUNK = BUILDER
            .comment(
                    "Max **small surface water pools** per chunk **when** this chunk passes floatingIslandsSurfaceWaterPoolChunkChance (grass / sand / mycelium; not snow tops).",
                    "Runs before extra trees. Default **1** with **~0.25** chunk chance ≈ **~80%** less coverage than the old default of **4** pools every chunk.")
            .defineInRange("floatingIslandsSurfaceWaterPoolsPerChunk", 1, 0, 32);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_STRIP_EXTERIOR_FLUIDS_AFTER_DECORATION = BUILDER
            .comment(
                    "After biome decoration (including optional surface pools / extra trees), remove **water** / **lava** that can leak into the void:",
                    "either outside the procedural island envelope, or inside it but touching **air**, **cave_air**, or open fluid in a **horizontal or downward** neighbor **outside** FloatingIslandLayout.columnContains.",
                    "**Upward** neighbors are ignored so sky above surface pools is not treated as a leak.",
                    "Within **`floatingIslandsStripExteriorFluidsTopDepthExemptBlocks`** of **`columnTopY`**, **water** skips leak stripping and is **not** cleared merely for lying outside **`columnContains`** (rim lakes). Set **false** for legacy behavior.")
            .define("floatingIslandsStripExteriorFluidsAfterDecoration", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_STRIP_EXTERIOR_FLUIDS_MAX_PASSES = BUILDER
            .comment(
                    "Max strip iterations per chunk (**1**–**16**). Multiple passes clear chained sources along the shell after the first removal.")
            .defineInRange("floatingIslandsStripExteriorFluidsMaxPasses", 6, 1, 16);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_STRIP_EXTERIOR_FLUIDS_TOP_DEPTH_EXEMPT_BLOCKS = BUILDER
            .comment(
                    "Band under **`columnTopY`** where **water** is protected from exterior stripping: skips sideways/down leak removal **and** skips clearing water whose block sits **outside** columnContains (vanilla lakes often spill past the analytic ellipsoid at the rim).",
                    "**Lava** outside columnContains is always cleared; lava inside the envelope still follows leak rules below this band.",
                    "**0** = no exemption (aggressive shell strip). Deep water far below this band is still stripped when it leaks.")
            .defineInRange("floatingIslandsStripExteriorFluidsTopDepthExemptBlocks", 48, 0, 256);

    /**
     * After biome decoration, each ore block is kept with this probability (1.0 = unchanged). Values below 1 thin veins;
     * 0 removes that ore category from generated chunks. Applies only to {@link net.projectisland.worldgen.FloatingIslandsChunkGenerator}.
     * Uses vanilla {@code BlockTags} coal/copper/iron/gold/redstone/lapis/diamond/emerald ores.
     */
    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_ORE_MULT_COAL = BUILDER
            .comment("Keep probability for blocks in minecraft:coal_ores (1.0 = vanilla after decoration).")
            .defineInRange("floatingIslandsOreMultiplierCoal", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_ORE_MULT_COPPER = BUILDER
            .comment("Keep probability for blocks in minecraft:copper_ores.")
            .defineInRange("floatingIslandsOreMultiplierCopper", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_ORE_MULT_IRON = BUILDER
            .comment("Keep probability for blocks in minecraft:iron_ores.")
            .defineInRange("floatingIslandsOreMultiplierIron", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_ORE_MULT_GOLD = BUILDER
            .comment("Keep probability for blocks in minecraft:gold_ores (overworld + nether gold in those tags).")
            .defineInRange("floatingIslandsOreMultiplierGold", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_ORE_MULT_REDSTONE = BUILDER
            .comment("Keep probability for blocks in minecraft:redstone_ores.")
            .defineInRange("floatingIslandsOreMultiplierRedstone", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_ORE_MULT_LAPIS = BUILDER
            .comment("Keep probability for blocks in minecraft:lapis_ores.")
            .defineInRange("floatingIslandsOreMultiplierLapis", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_ORE_MULT_DIAMOND = BUILDER
            .comment("Keep probability for blocks in minecraft:diamond_ores.")
            .defineInRange("floatingIslandsOreMultiplierDiamond", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_ORE_MULT_EMERALD = BUILDER
            .comment("Keep probability for blocks in minecraft:emerald_ores.")
            .defineInRange("floatingIslandsOreMultiplierEmerald", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_SPAWN_TUNING_ENABLED = BUILDER
            .comment(
                    "When true, floating-islands overworld **natural** chunk spawns (night mobs, etc.) are thinned with the keep-chance options below.",
                    "Spawners, structures, breeding, eggs, and commands are not affected.")
            .define("floatingIslandsSpawnTuningEnabled", true);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_NATURAL_MONSTER_SPAWN_KEEP_CHANCE = BUILDER
            .comment(
                    "Per natural spawn attempt for non-creeper **monsters** (zombies, skeletons, spiders, …): keep with this probability (1 = vanilla, 0.35 ≈ 65% fewer).",
                    "Creepers use floatingIslandsNaturalCreeperSpawnKeepChance instead.",
                    "Vanilla illagers (pillager, vindicator, …) use floatingIslandsNaturalIllagerSpawnKeepChance instead.")
            .defineInRange("floatingIslandsNaturalMonsterSpawnKeepChance", 0.42d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_NATURAL_ILLAGER_SPAWN_KEEP_CHANCE = BUILDER
            .comment(
                    "Per natural spawn attempt for vanilla **illagers** (pillager, vindicator, evoker, vex, ravager, illusioner): keep probability.",
                    "Separate from floatingIslandsNaturalMonsterSpawnKeepChance so patrol/outpost pressure can rise without raising all hostiles.")
            .defineInRange("floatingIslandsNaturalIllagerSpawnKeepChance", 0.58d, 0.0d, 1.0d);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FLOATING_ISLANDS_SPAWN_TUNING_BYPASS_ENTITY_NAMESPACES = BUILDER
            .comment(
                    "Entity registry **namespaces** that skip floating-islands spawn thinning for NATURAL / CHUNK_GENERATION (**keep = 1**).",
                    "Useful for rare boss/content mods. Default includes **mowziesmobs** and **cnb** (Creatures and Beasts: Continued); remove entries to thin them like vanilla monsters.",
                    "Each entry must match lowercase mod-id pattern **`^[a-z0-9_]+$`**.")
            .defineListAllowEmpty(
                    "floatingIslandsSpawnTuningBypassEntityNamespaces",
                    () -> List.copyOf(DEFAULT_SPAWN_TUNING_BYPASS_ENTITY_NAMESPACES),
                    Config::validateSpawnTuningBypassEntityNamespace);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_REALM_RPG_BALLOONS_SPAWN_FIX_ENABLED = BUILDER
            .comment(
                    "When **true** (default) and mod **realmrpg_balloons** (Realm RPG: Treasure Balloons) is loaded: on the floating-islands overworld, new balloon entities are **raised** to at least **floatingIslandsRealmrpgBalloonsMinBlocksAboveIslandSurface** above the procedural island top at their X/Z. If that column is void, nearby columns are searched before the join is **cancelled**.",
                    "Does not affect entities loaded from disk (world save).")
            .define("floatingIslandsRealmrpgBalloonsSpawnFixEnabled", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_REALM_RPG_BALLOONS_MIN_BLOCKS_ABOVE_SURFACE = BUILDER
            .comment(
                    "Minimum blocks **above** the island surface Y used by **floatingIslandsRealmrpgBalloonsSpawnFixEnabled** for **realmrpg_balloons** entities (first join only).")
            .defineInRange("floatingIslandsRealmrpgBalloonsMinBlocksAboveIslandSurface", 14, 0, 128);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_NATURAL_CREEPER_SPAWN_KEEP_CHANCE = BUILDER
            .comment(
                    "Per natural spawn attempt for **creepers** only (explosion damage on small islands). Lower = fewer creepers (e.g. **0.08** ≈ 1 in 12 attempts).")
            .defineInRange("floatingIslandsNaturalCreeperSpawnKeepChance", 0.08d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_NATURAL_CREATURE_SPAWN_KEEP_CHANCE = BUILDER
            .comment(
                    "Per natural spawn attempt for **land animals** (pigs, sheep, cows, chickens, …): keep with this probability (**1** = vanilla rate before optional multiplier below).")
            .defineInRange("floatingIslandsNaturalCreatureSpawnKeepChance", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_NATURAL_CREATURE_SPAWN_MULTIPLIER = BUILDER
            .comment(
                    "After a natural land **animal** spawn succeeds and passes the keep chance above, roll again: with probability **(multiplier − 1)** (capped at 1) spawn one extra tagged duplicate nearby.",
                    "**1** disables; **1.2** ≈ 20% extra animals. Does not apply to breeding, eggs, or spawners.")
            .defineInRange("floatingIslandsNaturalCreatureSpawnMultiplier", 1.2d, 1.0d, 2.5d);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_ENABLED = BUILDER
            .comment(
                    "When **true** (default): on floating-islands overworld during **daytime**, periodically roll each online player's biome **CREATURE** spawn list at a random **nearby loaded island surface** column.",
                    "Complements vanilla natural spawning (which rarely finds grass columns on small islands). Uses **NATURAL** so **`floatingIslandsNaturalCreatureSpawnKeepChance`** still applies.")
            .define("floatingIslandsDaytimeCreatureSpawnBoostEnabled", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_INTERVAL_TICKS = BUILDER
            .comment(
                    "How often the boost runs globally (**one** tick per interval). Each eligible player is considered on their own staggered sub-tick (see implementation). Minimum **20**.")
            .defineInRange("floatingIslandsDaytimeCreatureSpawnBoostIntervalTicks", 200, 20, 12000);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_RADIUS_BLOCKS = BUILDER
            .comment("Horizontal search radius around each player for a candidate island column (blocks).")
            .defineInRange("floatingIslandsDaytimeCreatureSpawnBoostRadiusBlocks", 56, 8, 160);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_TRIES_PER_PLAYER = BUILDER
            .comment("How many random columns to try per player each time their stagger fires (**1–8**).")
            .defineInRange("floatingIslandsDaytimeCreatureSpawnBoostTriesPerPlayer", 3, 1, 8);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_DAYTIME_CREATURE_SPAWN_BOOST_NEARBY_CAP = BUILDER
            .comment(
                    "Skip spawning if this many **CREATURE** mobs already exist within ~**28** blocks of the candidate feet position (reduces clumping).")
            .defineInRange("floatingIslandsDaytimeCreatureSpawnBoostNearbyCap", 10, 2, 64);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_PACK_SPAWN_BOOST_ENABLED = BUILDER
            .comment(
                    "When **true** (default): reloadable **`data/projectisland/floating_island_pack_spawns/rules.json`** drives extra **NATURAL** spawns on floating-islands overworld (e.g. sky **cnb:end_whale** on **`#minecraft:is_overworld`**).",
                    "Datapacks can override that path. **0** rules disables work until reload.")
            .define("floatingIslandsPackSpawnBoostEnabled", true);

    public static final ModConfigSpec.BooleanValue FLOATING_ISLANDS_PACK_SPAWN_BOOST_DAY_ONLY = BUILDER
            .comment(
                    "When **true** (default), pack spawn attempts only run during the same **daytime** window as **`floatingIslandsDaytimeCreatureSpawnBoost`**.",
                    "Set **false** for 24h pack spawns (still throttled by interval and chunk cooldown).")
            .define("floatingIslandsPackSpawnBoostDayOnly", true);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_PACK_SPAWN_BOOST_INTERVAL_TICKS = BUILDER
            .comment("Per-player stagger interval for pack spawn attempts (minimum **40**).")
            .defineInRange("floatingIslandsPackSpawnBoostIntervalTicks", 400, 40, 24000);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_PACK_SPAWN_BOOST_RADIUS_BLOCKS = BUILDER
            .comment("Horizontal search radius around each player for a candidate island column (blocks).")
            .defineInRange("floatingIslandsPackSpawnBoostRadiusBlocks", 72, 16, 192);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_PACK_SPAWN_BOOST_TRIES_PER_PLAYER = BUILDER
            .comment("Random island columns to try per player each time their stagger fires.")
            .defineInRange("floatingIslandsPackSpawnBoostTriesPerPlayer", 2, 1, 12);

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_PACK_SPAWN_CHUNK_COOLDOWN_TICKS = BUILDER
            .comment(
                    "Minimum **game ticks** between successful **pack** spawns in the same chunk (**0** disables). Reduces bursts when exploring fast.")
            .defineInRange("floatingIslandsPackSpawnChunkCooldownTicks", 3600, 0, 120000);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_NATURAL_VILLAGER_SPAWN_KEEP_CHANCE = BUILDER
            .comment(
                    "Per natural spawn attempt for **villagers** (e.g. from village mechanics): keep with this probability (**1** = unchanged).",
                    "Most villagers come from village structures; this covers villager-type spawns that use natural/chunk-generation.")
            .defineInRange("floatingIslandsNaturalVillagerSpawnKeepChance", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_NATURAL_AMBIENT_SPAWN_KEEP_CHANCE = BUILDER
            .comment("Natural spawns for **ambient** mobs (bats): keep chance (1 = unchanged).")
            .defineInRange("floatingIslandsNaturalAmbientSpawnKeepChance", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_NATURAL_WATER_CREATURE_SPAWN_KEEP_CHANCE = BUILDER
            .comment("Natural spawns for **water creatures** (squid, etc.): keep chance on islands (1 = unchanged).")
            .defineInRange("floatingIslandsNaturalWaterCreatureSpawnKeepChance", 1.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.IntValue SPAWN_PREGEN_CHUNK_RADIUS = BUILDER
            .comment(
                    "On server start, pregenerate chunks in an L-infinity (Chebyshev) neighborhood around overworld spawn.",
                    "0 disables. Small values (e.g. 4–8) reduce first-join hitching; large values slow server startup.")
            .defineInRange("spawnPregenChunkRadius", 4, 0, 128);

    public static final ModConfigSpec.IntValue SPAWN_PREGEN_CHUNKS_PER_TICK = BUILDER
            .comment("How many FULL chunks to load per server tick while spawn pregeneration is running (minimum 1).")
            .defineInRange("spawnPregenChunksPerTick", 4, 1, 64);

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_SYNC_ENABLED = BUILDER
            .comment(
                    "When true, players in the floating-islands overworld receive periodic nearby-island **names** (HUD beacons).",
                    "When false, the server sends an empty list on the same interval so clients clear any stale HUD.")
            .define("islandHudSyncEnabled", true);

    public static final ModConfigSpec.IntValue ISLAND_HUD_SYNC_INTERVAL_TICKS = BUILDER
            .comment("How often each player is sent an island HUD update (in ticks, 20 = 1 second).")
            .defineInRange("islandHudSyncIntervalTicks", 40, 1, 600);

    public static final ModConfigSpec.IntValue ISLAND_HUD_REGION_SCAN_RADIUS = BUILDER
            .comment(
                    "Chebyshev radius in island regions around the player to include in each HUD sync (0 = only the player's current region).")
            .defineInRange("islandHudRegionScanRadius", 10, 0, 48);

    public static final ModConfigSpec.IntValue ISLAND_HUD_HEIGHT_ABOVE_PEAK_BLOCKS = BUILDER
            .comment("Vertical offset in blocks above each island's procedural surface peak for the HUD anchor.")
            .defineInRange("islandHudHeightAbovePeakBlocks", 20, 4, 128);

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_WAYSTONE_TITLE_WHEN_LOADED = BUILDER
            .comment(
                    "When **waystones** is installed, use the **nearest named waystone** inside the procedural island (see `FloatingIslandLayout#columnContains`) as the **island HUD title** instead of the generated name.",
                    "Falls back to **`FloatingIslandDisplayName`** when no named waystone is found. Set **false** to always use generated names.")
            .define("islandHudWaystoneTitleWhenLoaded", true);

    public static final ModConfigSpec.IntValue ISLAND_HUD_WAYSTONE_TITLE_CACHE_TICKS = BUILDER
            .comment(
                    "How long (in ticks) to cache the resolved waystone title per island region before re-querying Waystones' manager (renames pick up after this interval, or immediately after right-clicking a waystone).",
                    "Higher values reduce repeated lookups; lower values refresh faster.")
            .defineInRange("islandHudWaystoneTitleCacheTicks", 120, 20, 72000);

    public static final ModConfigSpec.BooleanValue STARTER_ISLAND_AUTO_ASSIGN_ENABLED = BUILDER
            .comment(
                    "On first join to the floating-islands overworld, assign a starter-home island region (persisted **`StarterHomes`** mapping only), then teleport to that island's procedural center (HUD-aligned).",
                    "Players who already have a starter home entry are unchanged. Void rescue still runs afterward if needed.")
            .define("starterIslandAutoAssignEnabled", true);

    public static final ModConfigSpec.BooleanValue STARTER_ISLAND_SHARED_HUB = BUILDER
            .comment(
                    "When true: after the server picks a **shared starter hub** island, **new** players without a starter home are sent there (same region as existing starters) instead of claiming a new island.",
                    "The hub is created by the first successful starter claim while **starterIslandSplitWhenWorldSpawnMoves** still considers spawn unchanged (see that option).",
                    "When false: every new player spirals for their own AVAILABLE island (legacy one-island-per-player behavior).")
            .define("starterIslandSharedHub", true);

    public static final ModConfigSpec.BooleanValue STARTER_ISLAND_SPLIT_WHEN_WORLD_SPAWN_MOVES = BUILDER
            .comment(
                    "When **starterIslandSharedHub** is true: the overworld **shared spawn XZ** is remembered the first time starter assignment runs.",
                    "If an operator later moves world spawn (e.g. `/setworldspawn`) so **XZ** differs, **new** players without a starter home claim **their own** islands again (**starterIslandMinRegionSeparation** applies).",
                    "Set false to keep assigning everyone to the shared hub even after spawn moves.")
            .define("starterIslandSplitWhenWorldSpawnMoves", true);

    public static final ModConfigSpec.BooleanValue STARTER_ISLAND_SEARCH_FROM_WORLD_ORIGIN = BUILDER
            .comment(
                    "When true, the starter region spiral anchors at world block column (0, 0) (region containing that column).",
                    "Takes precedence over starterIslandSearchFromWorldSpawn when enabled.")
            .define("starterIslandSearchFromWorldOrigin", false);

    public static final ModConfigSpec.BooleanValue STARTER_ISLAND_SEARCH_FROM_WORLD_SPAWN = BUILDER
            .comment(
                    "When starterIslandSearchFromWorldOrigin is false: if true, the spiral starts at the overworld shared spawn chunk.",
                    "If false, the spiral starts at the player's join chunk (useful for tests).")
            .define("starterIslandSearchFromWorldSpawn", true);

    public static final ModConfigSpec.IntValue STARTER_ISLAND_MAX_REGION_SEARCH_RADIUS = BUILDER
            .comment(
                    "Chebyshev radius in island regions (8×8 chunks each) when searching for an AVAILABLE starter candidate.",
                    "Increase on dense servers if nearby regions are all claimed.")
            .defineInRange("starterIslandMaxRegionSearchRadius", 96, 1, 4096);

    public static final ModConfigSpec.IntValue STARTER_ISLAND_MIN_REGION_SEPARATION = BUILDER
            .comment(
                    "Minimum Chebyshev distance in regions between a new starter island and any existing starter home island.",
                    "0 disables separation checks.")
            .defineInRange("starterIslandMinRegionSeparation", 0, 0, 256);

    public static final ModConfigSpec.ConfigValue<String> STARTER_ISLAND_FAILURE_KICK_MESSAGE = BUILDER
            .comment(
                    "If non-empty and no starter island could be assigned within the search radius, disconnect the joining player with this literal message (otherwise they stay at join position and void rescue may still run).")
            .define("starterIslandFailureKickMessage", "");

    public static final ModConfigSpec.BooleanValue STARTER_ISLAND_SUPPLY_CHEST_ENABLED = BUILDER
            .comment(
                    "After a player's **first** starter-home assignment succeeds, place **one** loot chest on that island region (near the procedural center, offset from the spawn column).",
                    "Loot table: **`projectisland:chests/starter_supply`** (harpoon, elytra, food, torches, basic tools, etc.).",
                    "With **Lootr** installed, behavior follows Lootr's conversion rules for vanilla loot chests.",
                    "Persisted per region and block position in **`FloatingIslandSavedData`** so each starter island (hub or per-player) gets at most one chest; that chest resists **explosions** and **survival** mining (creative **instabuild** can still remove it).")
            .define("starterIslandSupplyChestEnabled", true);

    public static final ModConfigSpec.BooleanValue VOID_RESCUE_EACH_TICK = BUILDER
            .comment(
                    "When true, the server watches floating-islands overworld players in the void: **bed / starter / relocate** and optional **last-safe** snap (voidRescueSnapToLastSafe*) run only inside the **void-floor band** (**minBuildHeight** + **voidRescueTriggerBlocksAboveMinY**; see **FloatingIslandVoidRescue**) — not while you are only a few blocks under an island.",
                    "Join / dimension change still runs immediate void relocation when you are not on a surface.")
            .define("voidRescueEachTick", true);

    public static final ModConfigSpec.IntValue VOID_RESCUE_TRIGGER_BLOCKS_ABOVE_MIN_Y = BUILDER
            .comment(
                    "With voidRescueEachTick: when feet Y is at or below (minBuildHeight + this value), run rescue if still not supported.",
                    "This must stay a **narrow band near the world minimum** — large values (e.g. 200) treat mid-air under islands as “void” and cause **last-safe + floor rescue every tick** (rubber-band / teleport loops). Vanilla overworld min is **-64**; **12** ⇒ rescue at **Y≤-52**.",
                    "Hard-capped at **64** so the void-floor band cannot swallow normal overworld heights. Join / dimension relocate still runs when unsupported at any height (see FloatingIslandVoidRescue).")
            .defineInRange("voidRescueTriggerBlocksAboveMinY", 12, 0, 64);

    public static final ModConfigSpec.BooleanValue VOID_RESCUE_SNAP_TO_LAST_SAFE_ENABLED = BUILDER
            .comment(
                    "In the **void-floor band** only (same Y rule as bed/starter/relocate): optionally teleport you back to the last feet position that was on solid / island surface once you drop voidRescueSnapToLastSafeMinFallBlocks below that Y.",
                    "Does **not** run high under islands — avoids snap/relocate thrash and vanilla “Flying is not enabled” when voidRescueSnapToLastSafeMinFallBlocks is small.")
            .define("voidRescueSnapToLastSafeEnabled", true);

    public static final ModConfigSpec.IntValue VOID_RESCUE_SNAP_TO_LAST_SAFE_MIN_FALL_BLOCKS = BUILDER
            .comment(
                    "Used only inside the void-floor band: vertical gap below the saved last-safe Y before the snap runs.",
                    "Very small values only affect behavior near the world bottom, not a few blocks under an island.")
            .defineInRange("voidRescueSnapToLastSafeMinFallBlocks", 20, 4, 256);

    public static final ModConfigSpec.IntValue VOID_RESCUE_SNAP_TO_LAST_SAFE_COOLDOWN_TICKS = BUILDER
            .comment("Ticks after a last-safe snap before another mid-void snap can run (prevents thrash if the spot is no longer valid).")
            .defineInRange("voidRescueSnapToLastSafeCooldownTicks", 40, 0, 200);

    public static final ModConfigSpec.BooleanValue VOID_RESCUE_RESET_VANILLA_FLOATING_PACKET_COUNTERS = BUILDER
            .comment(
                    "On the **floating-islands overworld**, while **`FloatingIslandVoidRescue`** considers you **unsupported** (open void / rim gaps, etc.), each tick resets vanilla's move-packet floating accumulators on your connection.",
                    "Prevents **Disconnected: Flying is not enabled** / **kicked for floating too long** on long falls and void rescue when **`allow-flight`** is **false** in **`server.properties`**. Set **false** if you want vanilla's strict checks even in the void.")
            .define("voidRescueResetVanillaFloatingPacketCounters", true);

    public static final ModConfigSpec.BooleanValue ROPE_LINK_SYNC_ENABLED = BUILDER
            .comment(
                    "When true, players in the floating-islands overworld receive rope anchor segment positions for client rendering.",
                    "When false, the server sends an empty list on the same interval so clients clear stale segments.")
            .define("ropeLinkSyncEnabled", true);

    public static final ModConfigSpec.IntValue ROPE_LINK_SYNC_INTERVAL_TICKS = BUILDER
            .comment("How often each player is sent a rope link update (in ticks, 20 = 1 second).")
            .defineInRange("ropeLinkSyncIntervalTicks", 20, 1, 600);

    public static final ModConfigSpec.IntValue ROPE_LINK_SYNC_CULL_RADIUS_BLOCKS = BUILDER
            .comment(
                    "A rope segment is included if either anchor or the midpoint is within this horizontal Chebyshev distance (blocks) of the player.",
                    "Increase if long spans disappear while you stand between islands.")
            .defineInRange("ropeLinkSyncCullRadiusBlocks", 384, 32, 2048);

    public static final ModConfigSpec.IntValue ROPE_LINK_RAYCAST_RANGE_BLOCKS = BUILDER
            .comment(
                    "Harpoon gun raycast reach (blocks) for each shot when placing rope anchors.",
                    "Should be **≥ ropeLinkMaxLengthBlocks** so you can aim at the far island from the near one; defaults match max span.")
            .defineInRange("ropeLinkRaycastRangeBlocks", 256, 8, 512);

    public static final ModConfigSpec.IntValue ROPE_LINK_MAX_LENGTH_BLOCKS = BUILDER
            .comment(
                    "Maximum Euclidean distance (blocks) between two anchors for a new rope link (server-validated).",
                    "Default **256** fits typical floating-neighbor islands (~117+ blocks is common); raise up to **1024** for rare layouts. Stored on each RopeLink for strain / future rules.")
            .defineInRange("ropeLinkMaxLengthBlocks", 256, 16, 1024);

    public static final ModConfigSpec.DoubleValue ROPE_LINK_MAX_HEALTH = BUILDER
            .comment(
                    "Maximum (and initial) hit points per rope link. Strain lowers health; at 0 the link snaps and anchors restore.")
            .defineInRange("ropeLinkMaxHealth", 100.0d, 1.0d, 1_000_000.0d);

    public static final ModConfigSpec.BooleanValue ROPE_PROGRESSION_UPGRADE_EXISTING_LINKS = BUILDER
            .comment(
                    "When true, when a player unlocks a higher rope tier (advancement), existing RopeLinks they own are upgraded server-side (max length + max health).",
                    "Upgrades preserve the current health fraction. Disable if you want tiers to affect new links only.")
            .define("ropeProgressionUpgradeExistingLinks", true);

    public static final ModConfigSpec.IntValue ROPE_PROGRESSION_UPGRADE_INTERVAL_TICKS = BUILDER
            .comment("How often (ticks) to scan and upgrade existing rope links when ropeProgressionUpgradeExistingLinks is enabled.")
            .defineInRange("ropeProgressionUpgradeIntervalTicks", 200, 1, 20_000);

    public static final ModConfigSpec.IntValue ROPE_LINK_STRESS_TICK_INTERVAL = BUILDER
            .comment("Server ticks between strain evaluations (span vs max length) and optional damage. 20 ≈ once per second.")
            .defineInRange("ropeLinkStressTickInterval", 20, 1, 1200);

    public static final ModConfigSpec.DoubleValue ROPE_LINK_STRAIN_RATIO_THRESHOLD = BUILDER
            .comment(
                    "When chord length / maxLinkLength exceeds this ratio, the rope takes strain damage each stress tick (e.g. 0.88 = 88% of allowed span).")
            .defineInRange("ropeLinkStrainRatioThreshold", 0.88d, 0.5d, 0.999d);

    public static final ModConfigSpec.DoubleValue ROPE_LINK_STRAIN_DAMAGE_PER_TICK = BUILDER
            .comment(
                    "Hit points removed per stress tick while over the strain threshold. Scales up as span approaches max length.")
            .defineInRange("ropeLinkStrainDamagePerTick", 1.5d, 0.0d, 10_000.0d);

    public static final ModConfigSpec.DoubleValue ROPE_ANCHOR_LINK_DAMAGE_PER_DIG_TICK = BUILDER
            .comment(
                    "Survival/adventure: each time a **linked** rope anchor would be removed by mining, **link HP** is reduced (scaled by swing cooldown and how fast the block would break) and the break is blocked until 0% HP.",
                    "At **0** the anchor breaks like a normal block (one removal still severs the link via strain-style cleanup).",
                    "Default **0.35** is tuned for **ropeLinkMaxHealth** 100; raise to chip faster per completed mining attempt.")
            .defineInRange("ropeAnchorLinkDamagePerDigTick", 0.35d, 0.0d, 20.0d);

    public static final ModConfigSpec.BooleanValue ROPE_TOPOLOGY_ENABLED = BUILDER
            .comment("**Legacy — ignored.** Harpoon ziplines are not restricted by starter-centric graph rules.")
            .define("ropeTopologyEnabled", true);

    public static final ModConfigSpec.IntValue ROPE_TOPOLOGY_MAX_DEPTH_FROM_STARTER = BUILDER
            .comment("**Legacy — ignored.**")
            .defineInRange("ropeTopologyMaxDepthFromStarter", 2, 0, 8);

    public static final ModConfigSpec.BooleanValue ROPE_ALLOW_TERTIARY_ISLAND_LINKS = BUILDER
            .comment("**Legacy — ignored.**")
            .define("ropeAllowTertiaryIslandLinks", false);

    public static final ModConfigSpec.IntValue ROPE_MAIN_DIRECT_SPOKE_CAP = BUILDER
            .comment("**Legacy — ignored.**")
            .defineInRange("ropeMainDirectSpokeCap", 1, 1, 4);

    public static final ModConfigSpec.IntValue ROPE_SISTER_OUTBOUND_CAP = BUILDER
            .comment("**Legacy — ignored.**")
            .defineInRange("ropeSisterOutboundCap", 1, 1, 3);

    public static final ModConfigSpec.BooleanValue SECONDARY_CLAIM_REQUIRES_ROPE_LINK = BUILDER
            .comment("**Legacy — ignored.** Island **claims** and rope placement were decoupled; `/projectisland island claim` was removed.")
            .define("secondaryClaimRequiresRopeLink", true);

    public static final ModConfigSpec.IntValue SECONDARY_CLAIM_COMMAND_MAX_DISTANCE_BLOCKS = BUILDER
            .comment("**Legacy — ignored.**")
            .defineInRange("secondaryClaimCommandMaxDistanceBlocks", 160, 0, 2048);

    public static final ModConfigSpec.BooleanValue AUTO_CLAIM_ON_ROPE_LINK = BUILDER
            .comment("**Legacy — ignored.**")
            .define("autoClaimIslandOnRopeLink", true);

    public static final ModConfigSpec.IntValue SECONDARY_CLAIM_COMMAND_PERMISSION_LEVEL = BUILDER
            .comment("**Legacy — ignored.**")
            .defineInRange("secondaryClaimCommandPermissionLevel", 0, 0, 4);

    public static final ModConfigSpec.BooleanValue ROPE_TRAVERSAL_SURF_ENABLED = BUILDER
            .comment(
                    "When true, empty-hand use (not sneaking) on a linked rope anchor starts rope surfing along the sag toward the other anchor (any player).")
            .define("ropeTraversalSurfEnabled", true);

    public static final ModConfigSpec.DoubleValue ROPE_TRAVERSAL_SURF_MIN_HEALTH_FRACTION = BUILDER
            .comment("Minimum rope health fraction (0–1) required to start or continue rope surfing.")
            .defineInRange("ropeTraversalSurfMinHealthFraction", 0.12d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue ROPE_TRAVERSAL_SURF_SPEED_BLOCKS_PER_SECOND = BUILDER
            .comment(
                    "Approximate rope-surf speed along the rope curve (blocks per second).",
                    "Lower if dedicated-server movement validation kicks you; raise for faster zips (SP or allow-flight true).")
            .defineInRange("ropeTraversalSurfSpeedBlocksPerSecond", 12.0d, 0.5d, 80.0d);

    public static final ModConfigSpec.IntValue ROPE_TRAVERSAL_SURF_COOLDOWN_TICKS = BUILDER
            .comment("Ticks after completing a rope surf before another can start (0 = none). Sneak-cancel does not apply cooldown.")
            .defineInRange("ropeTraversalSurfCooldownTicks", 45, 0, 1200);

    public static final ModConfigSpec.IntValue ROPE_TRAVERSAL_SURF_MAX_DURATION_TICKS = BUILDER
            .comment(
                    "Safety cap: if rope surfing lasts longer than this (ticks), the server clears surf state.",
                    "Prevents a stuck \"already surfing\" session if movement packets desync.")
            .defineInRange("ropeTraversalSurfMaxDurationTicks", 2400, 40, 120_000);

    public static final ModConfigSpec.BooleanValue MOB_ROPE_SURF_ENABLED = BUILDER
            .comment(
                    "When true, entity types in **`data/projectisland/tags/entity_types/rope_surfing_mobs.json`** can auto-start rope surfing on linked anchors in the floating-islands overworld (server curve motion + link wear).")
            .define("mobRopeSurfEnabled", true);

    public static final ModConfigSpec.DoubleValue MOB_ROPE_SURF_DEFER_AUTO_WHEN_PLAYER_TARGET_WITHIN_BLOCKS = BUILDER
            .comment(
                    "When **> 0**, a mob with **`getTarget()`** on a **living player** in the **same level** within this **spherical** distance (blocks) defers auto-surf **only** when the **far** anchor is horizontally **farther** from that player than the anchor the mob is touching (chase on the rim / hallway — avoids riding away from the fight).",
                    "If the far anchor is **closer** to the player than the near anchor, auto-surf is **not** deferred so mobs can cross toward you. Defer is **off** while the target is player rope surfing. Set **0** to always allow auto-surf.")
            .defineInRange("mobRopeSurfDeferAutoWhenPlayerTargetWithinBlocks", 40.0d, 0.0d, 512.0d);

    public static final ModConfigSpec.DoubleValue MOB_ROPE_SURF_SPEED_BLOCKS_PER_SECOND = BUILDER
            .comment("Mob rope-surf speed along the sag curve (blocks per second), same integration as player surf.")
            .defineInRange("mobRopeSurfSpeedBlocksPerSecond", 12.0d, 0.5d, 80.0d);

    public static final ModConfigSpec.DoubleValue MOB_ROPE_DAMAGE_PER_COMPLETED_CROSSING = BUILDER
            .comment(
                    "Hit points removed from the shared **RopeLink** when a mob **finishes** one crossing (primary wear lever).",
                    "Stacks with **mobRopeDamagePerAdvanceDuringCrossing** while moving.")
            .defineInRange("mobRopeDamagePerCompletedCrossing", 28.0d, 0.0d, 1_000_000.0d);

    public static final ModConfigSpec.IntValue MOB_ROPE_MAX_CROSSINGS_BEFORE_SEVER = BUILDER
            .comment(
                    "If **> 0**, the link is severed when **mob crossings completed** reaches this count (both anchors), even if HP would remain.",
                    "**0** disables this cap (HP-only sever).")
            .defineInRange("mobRopeMaxCrossingsBeforeSever", 0, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue MOB_ROPE_DAMAGE_PER_ADVANCE_DURING_CROSSING = BUILDER
            .comment(
                    "Optional extra **RopeLink** HP removed **each server tick** while a mob is actively surfing (**0** = completion damage only).")
            .defineInRange("mobRopeDamagePerAdvanceDuringCrossing", 0.0d, 0.0d, 10_000.0d);

    public static final ModConfigSpec.IntValue MOB_ROPE_SURF_MAX_DURATION_TICKS = BUILDER
            .comment("Safety cap (ticks) for an active mob rope surf session before the server clears surf state.")
            .defineInRange("mobRopeSurfMaxDurationTicks", 2400, 40, 120_000);

    public static final ModConfigSpec.IntValue MOB_ROPE_AUTO_TRY_COOLDOWN_TICKS = BUILDER
            .comment(
                    "Minimum ticks between **auto-start** attempts for the same mob near anchors (reduces thrash when blocked).")
            .defineInRange("mobRopeAutoTryCooldownTicks", 40, 0, 1200);

    public static final ModConfigSpec.IntValue MOB_ROPE_AUTO_START_HORIZONTAL_CHEB_BLOCKS = BUILDER
            .comment(
                    "Auto-start: mob **feet** may be up to this **horizontal** Chebyshev distance (blocks) from an anchor and still try that link (default **10** — ~6–10 block “radius” on the island).",
                    "**0** = legacy: only a small box around feet plus **touch** (collision / slight inflate) on the anchor counts.")
            .defineInRange("mobRopeAutoStartHorizontalChebBlocks", 10, 0, 64);

    public static final ModConfigSpec.IntValue MOB_ROPE_AUTO_START_VERTICAL_BLOCKS = BUILDER
            .comment(
                    "With **mobRopeAutoStartHorizontalChebBlocks** > **0**: max **vertical** |feet Y − anchor Y| (default **128**). **0** = treat as **128** when horizontal mode is on.")
            .defineInRange("mobRopeAutoStartVerticalBlocks", 128, 0, 384);

    public static final ModConfigSpec.BooleanValue MOB_ROPE_ANCHOR_NAVIGATION_GOAL_ENABLED = BUILDER
            .comment(
                    "When true, **PathfinderMob** types in **`rope_surfing_mobs`** (e.g. zombie, skeleton) get an AI goal to **walk toward** the nearest legal rope anchor within the auto-start radii, then **`tryStart`** (bypasses player-target defer used by bump auto-start).",
                    "Non-**PathfinderMob** tag members (e.g. pillager) still use tick bump detection only. Set **false** to rely on bump auto-start alone.")
            .define("mobRopeAnchorNavigationGoalEnabled", true);

    public static final ModConfigSpec.IntValue MOB_ROPE_GOAL_PRIORITY = BUILDER
            .comment(
                    "Lower = higher AI priority (vanilla). Default **6** — below melee (**~2**) so close combat wins, above random stroll (**~8**) so crossing is preferred when out of melee.")
            .defineInRange("mobRopeGoalPriority", 6, 1, 15);

    public static final ModConfigSpec.IntValue MOB_ROPE_POST_CROSSING_COOLDOWN_TICKS = BUILDER
            .comment(
                    "After a mob **finishes** a rope surf, ticks before that mob may auto-start or goal-start again (**default 200** ≈ **10** s at 20 TPS) — reduces ping-pong loops on the same link.")
            .defineInRange("mobRopePostCrossingCooldownTicks", 200, 0, 6000);

    public static final ModConfigSpec.DoubleValue MOB_ROPE_GOAL_ANCHOR_START_DIST_BLOCKS = BUILDER
            .comment(
                    "Navigation goal: when the mob is within this **Euclidean** distance (blocks) of the anchor block center, **`tryStart`** runs (default **2.75**).")
            .defineInRange("mobRopeGoalAnchorStartDistBlocks", 2.75d, 0.5d, 12.0d);

    public static final ModConfigSpec.IntValue MOB_ROPE_GOAL_REPATH_INTERVAL_TICKS = BUILDER
            .comment("Navigation goal: refresh **`moveTo`** toward the anchor every this many ticks while chasing the anchor (default **40**).")
            .defineInRange("mobRopeGoalRepathIntervalTicks", 40, 5, 200);

    public static final ModConfigSpec.IntValue MOB_ROPE_MAX_SURFING_PER_LINK = BUILDER
            .comment(
                    "Max **concurrent** mob surfers per **RopeLink** id (**0** = unlimited). Starts beyond this cap are rejected until someone finishes or clears.")
            .defineInRange("mobRopeMaxSurfingPerLink", 0, 0, 64);

    public static final ModConfigSpec.BooleanValue MOB_ROPE_FOLLOW_PLAYER_SURF_ENABLED = BUILDER
            .comment(
                    "When true, **rope_surfing_mobs** that **target** a **ServerPlayer** receive a short **follow intent** when that player **starts** rope surfing from an anchor: they prefer that **departure** anchor for the navigation goal / bump auto-start (same link the player took).",
                    "Requires **mobRopeSurfEnabled**. Set **false** to disable chase-follow onto the rope.")
            .define("mobRopeFollowPlayerSurfEnabled", true);

    public static final ModConfigSpec.DoubleValue MOB_ROPE_FOLLOW_PLAYER_SURF_ASSIGN_RANGE_BLOCKS = BUILDER
            .comment(
                    "When assigning follow intent: mobs whose **AABB** intersects this **axis-aligned** expansion of the player’s bounding box (± blocks on each axis) and have **getTarget() ==** that player are eligible.",
                    "**0** disables assigning new intents (existing intents still expire normally).")
            .defineInRange("mobRopeFollowPlayerSurfAssignRangeBlocks", 32.0d, 0.0d, 128.0d);

    public static final ModConfigSpec.IntValue MOB_ROPE_FOLLOW_PLAYER_SURF_INTENT_TICKS = BUILDER
            .comment(
                    "How long (ticks) follow intent lasts after the player starts surfing — the mob prefers that departure anchor until expiry, target change, or invalid link.",
                    "Minimum **20** when applied.")
            .defineInRange("mobRopeFollowPlayerSurfIntentTicks", 400, 20, 6000);

    public static final ModConfigSpec.BooleanValue MOB_ROPE_NEARBY_PLAYER_WARNING_ENABLED = BUILDER
            .comment(
                    "When true, **ServerPlayer** clients within **`mobRopeNearbyPlayerWarningRangeBlocks`** of a mob that **starts** rope surfing receive an action-bar toast ( **`projectisland.rope.surf.mob_nearby_warning`** ).")
            .define("mobRopeNearbyPlayerWarningEnabled", true);

    public static final ModConfigSpec.DoubleValue MOB_ROPE_NEARBY_PLAYER_WARNING_RANGE_BLOCKS = BUILDER
            .comment(
                    "Spherical **inflate** radius (blocks) around the mob’s position when broadcasting the nearby-player rope-surf warning (**0** = no toast).")
            .defineInRange("mobRopeNearbyPlayerWarningRangeBlocks", 24.0d, 0.0d, 128.0d);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
