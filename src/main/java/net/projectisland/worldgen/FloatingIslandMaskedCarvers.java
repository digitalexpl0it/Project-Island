package net.projectisland.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;

/**
 * Runs vanilla overworld configured carvers while constraining results to procedural island columns (see {@link
 * FloatingIslandLayout#columnContains}).
 */
public final class FloatingIslandMaskedCarvers {
    private FloatingIslandMaskedCarvers() {}

    public static void applyMaskedOverworldCarvers(
            FloatingIslandsChunkGenerator generator,
            WorldGenRegion level,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving step,
            NoiseBasedChunkGenerator noiseDelegate,
            Holder<NoiseGeneratorSettings> noiseSettings) {
        if (!Config.FLOATING_ISLANDS_ENABLE_MASKED_OVERWORLD_CARVERS.getAsBoolean()) {
            return;
        }
        if (!(chunk instanceof ProtoChunk protoChunk)) {
            return;
        }

        BiomeManager biomemanager = biomeManager.withDifferentSource(
                (x, y, z) -> generator.getBiomeSource().getNoiseBiome(x, y, z, randomState.sampler()));

        WorldgenRandom worldgenrandom =
                new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));

        ChunkPos chunkpos = chunk.getPos();
        int r = Mth.clamp(Config.FLOATING_ISLANDS_MASKED_CARVER_NEIGHBOR_CHUNK_RADIUS.getAsInt(), 2, 8);

        NoiseChunk noisechunk = chunk.getOrCreateNoiseChunk(
                ca -> NoiseChunk.forChunk(
                        ca,
                        randomState,
                        Beardifier.forStructuresInChunk(structureManager, chunk.getPos()),
                        noiseSettings.value(),
                        carvingFluidPicker(noiseSettings.value()),
                        Blender.of(level)));

        CarvingContext carvingcontext = new CarvingContext(
                noiseDelegate,
                level.registryAccess(),
                chunk.getHeightAccessorForGeneration(),
                noisechunk,
                randomState,
                noiseSettings.value().surfaceRule());

        CarvingMask carvingmask = protoChunk.getOrCreateCarvingMask(step);

        for (int j = -r; j <= r; j++) {
            for (int k = -r; k <= r; k++) {
                ChunkPos chunkpos1 = new ChunkPos(chunkpos.x + j, chunkpos.z + k);
                ChunkAccess chunkaccess = level.getChunk(chunkpos1.x, chunkpos1.z);
                BiomeGenerationSettings biomegenerationsettings = chunkaccess.carverBiome(
                        () -> generator.getBiomeGenerationSettings(
                                generator
                                        .getBiomeSource()
                                        .getNoiseBiome(
                                                QuartPos.fromBlock(chunkpos1.getMinBlockX()),
                                                0,
                                                QuartPos.fromBlock(chunkpos1.getMinBlockZ()),
                                                randomState.sampler())));

                Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomegenerationsettings.getCarvers(step);
                int idx = 0;
                for (Holder<ConfiguredWorldCarver<?>> holder : iterable) {
                    ConfiguredWorldCarver<?> configuredworldcarver = holder.value();
                    worldgenrandom.setLargeFeatureSeed(seed + (long) idx, chunkpos1.x, chunkpos1.z);
                    if (configuredworldcarver.isStartChunk(worldgenrandom)) {
                        configuredworldcarver.carve(
                                carvingcontext,
                                chunk,
                                biomemanager::getBiome,
                                worldgenrandom,
                                noisechunk.aquifer(),
                                chunkpos1,
                                carvingmask);
                    }
                    idx++;
                }
            }
        }

        stripCarvingOutsideIslandColumns(chunk);

        if (Config.DEBUG_LOGGING.getAsBoolean()
                && Config.FLOATING_ISLANDS_MASKED_CARVERS_DEBUG_LOGGING.getAsBoolean()) {
            ProjectIsland.LOGGER.debug(
                    "Masked overworld carvers applied for chunk {} step {}", chunkpos, step);
        }
    }

    private static Aquifer.FluidPicker carvingFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus lavaStatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int sea = settings.seaLevel();
        Aquifer.FluidStatus waterStatus = new Aquifer.FluidStatus(sea, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, sea) ? lavaStatus : waterStatus;
    }

    /** Clears cave air / fluids outside island columns; cheap slab/void fast paths avoid per-Y {@code columnContains}. */
    private static void stripCarvingOutsideIslandColumns(ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        ChunkPos cp = chunk.getPos();
        int minWX = cp.getMinBlockX();
        int minWZ = cp.getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = minWX + lx;
                int wz = minWZ + lz;
                int columnTop = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
                int columnBottom = FloatingIslandLayout.columnBottomY(wx, wz, minY, maxY);
                boolean voidColumn = columnTop <= minY;

                for (int y = minY; y < maxY; y++) {
                    pos.set(lx, y, lz);
                    BlockState s = chunk.getBlockState(pos);
                    if (!isCarvingStripCandidate(s)) {
                        continue;
                    }
                    if (voidColumn || y < columnBottom || y > columnTop) {
                        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                        continue;
                    }
                    if (!FloatingIslandLayout.columnContains(wx, wz, y, minY, maxY)) {
                        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    private static boolean isCarvingStripCandidate(BlockState s) {
        if (s.is(Blocks.CAVE_AIR) || s.is(Blocks.WATER) || s.is(Blocks.LAVA)) {
            return true;
        }
        return !s.getFluidState().isEmpty();
    }

    public static Holder<NoiseGeneratorSettings> resolveOverworldNoiseSettings(
            net.minecraft.core.RegistryAccess registryAccess) {
        return registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
    }
}
