package net.projectisland.worldgen;

/**
 * Exclusive “featured” structure choice per {@link net.projectisland.island.FloatingIslandKey} (8×8-chunk island region),
 * rolled from config weights like {@link IslandRegionBiomePicker}. Land columns still use one biome per region; this picks
 * which rare vanilla surface/dungeon feature that region is allowed to **keep** when worldgen places one.
 */
public enum IslandRegionRareStructureSlot {
    /** No dungeon / trial / pyramid feature for this region — vanilla placements of those are stripped. */
    NONE,
    MONSTER_ROOM,
    TRIAL_CHAMBERS,
    DESERT_PYRAMID,
    JUNGLE_PYRAMID,
    /** {@code minecraft:mineshaft} — optional; weight {@code 0} disables this slot outcome. */
    MINESHAFT
}
