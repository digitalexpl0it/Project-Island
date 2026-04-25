package net.projectisland.island;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * Shared rules for “is this feet position supported?” so void rescue, respawn safety, and future systems stay aligned.
 */
public final class FloatingIslandSurfaceSupport {
    /**
     * How far above procedural {@link FloatingIslandsChunkGenerator#islandSurfaceBlockY} we still treat the player as
     * supported (vanilla structures / dungeon roofs / trees above the ellipsoid “skin”).
     */
    public static final int MAX_BLOCKS_ABOVE_PROCEDURAL_TOP = 128;

    private FloatingIslandSurfaceSupport() {}

    /**
     * True if this column’s feet height is on procedural island surface (with tall allowance above) or on solid
     * collision within a few blocks below the feet (structures in void columns, trimmed features, etc.).
     */
    public static boolean columnSupportsFeet(
            ServerLevel level, ChunkGenerator generator, int wx, int wz, double feetY, int minY, int maxY) {
        int top = FloatingIslandsChunkGenerator.islandSurfaceBlockY(generator, wx, wz, minY, maxY);
        if (top != Integer.MIN_VALUE) {
            if (feetY >= top - 0.5d && feetY <= top + (double) MAX_BLOCKS_ABOVE_PROCEDURAL_TOP) {
                return true;
            }
        }
        return solidFootingNearColumn(level, wx, wz, feetY, minY);
    }

    /**
     * True if any sample column under the horizontal footprint (with margin) supports the entity feet height.
     */
    public static boolean bboxSupported(
            ServerLevel level, ChunkGenerator generator, AABB bb, double feetY, int minY, int maxY) {
        int x0 = Mth.floor(bb.minX) - 1;
        int x1 = Mth.floor(bb.maxX - 1.0E-7) + 1;
        int z0 = Mth.floor(bb.minZ) - 1;
        int z1 = Mth.floor(bb.maxZ - 1.0E-7) + 1;
        for (int wx = x0; wx <= x1; wx++) {
            for (int wz = z0; wz <= z1; wz++) {
                if (columnSupportsFeet(level, generator, wx, wz, feetY, minY, maxY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean solidFootingNearColumn(ServerLevel level, int wx, int wz, double feetY, int minY) {
        int fy = Mth.floor(feetY);
        if (fy <= minY + 8) {
            return false;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= 6; dy++) {
            m.set(wx, fy - dy, wz);
            BlockState st = level.getBlockState(m);
            var shape = st.getCollisionShape(level, m);
            if (shape.isEmpty()) {
                continue;
            }
            double blockTop = m.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            if (feetY >= blockTop - 0.65d && feetY <= blockTop + 1.55d) {
                return true;
            }
        }
        return false;
    }

    /** Stand-up position for overworld bed / respawn anchor, if valid and in this level. */
    public static Optional<Vec3> findRespawnStandUp(ServerLevel level, ServerPlayer player) {
        if (!player.getRespawnDimension().equals(level.dimension())) {
            return Optional.empty();
        }
        BlockPos pos = player.getRespawnPosition();
        if (pos == null) {
            return Optional.empty();
        }
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof BedBlock && BedBlock.canSetSpawn(level)) {
            return BedBlock.findStandUpPosition(
                    EntityType.PLAYER, level, pos, st.getValue(BedBlock.FACING), player.getRespawnAngle());
        }
        if (st.getBlock() instanceof RespawnAnchorBlock && RespawnAnchorBlock.canSetSpawn(level)) {
            return RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, pos);
        }
        return Optional.empty();
    }
}
