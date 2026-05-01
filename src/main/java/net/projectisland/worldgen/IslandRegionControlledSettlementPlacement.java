package net.projectisland.worldgen;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.projectisland.Config;
import net.projectisland.island.FloatingIslandKey;

/**
 * Strips vanilla settlement starts and places one deterministic jigsaw settlement per inhabited island region when
 * {@link Config#FLOATING_ISLANDS_CONTROLLED_SETTLEMENT_PLACEMENT} is enabled. Runs during {@code STRUCTURE_STARTS} so
 * {@link net.minecraft.world.level.chunk.ChunkGenerator#createReferences} wires structure references correctly.
 */
public final class IslandRegionControlledSettlementPlacement {
    /** Must match {@link FloatingIslandsChunkGenerator} settlement roll salt so allow/deny stays aligned. */
    private static final int SALT_REGION_SETTLEMENT_ROLL = 991_871;

    private static final int SALT_CONTROLLED_SETTLEMENT_TYPE = 338_011;
    private static final int SALT_CONTROLLED_ANCHOR = 402_113;
    private static final int SALT_CONTROLLED_PLACE_TRY = 551_903;

    private IslandRegionControlledSettlementPlacement() {}

    private static RandomSource regionRandom(long levelSeed, int rcx, int rcz, int salt) {
        return RandomSource.create(Mth.getSeed(rcx, salt, rcz) ^ levelSeed ^ (levelSeed >>> 32));
    }

