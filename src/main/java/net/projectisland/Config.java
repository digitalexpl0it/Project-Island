package net.projectisland;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Log extra diagnostics during client and common setup (off by default for quieter logs).")
            .define("debugLogging", false);

    public static final ModConfigSpec.DoubleValue FLOATING_ISLANDS_RARE_STRUCTURE_KEEP_CHANCE = BUILDER
            .comment(
                    "In the floating-islands overworld, each vanilla monster room or trial chamber that generates in a chunk is kept with this probability (0 = remove all, 1 = keep all).",
                    "Lowering this reduces floating stone ruins in the void; raising it preserves more combat structures.")
            .defineInRange("floatingIslandsRareStructureKeepChance", 0.12d, 0.0d, 1.0d);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_RIVER = BUILDER
            .comment("Weight for minecraft:river when rolling per-island biome (0 = never). Land uses procedural biomes, not vanilla multi_noise sectors.")
            .defineInRange("islandBiomeWeightRiver", 28, 0, 1000);

    public static final ModConfigSpec.IntValue ISLAND_BIOME_WEIGHT_PLAINS = BUILDER
            .comment("Weight for minecraft:plains (0 = exclude).")
            .defineInRange("islandBiomeWeightPlains", 14, 0, 1000);

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

    public static final ModConfigSpec.IntValue FLOATING_ISLANDS_EXTRA_SURFACE_TREES_PER_CHUNK = BUILDER
            .comment(
                    "After normal biome decoration, try to place this many extra oak / fancy oak / birch trees on grass island tops per chunk (0 disables).")
            .defineInRange("floatingIslandsExtraSurfaceTreesPerChunk", 5, 0, 32);

    public static final ModConfigSpec.IntValue SPAWN_PREGEN_CHUNK_RADIUS = BUILDER
            .comment(
                    "On server start, pregenerate chunks in an L-infinity (Chebyshev) neighborhood around overworld spawn.",
                    "0 disables. Small values (e.g. 4–8) reduce first-join hitching; large values slow server startup.")
            .defineInRange("spawnPregenChunkRadius", 0, 0, 128);

    public static final ModConfigSpec.IntValue SPAWN_PREGEN_CHUNKS_PER_TICK = BUILDER
            .comment("How many FULL chunks to load per server tick while spawn pregeneration is running (minimum 1).")
            .defineInRange("spawnPregenChunksPerTick", 4, 1, 64);

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_SYNC_ENABLED = BUILDER
            .comment(
                    "When true, players in the floating-islands overworld receive periodic island state labels (available / claimed / contested).",
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

    public static final ModConfigSpec.BooleanValue STARTER_ISLAND_AUTO_ASSIGN_ENABLED = BUILDER
            .comment(
                    "On first join to the floating-islands overworld, assign one AVAILABLE island region as the player's starter home (CLAIMED + persisted), then teleport to that island's procedural center (HUD-aligned).",
                    "Players who already have a starter home entry are unchanged. Void rescue still runs afterward if needed.")
            .define("starterIslandAutoAssignEnabled", true);

    public static final ModConfigSpec.BooleanValue STARTER_ISLAND_SEARCH_FROM_WORLD_SPAWN = BUILDER
            .comment(
                    "When true, the region spiral for starter placement starts at the overworld shared spawn chunk.",
                    "When false, the spiral starts at the player's join chunk (useful for tests).")
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

    public static final ModConfigSpec.BooleanValue VOID_RESCUE_EACH_TICK = BUILDER
            .comment(
                    "When true, the server watches floating-islands overworld players in the void and rescues them **once per fall** when they reach **near the world minimum Y** (see voidRescueTriggerBlocksAboveMinY).",
                    "Does **not** teleport mid-air while you are still high above the floor (avoids yanking players at island edges).",
                    "Join / dimension change still runs immediate void relocation when you are not on a surface.")
            .define("voidRescueEachTick", true);

    public static final ModConfigSpec.IntValue VOID_RESCUE_TRIGGER_BLOCKS_ABOVE_MIN_Y = BUILDER
            .comment(
                    "With voidRescueEachTick: when feet Y is at or below (minBuildHeight + this value), run rescue if still not supported.",
                    "This is the **void-floor band** only — keep it small so dungeons / stairs far above the world minimum are not mistaken for the void. Vanilla overworld min is -64; 12 ⇒ rescue at Y≤-52 unless you raise this.",
                    "Join / dimension relocate still runs when unsupported at any height (see FloatingIslandVoidRescue).")
            .defineInRange("voidRescueTriggerBlocksAboveMinY", 12, 0, 512);

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

    public static final ModConfigSpec.BooleanValue ROPE_TOPOLOGY_ENABLED = BUILDER
            .comment(
                    "When true, completing a harpoon link must keep the network connected to your starter island and within depth / spoke caps below.",
                    "Turn off only for unrestricted testing.")
            .define("ropeTopologyEnabled", true);

    public static final ModConfigSpec.IntValue ROPE_TOPOLOGY_MAX_DEPTH_FROM_STARTER = BUILDER
            .comment(
                    "Maximum BFS hop count from the starter region along **your** ropes (0 = starter only). Value **2** allows starter → secondary → tertiary when **ropeAllowTertiaryIslandLinks** is true; otherwise depth is capped at **1** regardless of this number.")
            .defineInRange("ropeTopologyMaxDepthFromStarter", 2, 0, 8);

    public static final ModConfigSpec.BooleanValue ROPE_ALLOW_TERTIARY_ISLAND_LINKS = BUILDER
            .comment(
                    "When **false** (default), topology never lets a rope put an island **two hops** from your starter (only the hub + one ring). Set **true** to apply the full **ropeTopologyMaxDepthFromStarter** depth (e.g. 2 = tertiary islands). Advancement-based unlocks can replace this toggle later.")
            .define("ropeAllowTertiaryIslandLinks", false);

    public static final ModConfigSpec.IntValue ROPE_MAIN_DIRECT_SPOKE_CAP = BUILDER
            .comment(
                    "Max **distinct** other regions linked **directly** to your starter by your ropes (default **1**; raise up to **4** as a stand-in until advancement gates exist).")
            .defineInRange("ropeMainDirectSpokeCap", 1, 1, 4);

    public static final ModConfigSpec.IntValue ROPE_SISTER_OUTBOUND_CAP = BUILDER
            .comment(
                    "Per **claimed** non-starter region you own: max distinct **non-starter** rope neighbors (links back to the starter excluded). Default **1**, max **3**.")
            .defineInRange("ropeSisterOutboundCap", 1, 1, 3);

    public static final ModConfigSpec.BooleanValue SECONDARY_CLAIM_REQUIRES_ROPE_LINK = BUILDER
            .comment(
                    "When true, secondary claims require a harpoon RopeLink owned by the claimer between the target island and an island they already own (starter or prior claim).")
            .define("secondaryClaimRequiresRopeLink", true);

    public static final ModConfigSpec.BooleanValue AUTO_CLAIM_ON_ROPE_LINK = BUILDER
            .comment(
                    "When true, completing a harpoon link immediately **claims** an **AVAILABLE** endpoint if the other endpoint is your **starter home** or an island you already **claim** (no shift-click needed). Turn off to require anchor sneak-use or `/projectisland island claim` only.")
            .define("autoClaimIslandOnRopeLink", true);

    public static final ModConfigSpec.IntValue SECONDARY_CLAIM_COMMAND_PERMISSION_LEVEL = BUILDER
            .comment(
                    "Minimum permission level for `/projectisland island claim` (0 = any player; 2 = OP on vanilla).",
                    "Rope-anchor shift-use is not gated by this — only the Brigadier command.")
            .defineInRange("secondaryClaimCommandPermissionLevel", 0, 0, 4);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
