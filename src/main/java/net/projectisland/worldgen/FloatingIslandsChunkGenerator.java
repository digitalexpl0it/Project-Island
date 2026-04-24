package net.projectisland.worldgen;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;

/**
 * Void sky islands: asymmetric vertical profile (flat-ish dome top, deeper underside).
 * Horizontal scale uses smooth analytic wobble (no block-sized discontinuities).
 * Vanilla structures still generate, then {@link #trimFloatingStructureBlocks} removes
 * pieces in void columns or floating above the island surface so mineshafts / ruined portals
 * tend to hug terrain where they overlap land.
 */
public final class FloatingIslandsChunkGenerator extends ChunkGenerator {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "floating_islands");

    public static final MapCodec<FloatingIslandsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(instance, FloatingIslandsChunkGenerator::new));

    public FloatingIslandsChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /**
     * Top solid block Y at ({@code wx}, {@code wz}), or {@link Integer#MIN_VALUE} if the column is void.
     * Used for spawn relocation when the dimension's default coordinates miss an island.
     */
    public static int islandSurfaceBlockY(ChunkGenerator generator, int wx, int wz, int minY, int maxY) {
        if (!(generator instanceof FloatingIslandsChunkGenerator)) {
            return Integer.MIN_VALUE;
        }
        int top = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
        return top > minY ? top : Integer.MIN_VALUE;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** How far above the noise island top we allow structure blocks to remain (surface features). */
    private static final int STRUCTURE_CLEARANCE_ABOVE_TOP = 2;

    /**
     * Small vanilla structures that often read as “dungeons” in the void; frequency is thinned via
     * {@link Config#FLOATING_ISLANDS_RARE_STRUCTURE_KEEP_CHANCE} after {@link #trimFloatingStructureBlocks}.
     */
    private static final Set<ResourceLocation> RARE_AMBUSH_STRUCTURE_IDS = Set.of(
            ResourceLocation.withDefaultNamespace("monster_room"),
            ResourceLocation.withDefaultNamespace("trial_chambers"));

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager) {
        super.createStructures(registryAccess, structureState, structureManager, chunk, structureTemplateManager);
        trimFloatingStructureBlocks(chunk);
        thinRareAmbushStructures(registryAccess, chunk, structureState);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.WORLD_SURFACE_WG));
    }

    /**
     * Randomly removes monster rooms and trial chambers so fewer isolated ruins remain (see common config).
     * Multi-chunk structures may be partially cleared until neighboring chunks generate.
     */
    private static void thinRareAmbushStructures(
            RegistryAccess registryAccess, ChunkAccess chunk, ChunkGeneratorStructureState structureState) {
        double keepChance = Config.FLOATING_ISLANDS_RARE_STRUCTURE_KEEP_CHANCE.getAsDouble();
        if (keepChance >= 1.0d) {
            return;
        }
        var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        long levelSeed = structureState.getLevelSeed();
        ChunkPos cpos = chunk.getPos();
        for (var entry : new ArrayList<>(chunk.getAllStarts().entrySet())) {
            Structure structure = entry.getKey();
            ResourceLocation id = structureRegistry.getKey(structure);
            if (id == null || !RARE_AMBUSH_STRUCTURE_IDS.contains(id)) {
                continue;
            }
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            RandomSource roll = RandomSource.create(
                    cpos.toLong() ^ levelSeed ^ id.hashCode());
            if (roll.nextDouble() < keepChance) {
                continue;
            }
            wipeStructureBlocksInChunk(chunk, start.getBoundingBox());
            chunk.setStartForStructure(structure, StructureStart.INVALID_START);
        }
    }

    private static void wipeStructureBlocksInChunk(ChunkAccess chunk, BoundingBox bb) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        ChunkPos cp = chunk.getPos();
        int minWX = cp.getMinBlockX();
        int maxWX = cp.getMaxBlockX();
        int minWZ = cp.getMinBlockZ();
        int maxWZ = cp.getMaxBlockZ();
        int x0 = Math.max(bb.minX(), minWX);
        int x1 = Math.min(bb.maxX(), maxWX);
        int z0 = Math.max(bb.minZ(), minWZ);
        int z1 = Math.min(bb.maxZ(), maxWZ);
        int y0 = Math.max(bb.minY(), minY);
        int y1 = Math.min(bb.maxY(), maxY - 1);
        for (int wx = x0; wx <= x1; wx++) {
            int lx = wx - minWX;
            for (int wz = z0; wz <= z1; wz++) {
                int lz = wz - minWZ;
                for (int y = y0; y <= y1; y++) {
                    BlockPos pos = new BlockPos(lx, y, lz);
                    if (!chunk.getBlockState(pos).isAir()) {
                        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        sprinkleExtraSurfaceTrees(level, chunk);
    }

    private void sprinkleExtraSurfaceTrees(WorldGenLevel level, ChunkAccess chunk) {
        int n = Config.FLOATING_ISLANDS_EXTRA_SURFACE_TREES_PER_CHUNK.getAsInt();
        if (n <= 0) {
            return;
        }
        ChunkPos cpos = chunk.getPos();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        var configured = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
        List<Holder<ConfiguredFeature<?, ?>>> variants = List.of(
                configured.getOrThrow(TreeFeatures.OAK),
                configured.getOrThrow(TreeFeatures.FANCY_OAK),
                configured.getOrThrow(TreeFeatures.BIRCH));
        RandomSource rnd = RandomSource.create(cpos.toLong() ^ level.getSeed());
        for (int i = 0; i < n; i++) {
            int wx = cpos.getMinBlockX() + rnd.nextInt(16);
            int wz = cpos.getMinBlockZ() + rnd.nextInt(16);
            int topY = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
            if (topY <= minY) {
                continue;
            }
            int lx = wx - cpos.getMinBlockX();
            int lz = wz - cpos.getMinBlockZ();
            BlockPos surfacePos = new BlockPos(lx, topY, lz);
            if (!chunk.getBlockState(surfacePos).is(Blocks.GRASS_BLOCK)) {
                continue;
            }
            BlockPos placeAt = new BlockPos(wx, topY + 1, wz);
            Holder<ConfiguredFeature<?, ?>> tree = variants.get(rnd.nextInt(variants.size()));
            tree.value().place(level, this, rnd, placeAt);
        }
    }

    /**
     * Removes structure blocks that sit in columns with no island, or hang in open air above
     * the island top. Overlap with solid terrain is kept so mineshafts / portals can embed.
     */
    private static void trimFloatingStructureBlocks(ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        ChunkPos cp = chunk.getPos();
        int minWX = cp.getMinBlockX();
        int maxWX = cp.getMaxBlockX();
        int minWZ = cp.getMinBlockZ();
        int maxWZ = cp.getMaxBlockZ();

        for (StructureStart start : chunk.getAllStarts().values()) {
            if (!start.isValid()) {
                continue;
            }
            BoundingBox bb = start.getBoundingBox();
            int x0 = Math.max(bb.minX(), minWX);
            int x1 = Math.min(bb.maxX(), maxWX);
            int z0 = Math.max(bb.minZ(), minWZ);
            int z1 = Math.min(bb.maxZ(), maxWZ);
            int y0 = Math.max(bb.minY(), minY);
            int y1 = Math.min(bb.maxY(), maxY - 1);

            for (int wx = x0; wx <= x1; wx++) {
                int lx = wx - minWX;
                for (int wz = z0; wz <= z1; wz++) {
                    int lz = wz - minWZ;
                    int top = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
                    boolean hasIslandColumn = top > minY;

                    for (int y = y0; y <= y1; y++) {
                        BlockPos pos = new BlockPos(lx, y, lz);
                        BlockState state = chunk.getBlockState(pos);
                        if (state.isAir()) {
                            continue;
                        }
                        if (!hasIslandColumn) {
                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                        } else if (y > top + STRUCTURE_CLEARANCE_ABOVE_TOP) {
                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                        }
                    }
                }
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = pos.getMinBlockX() + lx;
                int wz = pos.getMinBlockZ() + lz;

                int topY = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
                if (topY <= minY) {
                    continue;
                }

                int bottomY = FloatingIslandLayout.columnBottomY(wx, wz, minY, maxY);
                if (bottomY >= maxY) {
                    continue;
                }

                Holder<Biome> biome = chunk.getNoiseBiome(
                        QuartPos.fromBlock(wx),
                        QuartPos.fromBlock(Mth.clamp(topY - 1, minY, maxY - 1)),
                        QuartPos.fromBlock(wz));
                BlockState surface = surfaceState(biome);

                for (int y = bottomY; y <= topY; y++) {
                    if (y < minY || y >= maxY) {
                        continue;
                    }
                    if (!FloatingIslandLayout.columnContains(wx, wz, y, minY, maxY)) {
                        continue;
                    }

                    BlockState state;
                    if (y == topY) {
                        state = surface;
                    } else if (y >= topY - 3) {
                        state = underSurfaceState(surface);
                    } else {
                        state = Blocks.STONE.defaultBlockState();
                    }

                    chunk.setBlockState(new BlockPos(lx, y, lz), state, false);
                }
            }
        }

        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(
            WorldGenRegion level,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -63;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int topY = FloatingIslandLayout.columnTopY(x, z, minY, maxY);
        if (topY <= minY) {
            return minY;
        }
        return topY + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        int minY = height.getMinBuildHeight();
        int maxY = height.getMaxBuildHeight();
        int heightCount = maxY - minY;
        BlockState[] states = new BlockState[heightCount];
        for (int i = 0; i < heightCount; i++) {
            states[i] = Blocks.AIR.defaultBlockState();
        }

        int topY = FloatingIslandLayout.columnTopY(x, z, minY, maxY);
        if (topY <= minY) {
            return new NoiseColumn(minY, states);
        }

        int bottomY = FloatingIslandLayout.columnBottomY(x, z, minY, maxY);
        Holder<Biome> biome = getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x),
                QuartPos.fromBlock(Mth.clamp(topY - 1, minY, maxY - 1)),
                QuartPos.fromBlock(z),
                random.sampler());
        BlockState surface = surfaceState(biome);

        for (int y = bottomY; y <= topY; y++) {
            if (y < minY || y >= maxY) {
                continue;
            }
            if (!FloatingIslandLayout.columnContains(x, z, y, minY, maxY)) {
                continue;
            }

            int idx = y - minY;
            if (y == topY) {
                states[idx] = surface;
            } else if (y >= topY - 3) {
                states[idx] = underSurfaceState(surface);
            } else {
                states[idx] = Blocks.STONE.defaultBlockState();
            }
        }

        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("ChunkGenerator: projectisland:floating_islands");
    }

    private static BlockState surfaceState(Holder<Biome> biome) {
        float temperature = biome.value().getBaseTemperature();
        if (temperature < 0.25f) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        if (temperature > 0.85f) {
            return Blocks.SAND.defaultBlockState();
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    private static BlockState underSurfaceState(BlockState surface) {
        if (surface.is(Blocks.SNOW_BLOCK)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (surface.is(Blocks.SAND)) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        return Blocks.DIRT.defaultBlockState();
    }
}
