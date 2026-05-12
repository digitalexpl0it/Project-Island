package net.projectisland.island;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared rules for “is this feet position supported?” so void rescue, respawn safety, and future systems stay aligned.
 */
public final class FloatingIslandSurfaceSupport {
    /** How far below feet we scan for real collision (tall trees / builds above the analytic island shell). */
    public static final int SOLID_FOOTING_SCAN_MAX_DY = 40;

    private FloatingIslandSurfaceSupport() {}

    /**
     * True when this column has **world** collision the entity can stand on within {@value #SOLID_FOOTING_SCAN_MAX_DY}
     * blocks below the feet. We no longer return true from **procedural math alone** — that matched open air beside an
     * island (same analytic “island column” as a neighbor’s shell) and made {@link #bboxSupported} pass in the void,
     * void rescue “succeeded”, then gravity dropped the player back into the floor band every tick.
     */
    public static boolean columnSupportsFeet(ServerLevel level, int wx, int wz, double feetY, int minY, int maxY) {
        return solidFootingNearColumn(level, wx, wz, feetY, minY, SOLID_FOOTING_SCAN_MAX_DY);
    }

    /**
     * True if any sample column under the horizontal footprint (with margin) supports the entity feet height.
     */
    public static boolean bboxSupported(ServerLevel level, AABB bb, double feetY, int minY, int maxY) {
        int x0 = Mth.floor(bb.minX) - 1;
        int x1 = Mth.floor(bb.maxX - 1.0E-7) + 1;
        int z0 = Mth.floor(bb.minZ) - 1;
        int z1 = Mth.floor(bb.maxZ - 1.0E-7) + 1;
        for (int wx = x0; wx <= x1; wx++) {
            for (int wz = z0; wz <= z1; wz++) {
                if (columnSupportsFeet(level, wx, wz, feetY, minY, maxY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean solidFootingNearColumn(ServerLevel level, int wx, int wz, double feetY, int minY, int maxDy) {
        int fy = Mth.floor(feetY);
        if (fy <= minY + 8) {
            return false;
        }
        int cap = Math.max(1, Math.min(maxDy, 64));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= cap; dy++) {
            m.set(wx, fy - dy, wz);
            BlockState st = level.getBlockState(m);
            var shape = st.getCollisionShape(level, m);
            if (shape.isEmpty()) {
                continue;
            }
            double blockTop = m.getY() + shape.max(Direction.Axis.Y);
            if (feetY >= blockTop - 0.65d && feetY <= blockTop + 1.55d) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stand-up position for overworld bed / respawn anchor, if valid and in this level. Resolves the **foot** of a bed
     * when {@linkplain net.minecraft.server.level.ServerPlayer#getRespawnPosition() respawn} points at the head
     * half (otherwise vanilla stand-up can be empty). If {@link BedBlock#findStandUpPosition} is obstructed, falls
     * back to a clear column one block above the anchor when possible.
     */
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
            BlockPos footPos = pos;
            BlockState footState = st;
            if (st.hasProperty(BedBlock.PART) && st.getValue(BedBlock.PART) == BedPart.HEAD) {
                footPos = pos.relative(st.getValue(BedBlock.FACING).getOpposite());
                footState = level.getBlockState(footPos);
            }
            if (!(footState.getBlock() instanceof BedBlock) || !BedBlock.canSetSpawn(level)) {
                return Optional.empty();
            }
            Direction facing = footState.getValue(BedBlock.FACING);
            Optional<Vec3> stand =
                    BedBlock.findStandUpPosition(EntityType.PLAYER, level, footPos, facing, player.getRespawnAngle());
            if (stand.isPresent()) {
                return stand;
            }
            return fallbackFeetOneBlockAboveAnchor(level, footPos);
        }
        if (st.getBlock() instanceof RespawnAnchorBlock && RespawnAnchorBlock.canSetSpawn(level)) {
            Optional<Vec3> stand = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, pos);
            if (stand.isPresent()) {
                return stand;
            }
            return fallbackFeetOneBlockAboveAnchor(level, pos);
        }
        return Optional.empty();
    }

    /** Feet on the block above the bed/anchor when two air blocks exist (obstructed vanilla stand-up). */
    private static Optional<Vec3> fallbackFeetOneBlockAboveAnchor(ServerLevel level, BlockPos anchorBlock) {
        BlockPos feet = anchorBlock.above();
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
            return Optional.empty();
        }
        return Optional.of(new Vec3(feet.getX() + 0.5d, feet.getY(), feet.getZ() + 0.5d));
    }
}
