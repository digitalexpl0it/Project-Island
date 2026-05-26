package net.projectisland.worldgen;

import java.util.Locale;

/**
 * Where Create: Levite Fields ({@code levmod:levitite_fields}) is assigned on the floating-islands overworld.
 */
public enum LeviteFieldsPlacementMode {
    /** Open void near islands only; island surfaces use normal rolls. */
    VOID_ONLY,
    /** Per-island region rolls can include Levite; void uses the normal void biome. */
    ISLAND_ONLY,
    /** Levite in void belt and eligible on island surfaces (via {@code islandBiomeModWeightedEntries} / discover-all). */
    BOTH;

    public static LeviteFieldsPlacementMode fromConfig(String raw) {
        if (raw == null) {
            return VOID_ONLY;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "void_only", "void" -> VOID_ONLY;
            case "island_only", "island", "islands" -> ISLAND_ONLY;
            case "both", "all" -> BOTH;
            default -> VOID_ONLY;
        };
    }
}
