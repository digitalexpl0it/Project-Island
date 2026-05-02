package net.projectisland.worldgen;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.projectisland.Config;
import net.projectisland.compat.TakesAPillageIntegration;
import net.projectisland.island.FloatingIslandKey;

/**
 * Strips vanilla settlement starts and places one deterministic jigsaw settlement per inhabited island region when
 * {@link Config#FLOATING_ISLANDS_CONTROLLED_SETTLEMENT_PLACEMENT} is enabled. Runs during {@code STRUCTURE_STARTS} so
 * {@link net.minecraft.world.level.chunk.ChunkGenerator#createReferences} wires structure references correctly.
 */
public final class IslandRegionControlledSettlementPlacement {
    private static final int SALT_CONTROLLED_ANCHOR = 402_113;
    /** Bastille vs pillager camp (1:2 like the mod’s structure set). */
    private static final int SALT_TAKESAPILLAGE_VARIANT = 771_029;

    private IslandRegionControlledSettlementPlacement() {}

    public static void stripVanillaSettlementStarts(RegistryAccess registryAccess, ChunkAccess chunk) {
        var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        for (var entry : new ArrayList<>(chunk.getAllStarts().entrySet())) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            ResourceLocation id = structureRegistry.getKey(structure);
            if (!FloatingIslandsChunkGenerator.shouldStripBeforeControlledSettlement(id)) {
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

        RandomSource settleRnd =
                IslandRegionSettlementRoll.regionRandom(levelSeed, rcx, rcz, IslandRegionSettlementRoll.SALT_REGION_SETTLEMENT_ROLL);
        if (!IslandRegionStructurePicker.rollSettlementAllowed(settleRnd)) {
            return;
        }

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int jitter = Config.CONTROLLED_SETTLEMENT_ANCHOR_JITTER_BLOCKS.getAsInt();
        int maxTries = Config.CONTROLLED_SETTLEMENT_ANCHOR_TRIES.getAsInt();
        RandomSource anchorRnd =
                IslandRegionSettlementRoll.regionRandom(levelSeed, rcx, rcz, SALT_CONTROLLED_ANCHOR);
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

        RandomSource kindRnd =
                IslandRegionSettlementRoll.regionRandom(
                        levelSeed, rcx, rcz, IslandRegionSettlementRoll.SALT_CONTROLLED_SETTLEMENT_TYPE);
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
            RandomSource variantRnd =
                    IslandRegionSettlementRoll.regionRandom(levelSeed, rcx, rcz, SALT_TAKESAPILLAGE_VARIANT);
            structure = resolveOutpostStructure(registryAccess, variantRnd);
            if (structure == null) {
                return;
            }
        } else {
            return;
        }

        double placeChance = Config.CONTROLLED_SETTLEMENT_PLACE_TRY_CHANCE.getAsDouble();
        if (placeChance <= 0.0d) {
            return;
        }
        if (placeChance < 1.0d) {
            RandomSource placeTryRnd =
                    IslandRegionSettlementRoll.regionRandom(
                            levelSeed, rcx, rcz, IslandRegionSettlementRoll.SALT_CONTROLLED_PLACE_TRY);
            if (placeTryRnd.nextDouble() > placeChance) {
                return;
            }
        }

        if (attachControlledStructureStart(
                structure,
                generator,
                registryAccess,
                randomState,
                structureTemplateManager,
                structureManager,
                chunk,
                levelSeed,
                cpos,
                anchor)) {
            return;
        }

        if (roll >= wV && roll < wV + wO
                && TakesAPillageIntegration.isLoaded()
                && Config.FLOATING_ISLANDS_TAKESAPILLAGE_CONTROLLED_OUTPOST.getAsBoolean()
                && !(structure instanceof JigsawStructure)) {
            Structure vanillaOutpost = registryAccess
                    .lookupOrThrow(Registries.STRUCTURE)
                    .get(ResourceKey.create(
                                    Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("pillager_outpost")))
                    .map(Holder::value)
                    .orElse(null);
            if (vanillaOutpost instanceof JigsawStructure && vanillaOutpost != structure) {
                attachControlledStructureStart(
                        vanillaOutpost,
                        generator,
                        registryAccess,
                        randomState,
                        structureTemplateManager,
                        structureManager,
                        chunk,
                        levelSeed,
                        cpos,
                        anchor);
            }
        }
    }

