package net.projectisland.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;
import net.projectisland.Config;

/**
 * Removes water/lava that touches open void **sideways or downward** relative to {@link FloatingIslandLayout#columnContains}.
 * {@link Direction#UP} is ignored (sky is outside the analytic envelope). Fluids within {@link
 * net.projectisland.Config#FLOATING_ISLANDS_STRIP_EXTERIOR_FLUIDS_TOP_DEPTH_EXEMPT_BLOCKS} of {@link
 * FloatingIslandLayout#columnTopY} skip leak stripping so narrow islands keep lakes and sprinkled pools.
 * **Water** in that band is also kept when the block lies **outside** {@link FloatingIslandLayout#columnContains} (rim /
 * biome decoration past the analytic shell); **lava** outside the envelope is still cleared.
 */
public final class FloatingIslandExteriorFluidStrip {
    private FloatingIslandExteriorFluidStrip() {}

    public static void applyAfterDecoration(WorldGenLevel level, ChunkAccess chunk) {
        if (!Config.FLOATING_ISLANDS_STRIP_EXTERIOR_FLUIDS_AFTER_DECORATION.getAsBoolean()) {
            return;
        }
        int maxPasses = Config.FLOATING_ISLANDS_STRIP_EXTERIOR_FLUIDS_MAX_PASSES.getAsInt();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        ChunkPos cpos = chunk.getPos();
        int minWX = cpos.getMinBlockX();
        int minWZ = cpos.getMinBlockZ();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean changed = false;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int wx = minWX + lx;
                    int wz = minWZ + lz;
                    for (int y = minY; y < maxY; y++) {
                        pos.set(lx, y, lz);
                        BlockState state = chunk.getBlockState(pos);
                        if (!isWaterOrLava(state)) {
                            continue;
                        }
                        if (stripIfShellLeak(level, chunk, cpos, minY, maxY, minWX, minWZ, wx, y, wz, lx, lz, pos, state)) {
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    private static boolean isWaterOrLava(BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) {
            return false;
        }
        return fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA);
    }

    private static boolean isWaterFluid(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && fluid.is(FluidTags.WATER);
    }

    /**
     * True for neighbors outside the procedural envelope that fluid could drain into (air / cave air / fluid), not solid
     * filler outside the ellipsoid (e.g. structure walls).
     */
    private static boolean neighborAllowsVoidLeak(BlockState neighbor) {
        if (neighbor.isAir() || neighbor.is(Blocks.CAVE_AIR)) {
            return true;
        }
        FluidState fs = neighbor.getFluidState();
        return !fs.isEmpty() && (fs.is(FluidTags.WATER) || fs.is(FluidTags.LAVA));
    }

    private static BlockState stateAtWorld(
            WorldGenLevel level, ChunkAccess chunk, ChunkPos cpos, int minWX, int minWZ, int nx, int ny, int nz) {
        if (nx >= minWX && nx <= minWX + 15 && nz >= minWZ && nz <= minWZ + 15) {
            return chunk.getBlockState(new BlockPos(nx - minWX, ny, nz - minWZ));
        }
        return level.getBlockState(new BlockPos(nx, ny, nz));
    }

    private static boolean stripIfShellLeak(
            WorldGenLevel level,
            ChunkAccess chunk,
            ChunkPos cpos,
            int minY,
            int maxY,
            int minWX,
            int minWZ,
            int wx,
            int y,
            int wz,
            int lx,
            int lz,
            BlockPos.MutableBlockPos pos,
            BlockState fluidState) {
        int exemptBelowTop = Config.FLOATING_ISLANDS_STRIP_EXTERIOR_FLUIDS_TOP_DEPTH_EXEMPT_BLOCKS.getAsInt();
        if (!FloatingIslandLayout.columnContains(wx, y, wz, minY, maxY)) {
            if (exemptBelowTop > 0
                    && isWaterFluid(fluidState)
                    && inTopDepthExemptBand(wx, y, wz, minY, maxY, exemptBelowTop)) {
                return false;
            }
            pos.set(lx, y, lz);
            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
            return true;
        }
        if (exemptBelowTop > 0 && inTopDepthExemptBand(wx, y, wz, minY, maxY, exemptBelowTop)) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) {
                continue;
            }
            int nx = wx + dir.getStepX();
            int ny = y + dir.getStepY();
            int nz = wz + dir.getStepZ();
            if (ny < minY || ny >= maxY) {
                pos.set(lx, y, lz);
                chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                return true;
            }
            if (!FloatingIslandLayout.columnContains(nx, ny, nz, minY, maxY)) {
                BlockState nstate = stateAtWorld(level, chunk, cpos, minWX, minWZ, nx, ny, nz);
                if (neighborAllowsVoidLeak(nstate)) {
                    pos.set(lx, y, lz);
                    chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                    return true;
                }
            }
        }
        return false;
    }

    /** True when Y is within N blocks of the procedural dome top for this column (surface band). */
    private static boolean inTopDepthExemptBand(int wx, int y, int wz, int minY, int maxY, int blocksBelowTop) {
        int topY = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
        if (topY <= minY) {
            return false;
        }
        return y >= topY - blocksBelowTop;
    }
}
