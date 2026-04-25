package net.projectisland.island;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Ensures chunks exist before teleport so island columns are generated and decorated (avoids standing on empty math
 * until neighbors load).
 */
final class IslandChunkLoader {
    private static final int DEFAULT_RADIUS_CHUNKS = 1;

    private IslandChunkLoader() {}

    static void ensureChunksAroundWorldBlock(ServerLevel level, int blockX, int blockZ) {
        ensureChunksAroundWorldBlock(level, blockX, blockZ, DEFAULT_RADIUS_CHUNKS);
    }

    static void ensureChunksAroundWorldBlock(ServerLevel level, int blockX, int blockZ, int halfSizeChunks) {
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        int r = Math.max(0, halfSizeChunks);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, true);
            }
        }
    }
}
