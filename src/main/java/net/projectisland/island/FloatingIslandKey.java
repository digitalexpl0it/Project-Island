package net.projectisland.island;

import java.util.Optional;

/**
 * Stable identity for one procedural island mass: the coarse grid cell that owns the island RNG.
 * Matches {@link net.projectisland.worldgen.FloatingIslandLayout#REGION_CHUNKS}.
 */
public record FloatingIslandKey(int regionX, int regionZ) {
    private static final char SEP = ';';

    public String toStorageKey() {
        return regionX + String.valueOf(SEP) + regionZ;
    }

    public static Optional<FloatingIslandKey> parseStorageKey(String key) {
        int i = key.indexOf(SEP);
        if (i <= 0 || i >= key.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(new FloatingIslandKey(Integer.parseInt(key.substring(0, i)), Integer.parseInt(key.substring(i + 1))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