    /**
     * Prefer **It Takes a Pillage** bastille / pillager camp when enabled; else vanilla pillager outpost.
     */
    private static Structure resolveOutpostStructure(RegistryAccess registryAccess, RandomSource variantRnd) {
        var lookup = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        if (TakesAPillageIntegration.isLoaded()
                && Config.FLOATING_ISLANDS_TAKESAPILLAGE_CONTROLLED_OUTPOST.getAsBoolean()) {
            ResourceKey<Structure> key =
                    variantRnd.nextInt(3) == 0 ? TakesAPillageIntegration.BASTILLE : TakesAPillageIntegration.PILLAGER_CAMP;
            Optional<Structure> mod = lookup.get(key).map(Holder::value);
            if (mod.isPresent()) {
                return mod.get();
            }
        }
        return lookup.get(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("pillager_outpost")))
                .map(Holder::value)
                .orElse(null);
    }

    /**
     * {@link JigsawStructure} uses anchored {@link JigsawPlacement}; other types (e.g. {@code takesapillage:pillager_structure}) use
     * {@link Structure#findValidGenerationPoint}.
     */
    private static boolean attachControlledStructureStart(
            Structure structure,
            FloatingIslandsChunkGenerator generator,
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager structureTemplateManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            long levelSeed,
            ChunkPos cpos,
            BlockPos anchor) {
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

        Optional<Structure.GenerationStub> stub;
        if (structure instanceof JigsawStructure jig) {
            /*
             * JigsawPlacement adds heightmap projection as: k = pos.getY() + chunkGenerator.getFirstFreeHeight(...),
             * where getFirstFreeHeight is an absolute Y (for us: procedural surface + 1). Vanilla passes a low
             * startHeight sample for pos.getY(); using surface top here double-counts and spawns settlements in the sky.
             */
            BlockPos jigsawAnchor = jig.projectStartToHeightmap.isPresent()
                    ? new BlockPos(anchor.getX(), 0, anchor.getZ())
                    : anchor;
            stub = JigsawPlacement.addPieces(
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
        } else {
            /*
             * takesapillage:pillager_structure (bastille / pillager camp) runs ModStructureUtils.isRelativelyFlat first;
             * void + island height mismatch fails that check on floating worlds, so findValidGenerationPoint is almost
             * always empty and we used to fall back to vanilla outpost. Anchor jigsaw the same way as JigsawStructure.
             */
            ResourceLocation sid = registryAccess.registryOrThrow(Registries.STRUCTURE).getKey(structure);
            if (sid != null && TakesAPillageIntegration.isPillagerStructure(sid)) {
                stub = takesapillageAnchoredJigsaw(ctx, registryAccess, anchor, levelSeed, sid);
            } else {
                stub = Optional.empty();
            }
            if (stub.isEmpty()) {
                stub = structure.findValidGenerationPoint(ctx);
            }
        }

        if (stub.isEmpty()) {
            return false;
        }
        StructurePiecesBuilder piecesBuilder = stub.get().getPiecesBuilder();
        StructureStart start = new StructureStart(structure, cpos, 0, piecesBuilder.build());
        if (!start.isValid()) {
            return false;
        }
        structureManager.setStartForStructure(SectionPos.bottomOf(chunk), structure, start, chunk);
        return true;
    }

    /**
     * Mirrors {@code takesapillage} JSON for bastille / pillager_camp ({@code project_start_to_heightmap}, pools, depth).
     */
    private static Optional<Structure.GenerationStub> takesapillageAnchoredJigsaw(
            Structure.GenerationContext ctx,
            RegistryAccess registryAccess,
            BlockPos anchor,
            long levelSeed,
            ResourceLocation structureId) {
        if (!TakesAPillageIntegration.isLoaded() || !Config.FLOATING_ISLANDS_TAKESAPILLAGE_CONTROLLED_OUTPOST.getAsBoolean()) {
            return Optional.empty();
        }
        boolean bastille = TakesAPillageIntegration.BASTILLE.location().equals(structureId);
        ResourceKey<StructureTemplatePool> poolKey =
                bastille ? TakesAPillageIntegration.BASTILLE_START_POOL : TakesAPillageIntegration.PILLAGER_CAMP_START_POOL;
        int depth =
                bastille ? TakesAPillageIntegration.BASTILLE_JIGSAW_DEPTH : TakesAPillageIntegration.PILLAGER_CAMP_JIGSAW_DEPTH;

        BlockPos jigsawAnchor = new BlockPos(anchor.getX(), 0, anchor.getZ());
        return registryAccess
                .lookupOrThrow(Registries.TEMPLATE_POOL)
                .get(poolKey)
                .flatMap(
                        poolHolder ->
                                JigsawPlacement.addPieces(
                                        ctx,
                                        poolHolder,
                                        Optional.empty(),
                                        depth,
                                        jigsawAnchor,
                                        false,
                                        Optional.of(Heightmap.Types.WORLD_SURFACE_WG),
                                        TakesAPillageIntegration.PILLAGER_STRUCTURE_MAX_DISTANCE_FROM_CENTER,
                                        PoolAliasLookup.create(List.of(), jigsawAnchor, levelSeed),
                                        JigsawStructure.DEFAULT_DIMENSION_PADDING,
                                        JigsawStructure.DEFAULT_LIQUID_SETTINGS));
    }

    /**
     * Picks a vanilla {@code village_*} id that the biome is allowed to generate (same tags datapacks use). Mod biomes
     * (e.g. BOP snow) must match {@link BiomeTags#HAS_VILLAGE_SNOWY} etc.; using {@code village_plains} on snowy mod
     * biomes makes {@link JigsawPlacement} fail silently so regions skew toward successful outposts only.
     */
    private static Optional<ResourceKey<Structure>> villageKeyForBiome(Holder<Biome> biome) {
        ResourceKey<Structure> key;
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_desert"));
        } else if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_snowy"));
        } else if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_taiga"));
        } else if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_savanna"));
        } else if (biome.is(BiomeTags.HAS_VILLAGE_PLAINS)) {
            key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_plains"));
        } else {
            key = legacyVillageKeyForUntaggedVanillaStyleBiome(biome);
        }
        return Optional.of(key);
    }

    /** Island picker biomes that lack HAS_VILLAGE_* tags (should be rare); mirrors pre-tag vanilla heuristics. */
    private static ResourceKey<Structure> legacyVillageKeyForUntaggedVanillaStyleBiome(Holder<Biome> biome) {
        if (biome.is(Biomes.DESERT)) {
            return ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_desert"));
        }
        if (biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.SNOWY_TAIGA)) {
            return ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_snowy"));
        }
        if (biome.is(Biomes.TAIGA)) {
            return ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_taiga"));
        }
        if (biome.is(Biomes.SAVANNA)
                || biome.is(Biomes.SAVANNA_PLATEAU)
                || biome.is(Biomes.WINDSWEPT_SAVANNA)) {
            return ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_savanna"));
        }
        return ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_plains"));
    }
}
