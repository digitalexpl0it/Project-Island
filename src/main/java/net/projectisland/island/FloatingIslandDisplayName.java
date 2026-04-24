package net.projectisland.island;

import java.util.SplittableRandom;

/**
 * Deterministic fantasy-style labels for procedural islands, keyed only by region coordinates
 * (same island always gets the same name on server and client).
 */
public final class FloatingIslandDisplayName {
    private static final int NAME_SEED_SALT = 0x4E174001;

    private static final String[] ADJECTIVES = {
        "Misty", "Coral", "Jade", "Amber", "Azure", "Dusky", "Ivory", "Onyx", "Opal", "Rust",
        "Sable", "Solar", "Verdant", "Cobalt", "Crimson", "Gilded", "Marble", "Obsidian", "Pearl", "Quartz",
        "Saffron", "Silver", "Velvet", "Winding", "Brine", "Drift", "High", "Low", "Far", "Near"
    };

    private static final String[] NOUNS = {
        "Atoll", "Barrow", "Bluff", "Cay", "Crest", "Crag", "Delta", "Drift", "Firth", "Haven",
        "Isle", "Knoll", "Lagoon", "Ledge", "Mesa", "Moor", "Nook", "Reach", "Reef", "Ridge",
        "Shoal", "Skerry", "Spire", "Stack", "Strand", "Table", "Tor", "Vale", "Veer", "Ward"
    };

    private FloatingIslandDisplayName() {}

    public static String forRegion(int regionX, int regionZ) {
        long seed = (long) regionX * 3129871L ^ (long) regionZ * 116129811L ^ (long) NAME_SEED_SALT;
        SplittableRandom rnd = new SplittableRandom(seed);
        String a = ADJECTIVES[rnd.nextInt(ADJECTIVES.length)];
        String n = NOUNS[rnd.nextInt(NOUNS.length)];
        return a + " " + n;
    }
}
