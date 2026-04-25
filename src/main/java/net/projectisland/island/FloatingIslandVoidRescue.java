package net.projectisland.island;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.Vec3;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * Void rescue for the floating-islands overworld: {@linkplain #tickVoidRescue(ServerPlayer, ServerLevel) per-tick}
 * path fires **once per fall** when the player reaches **near min build height** while not supported on island surface.
 * {@linkplain #relocatePlayerFromVoid(ServerPlayer, ServerLevel)} is also used on join / dimension change (immediate).
 */
public final class FloatingIslandVoidRescue {
    private FloatingIslandVoidRescue() {}

    /** Marked while the player is in open void (not supported); cleared after a floor rescue or when back on surface. */
    private static final String TAG_VOID_FALLING = ProjectIsland.MOD_ID + "_void_falling";

    private static final int MAX_CHUNK_RADIUS = 80;
    private static final int[] LOCAL_X = {8, 2, 14, 8, 8};
    private static final int[] LOCAL_Z = {8, 8, 8, 2, 14};

    /**
     * Once per void fall: when the player is **not** supported on island surface and has dropped to
     * {@code minBuildHeight + voidRescueTriggerBlocksAboveMinY} or below, teleport to starter (if any) then nearest island.
     * Mid-air high above the void floor does nothing (rim-safe; no mid-fall yank).
     */
    public static void tickVoidRescue(ServerPlayer player, ServerLevel level) {
        if (!Config.VOID_RESCUE_EACH_TICK.getAsBoolean()) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (isSupportedOnIslandSurface(player, level)) {
            data.remove(TAG_VOID_FALLING);
            return;
        }
        data.putBoolean(TAG_VOID_FALLING, true);
        int minY = level.getMinBuildHeight();
        int triggerY = minY + Config.VOID_RESCUE_TRIGGER_BLOCKS_ABOVE_MIN_Y.getAsInt();
        if (player.getY() > triggerY) {
            return;
        }
        Optional<FloatingIslandKey> home = IslandWorld.get(level).getStarterHome(player.getUUID());
        if (home.isPresent()) {
            FloatingIslandStarterPlacement.teleportToIslandCenter(player, level, home.get());
            if (isSupportedOnIslandSurface(player, level)) {
                data.remove(TAG_VOID_FALLING);
                return;
            }
        }
        relocatePlayerFromVoid(player, level);
        data.remove(TAG_VOID_FALLING);
    }

    /**
     * True when the player should be pulled out of the void. Uses the entity {@linkplain Player#getBoundingBox() bounding
     * box} on XZ plus a one-block margin: at island rims {@code floor(getX())} often lies in a void column while the
     * player still stands on the edge block.
     */
    public static boolean needsVoidRelocate(ServerPlayer player, ServerLevel level) {
        return !isSupportedOnIslandSurface(player, level);
    }

    /**
     * {@code true} if any column under the player's horizontal footprint (with margin) has procedural island surface
     * within the usual “standing on top” vertical band. Non-floating-islands levels always return {@code true}.
     */
    public static boolean isSupportedOnIslandSurface(ServerPlayer player, ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return true;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        double ey = player.getY();
        var bb = player.getBoundingBox();
        int x0 = Mth.floor(bb.minX) - 1;
        int x1 = Mth.floor(bb.maxX - 1.0E-7) + 1;
        int z0 = Mth.floor(bb.minZ) - 1;
        int z1 = Mth.floor(bb.maxZ - 1.0E-7) + 1;
        for (int wx = x0; wx <= x1; wx++) {
            for (int wz = z0; wz <= z1; wz++) {
                int top = FloatingIslandsChunkGenerator.islandSurfaceBlockY(generator, wx, wz, minY, maxY);
                if (top != Integer.MIN_VALUE && ey >= top - 0.5d && ey <= top + 8.0d) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void relocatePlayerFromVoid(ServerPlayer player, ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        if (isSupportedOnIslandSurface(player, level)) {
            return;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        int originX = Mth.floor(player.getX());
        int originZ = Mth.floor(player.getZ());
        int ox = originX >> 4;
        int oz = originZ >> 4;

        for (int r = 0; r <= MAX_CHUNK_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = ox + dx;
                    int cz = oz + dz;
                    int bx = cx << 4;
                    int bz = cz << 4;
                    for (int s = 0; s < LOCAL_X.length; s++) {
                        int wx = bx + LOCAL_X[s];
                        int wz = bz + LOCAL_Z[s];
                        int top = FloatingIslandsChunkGenerator.islandSurfaceBlockY(generator, wx, wz, minY, maxY);
                        if (top != Integer.MIN_VALUE) {
                            IslandChunkLoader.ensureChunksAroundWorldBlock(level, wx, wz);
                            player.teleportTo(level, wx + 0.5d, top + 1.0d, wz + 0.5d, player.getYRot(), player.getXRot());
                            return;
                        }
                    }
                }
            }
        }

        ProjectIsland.LOGGER.warn(
                "Could not find an island surface within {} chunks of block {}, {} in overworld — player left at void position",
                MAX_CHUNK_RADIUS,
                originX,
                originZ);
    }

    public static Optional<Vec3> findNearestIslandFeet(ServerLevel level, double x, double z) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return Optional.empty();
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int originX = Mth.floor(x);
        int originZ = Mth.floor(z);
        int ox = originX >> 4;
        int oz = originZ >> 4;

        for (int r = 0; r <= MAX_CHUNK_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = ox + dx;
                    int cz = oz + dz;
                    int bx = cx << 4;
                    int bz = cz << 4;
                    for (int s = 0; s < LOCAL_X.length; s++) {
                        int wx = bx + LOCAL_X[s];
                        int wz = bz + LOCAL_Z[s];
                        int top = FloatingIslandsChunkGenerator.islandSurfaceBlockY(generator, wx, wz, minY, maxY);
                        if (top != Integer.MIN_VALUE) {
                            IslandChunkLoader.ensureChunksAroundWorldBlock(level, wx, wz);
                            return Optional.of(new Vec3(wx + 0.5d, top + 1.0d, wz + 0.5d));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
