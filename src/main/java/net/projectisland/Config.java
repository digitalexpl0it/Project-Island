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

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
