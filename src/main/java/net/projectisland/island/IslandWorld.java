package net.projectisland.island;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.worldgen.FloatingIslandLayout;

/**
 * Server-side access to {@link FloatingIslandSavedData} and column → {@link FloatingIslandKey} resolution.
 */
public final class IslandWorld {
    private IslandWorld() {}

    public static FloatingIslandSavedData get(ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            throw new IllegalStateException("Floating island saved data is only defined for overworld floating-island levels.");
        }
        return level.getDataStorage().computeIfAbsent(FloatingIslandSavedData.FACTORY, FloatingIslandSavedData.FILE_ID);
    }

    public static Optional<FloatingIslandKey> keyAt(ServerLevel level, BlockPos pos) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return Optional.empty();
        }
        return FloatingIslandLayout.islandOwningSurface(
                pos.getX(), pos.getZ(), level.getMinBuildHeight(), level.getMaxBuildHeight());
    }

    public static Optional<IslandRecord> recordAt(ServerLevel level, BlockPos pos) {
        return keyAt(level, pos).map(k -> get(level).getOrCreate(k));
    }

    /**
     * Records waystone use for every procedural region identity that can disagree on merged islands: surface winner at
     * the waystone, at the player's feet, and at this region's procedural horizontal center (where HUD/Xaero pins sit).
     * Without the extra keys, client coords for the pin can fall in a neighbor grid cell while only the waystone column
     * was marked → gold/temporary flags break and pins look “lost” when you fly away.
     */
    /** @return {@code true} if any procedural region key was newly recorded for {@code playerId} */
    public static boolean markWaystoneHitsForHudSync(ServerLevel level, UUID playerId, BlockPos waystonePos, BlockPos playerFeet) {
        HashSet<FloatingIslandKey> keys = new HashSet<>();
        keyAt(level, waystonePos).ifPresent(keys::add);
        keyAt(level, playerFeet).ifPresent(keys::add);
        addSurfaceWinnerAtProceduralCenter(level, waystonePos.getY(), keys, keyAt(level, waystonePos));
        addSurfaceWinnerAtProceduralCenter(level, playerFeet.getY(), keys, keyAt(level, playerFeet));
        FloatingIslandSavedData data = get(level);
        boolean anyNew = false;
        for (FloatingIslandKey k : keys) {
            if (data.markPlayerUsedWaystoneOnIsland(playerId, k)) {
                anyNew = true;
            }
        }
        return anyNew;
    }

    private static void addSurfaceWinnerAtProceduralCenter(
            ServerLevel level, int sampleY, HashSet<FloatingIslandKey> keys, Optional<FloatingIslandKey> region) {
        region.ifPresent(key -> {
            FloatingIslandLayout.IslandParams params = new FloatingIslandLayout.IslandParams();
            FloatingIslandLayout.regionIsland(key.regionX(), key.regionZ(), params);
            int cx = Mth.floor(params.centerX);
            int cz = Mth.floor(params.centerZ);
            keyAt(level, new BlockPos(cx, sampleY, cz)).ifPresent(keys::add);
        });
    }
}
