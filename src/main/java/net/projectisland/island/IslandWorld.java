package net.projectisland.island;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
}
