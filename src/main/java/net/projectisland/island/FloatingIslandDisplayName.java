package net.projectisland.island;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Deterministic fantasy-style labels for procedural islands, keyed only by region coordinates
 * (same island always gets the same name on server and client). Word lists default to built-ins and are replaced when
 * {@linkplain FloatingIslandDisplayNameReloader server data reload} reads {@code data/projectisland/floating_island_display_names/names.json}
 * (datapacks may override that path).
 */
public final class FloatingIslandDisplayName {
    private static final int NAME_SEED_SALT = 0x4E174001;

    private static final String[] BUILTIN_ADJECTIVES = {
        "Misty", "Coral", "Jade", "Amber", "Azure", "Dusky", "Ivory", "Onyx", "Opal", "Rust",
        "Sable", "Solar", "Verdant", "Cobalt", "Crimson", "Gilded", "Marble", "Obsidian", "Pearl", "Quartz",
        "Saffron", "Silver", "Velvet", "Winding", "Brine", "Drift", "High", "Low", "Far", "Near"
    };

    private static final String[] BUILTIN_NOUNS = {
        "Atoll", "Barrow", "Bluff", "Cay", "Crest", "Crag", "Delta", "Drift", "Firth", "Haven",
        "Isle", "Knoll", "Lagoon", "Ledge", "Mesa", "Moor", "Nook", "Reach", "Reef", "Ridge",
        "Shoal", "Skerry", "Spire", "Stack", "Strand", "Table", "Tor", "Vale", "Veer", "Ward"
    };

    private static volatile String[] adjectives = Arrays.copyOf(BUILTIN_ADJECTIVES, BUILTIN_ADJECTIVES.length);
    private static volatile String[] nouns = Arrays.copyOf(BUILTIN_NOUNS, BUILTIN_NOUNS.length);

    private FloatingIslandDisplayName() {}

    public static String forRegion(int regionX, int regionZ) {
        long seed = (long) regionX * 3129871L ^ (long) regionZ * 116129811L ^ (long) NAME_SEED_SALT;
        SplittableRandom rnd = new SplittableRandom(seed);
        String[] a = adjectives;
        String[] n = nouns;
        String adj = a[rnd.nextInt(Math.max(1, a.length))];
        String noun = n[rnd.nextInt(Math.max(1, n.length))];
        return adj + " " + noun;
    }

    /** Restores built-in word lists (e.g. missing or invalid datapack JSON). */
    public static void applyReloadBuiltin() {
        adjectives = Arrays.copyOf(BUILTIN_ADJECTIVES, BUILTIN_ADJECTIVES.length);
        nouns = Arrays.copyOf(BUILTIN_NOUNS, BUILTIN_NOUNS.length);
    }

    /** Applies lists from {@code names.json}. */
    public static void applyReload(List<String> newAdjectives, List<String> newNouns) {
        if (newAdjectives.isEmpty() || newNouns.isEmpty()) {
            applyReloadBuiltin();
            return;
        }
        adjectives = List.copyOf(newAdjectives).toArray(String[]::new);
        nouns = List.copyOf(newNouns).toArray(String[]::new);
    }
}