    public static void stripVanillaSettlementStarts(RegistryAccess registryAccess, ChunkAccess chunk) {
        var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        for (var entry : new ArrayList<>(chunk.getAllStarts().entrySet())) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            ResourceLocation id = structureRegistry.getKey(structure);
            if (!FloatingIslandsChunkGenerator.isSettlementStructure(id)) {
                continue;
            }
            FloatingIslandsChunkGenerator.wipeStructureBlocksInChunk(chunk, start.getBoundingBox());
            chunk.setStartForStructure(structure, StructureStart.INVALID_START);
        }
    }

    public static void tryPlaceControlledSettlement(
            FloatingIslandsChunkGenerator generator,
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager) {
        long levelSeed = structureState.getLevelSeed();
        RandomState randomState = structureState.randomState();
        ChunkPos cpos = chunk.getPos();
        int rcx = Mth.floorDiv(cpos.x, FloatingIslandLayout.REGION_CHUNKS);
        int rcz = Mth.floorDiv(cpos.z, FloatingIslandLayout.REGION_CHUNKS);
        if (!FloatingIslandLayout.regionHasIsland(rcx, rcz)) {
            return;
        }

        FloatingIslandLayout.IslandParams params = new FloatingIslandLayout.IslandParams();
        FloatingIslandLayout.regionIsland(rcx, rcz, params);
        ChunkPos ownerChunk = new ChunkPos(params.centerX >> 4, params.centerZ >> 4);
        if (!cpos.equals(ownerChunk)) {
            return;
        }

        RandomSource settleRnd = regionRandom(levelSeed, rcx, rcz, SALT_REGION_SETTLEMENT_ROLL);
        if (!IslandRegionStructurePicker.rollSettlementAllowed(settleRnd)) {
            return;
        }

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int jitter = Config.CONTROLLED_SETTLEMENT_ANCHOR_JITTER_BLOCKS.getAsInt();
        int maxTries = Config.CONTROLLED_SETTLEMENT_ANCHOR_TRIES.getAsInt();
        RandomSource anchorRnd = regionRandom(levelSeed, rcx, rcz, SALT_CONTROLLED_ANCHOR);
        BlockPos anchor = null;
        for (int t = 0; t < maxTries; t++) {
            int dx = jitter == 0 ? 0 : anchorRnd.nextInt(jitter * 2 + 1) - jitter;
            int dz = jitter == 0 ? 0 : anchorRnd.nextInt(jitter * 2 + 1) - jitter;
            int ax = params.centerX + dx;
            int az = params.centerZ + dz;
            int top = FloatingIslandLayout.columnTopY(ax, az, minY, maxY);
            if (top <= minY) {
                continue;
            }
            if (!FloatingIslandLayout.columnContains(ax, az, top, minY, maxY)) {
                continue;
            }
            Optional<FloatingIslandKey> owner = FloatingIslandLayout.islandOwningSurface(ax, az, minY, maxY);
            if (owner.isEmpty() || owner.get().regionX() != rcx || owner.get().regionZ() != rcz) {
                continue;
            }
            anchor = new BlockPos(ax, top, az);
            break;
        }
        if (anchor == null) {
            return;
        }

        RandomSource kindRnd = regionRandom(levelSeed, rcx, rcz, SALT_CONTROLLED_SETTLEMENT_TYPE);
        int wV = Config.CONTROLLED_SETTLEMENT_WEIGHT_VILLAGE.getAsInt();
        int wO = Config.CONTROLLED_SETTLEMENT_WEIGHT_OUTPOST.getAsInt();
        int wN = Config.CONTROLLED_SETTLEMENT_WEIGHT_NONE.getAsInt();
        int total = wV + wO + wN;
        if (total <= 0) {
            return;
        }
        int roll = kindRnd.nextInt(total);
        Structure structure;
        if (roll < wV) {
            Holder<Biome> biome = generator.rolledIslandSurfaceBiome(randomState, anchor.getX(), anchor.getZ(), minY, maxY);
            Optional<ResourceKey<Structure>> villageKey = villageKeyForBiome(biome);
            if (villageKey.isEmpty()) {
                return;
            }
            structure = registryAccess
                    .lookupOrThrow(Registries.STRUCTURE)
                    .getOrThrow(villageKey.get())
                    .value();
        } else if (roll < wV + wO) {
            structure = registryAccess
                    .lookupOrThrow(Registries.STRUCTURE)
                    .getOrThrow(ResourceKey.create(
                            Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("pillager_outpost")))
                    .value();
        } else {
            return;
        }

        double placeChance = Config.CONTROLLED_SETTLEMENT_PLACE_TRY_CHANCE.getAsDouble();
        if (placeChance <= 0.0d) {
            return;
        }
        if (placeChance < 1.0d) {
            RandomSource placeTryRnd = regionRandom(levelSeed, rcx, rcz, SALT_CONTROLLED_PLACE_TRY);
            if (placeTryRnd.nextDouble() > placeChance) {
                return;
            }
        }

        if (!(structure instanceof JigsawStructure jig)) {
            return;
        }

        /*
         * JigsawPlacement adds heightmap projection as: k = pos.getY() + chunkGenerator.getFirstFreeHeight(...),
         * where getFirstFreeHeight is an absolute Y (for us: procedural surface + 1). Vanilla passes a low
         * startHeight sample for pos.getY(); using surface top here double-counts and spawns settlements in the sky.
         */
        BlockPos jigsawAnchor = jig.projectStartToHeightmap.isPresent()
                ? new BlockPos(anchor.getX(), 0, anchor.getZ())
                : anchor;

        Predicate<Holder<Biome>> validBiome = h -> true;
        Structure.GenerationContext ctx = new Structure.GenerationContext(
                registryAccess,
                generator,
                generator.getBiomeSource(),
                randomState,
                structureTemplateManager,
                levelSeed,
                cpos,
                chunk,
                validBiome);

        Optional<Structure.GenerationStub> stub = JigsawPlacement.addPieces(
                ctx,
                jig.startPool,
                jig.startJigsawName,
                jig.maxDepth,
                jigsawAnchor,
                jig.useExpansionHack,
                jig.projectStartToHeightmap,
                jig.maxDistanceFromCenter,
                PoolAliasLookup.create(jig.poolAliases, jigsawAnchor, levelSeed),
                jig.dimensionPadding,
                jig.liquidSettings);

        if (stub.isEmpty()) {
            return;
        }
        StructurePiecesBuilder piecesBuilder = stub.get().getPiecesBuilder();
        StructureStart start = new StructureStart(structure, cpos, 0, piecesBuilder.build());
        if (!start.isValid()) {
            return;
        }
        structureManager.setStartForStructure(SectionPos.bottomOf(chunk), structure, start, chunk);
    }

    private static Optional<ResourceKey<Structure>> villageKeyForBiome(Holder<Biome> biome) {
        ResourceKey<Structure> key;
        if (biome.is(Biomes.DESERT)) {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_desert"));
        } else if (biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.SNOWY_TAIGA)) {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_snowy"));
        } else if (biome.is(Biomes.TAIGA)) {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_taiga"));
        } else if (biome.is(Biomes.SAVANNA)
                || biome.is(Biomes.SAVANNA_PLATEAU)
                || biome.is(Biomes.WINDSWEPT_SAVANNA)) {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_savanna"));
        } else {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_plains"));
        }
        return Optional.of(key);
    }
}
