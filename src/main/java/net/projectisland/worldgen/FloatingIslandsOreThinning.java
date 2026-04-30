package net.projectisland.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.projectisland.Config;

/**
 * Optional post-decoration pass: randomly strips ore blocks so per-material multipliers ({@code 0..1}) thin veins on
 * floating islands. Deterministic from chunk position and level seed.
 */
public final class FloatingIslandsOreThinning {
    private FloatingIslandsOreThinning() {}

    private static final long SALT = 0xB10CFEBEE77EB10CL;

    public static void applyAfterDecoration(WorldGenLevel level, ChunkAccess chunk) {
        if (!anyMultiplierBelowOne()) {
            return;
        }
        ChunkPosBounds b = ChunkPosBounds.of(chunk);
        RandomSource rnd = RandomSource.create(b.chunkSeed(level.getSeed()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = b.minX; x <= b.maxX; x++) {
            for (int z = b.minZ; z <= b.maxZ; z++) {
                for (int y = b.minY; y < b.maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    double mult = multiplierFor(state);
                    if (mult >= 1.0d) {
                        continue;
                    }
                    if (rnd.nextDouble() < mult) {
                        continue;
                    }
                    chunk.setBlockState(pos, stoneLikeReplacement(state.getBlock()), false);
                }
            }
        }
    }

    private static boolean anyMultiplierBelowOne() {
        return Config.FLOATING_ISLANDS_ORE_MULT_COAL.getAsDouble() < 1.0d
                || Config.FLOATING_ISLANDS_ORE_MULT_COPPER.getAsDouble() < 1.0d
                || Config.FLOATING_ISLANDS_ORE_MULT_IRON.getAsDouble() < 1.0d
                || Config.FLOATING_ISLANDS_ORE_MULT_GOLD.getAsDouble() < 1.0d
                || Config.FLOATING_ISLANDS_ORE_MULT_REDSTONE.getAsDouble() < 1.0d
                || Config.FLOATING_ISLANDS_ORE_MULT_LAPIS.getAsDouble() < 1.0d
                || Config.FLOATING_ISLANDS_ORE_MULT_DIAMOND.getAsDouble() < 1.0d
                || Config.FLOATING_ISLANDS_ORE_MULT_EMERALD.getAsDouble() < 1.0d;
    }

    private static double multiplierFor(BlockState state) {
        if (state.is(BlockTags.COAL_ORES)) {
            return Config.FLOATING_ISLANDS_ORE_MULT_COAL.getAsDouble();
        }
        if (state.is(BlockTags.COPPER_ORES)) {
            return Config.FLOATING_ISLANDS_ORE_MULT_COPPER.getAsDouble();
        }
        if (state.is(BlockTags.IRON_ORES)) {
            return Config.FLOATING_ISLANDS_ORE_MULT_IRON.getAsDouble();
        }
        if (state.is(BlockTags.GOLD_ORES)) {
            return Config.FLOATING_ISLANDS_ORE_MULT_GOLD.getAsDouble();
        }
        if (state.is(BlockTags.REDSTONE_ORES)) {
            return Config.FLOATING_ISLANDS_ORE_MULT_REDSTONE.getAsDouble();
        }
        if (state.is(BlockTags.LAPIS_ORES)) {
            return Config.FLOATING_ISLANDS_ORE_MULT_LAPIS.getAsDouble();
        }
        if (state.is(BlockTags.DIAMOND_ORES)) {
            return Config.FLOATING_ISLANDS_ORE_MULT_DIAMOND.getAsDouble();
        }
        if (state.is(BlockTags.EMERALD_ORES)) {
            return Config.FLOATING_ISLANDS_ORE_MULT_EMERALD.getAsDouble();
        }
        return 1.0d;
    }

    private static BlockState stoneLikeReplacement(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String path = id.getPath();
        if (path.startsWith("deepslate_")) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (block == Blocks.NETHER_GOLD_ORE || block == Blocks.NETHER_QUARTZ_ORE) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (block == Blocks.ANCIENT_DEBRIS) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private record ChunkPosBounds(
            int minX, int maxX, int minZ, int maxZ, int minY, int maxY, int chunkX, int chunkZ) {
        static ChunkPosBounds of(ChunkAccess chunk) {
            var cp = chunk.getPos();
            return new ChunkPosBounds(
                    cp.getMinBlockX(),
                    cp.getMaxBlockX(),
                    cp.getMinBlockZ(),
                    cp.getMaxBlockZ(),
                    chunk.getMinBuildHeight(),
                    chunk.getMaxBuildHeight(),
                    cp.x,
                    cp.z);
        }

        long chunkSeed(long levelSeed) {
            return levelSeed ^ SALT ^ chunkX ^ ((long) chunkZ << 32);
        }
    }
}
