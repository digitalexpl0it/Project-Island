package net.projectisland.worldgen;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.compat.BiomeModIntegration;
import net.projectisland.compat.TakesAPillageIntegration;
import net.projectisland.island.FloatingIslandKey;
import org.jetbrains.annotations.Nullable;

/**
 * Void sky islands: asymmetric vertical profile (flat-ish dome top, deeper underside).
 * Horizontal scale uses smooth analytic wobble (no block-sized discontinuities).
 * Vanilla structures still generate, then {@link #trimFloatingStructureBlocks} removes
 * pieces in void columns or floating above the island surface so ruins tend to hug terrain where they overlap land.
 * {@link #removeStructureStartsWithNoIslandContact} drops **mineshaft** starts unless the bounding box is **centered**
 * on island mass with enough horizontal overlap (configurable). Void-column trimming preserves mineshaft corridors by
 * default ({@link Config#FLOATING_ISLANDS_TRIM_STRIP_MINESHAFT_THROUGH_VOID}) so halls stay connected under islands.
 */
public final class FloatingIslandsChunkGenerator extends ChunkGenerator {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "floating_islands");

    public static final MapCodec<FloatingIslandsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(instance, FloatingIslandsChunkGenerator::new));

    private static final ResourceLocation ISLAND_REGION_BIOME_RANDOM =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "island_region_biome");

    private static final ResourceLocation LEVITE_VOID_COLUMN_BIOME_RANDOM =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "levite_void_column_biome");

    /** F3-friendly biome for void columns (no solid island); not rolled per region. */
    private static final ResourceKey<Biome> VOID_COLUMN_BIOME = Biomes.PLAINS;

    private final Map<ResourceKey<Biome>, Holder<Biome>> biomeHolderCache = new HashMap<>();

    /**
     * Biome registry view taken from any {@link Holder.Reference} in this dimension’s {@link BiomeSource}, so mod
     * biomes (e.g. Biomes O’ Plenty) can be resolved even when they are not listed in
     * {@link BiomeSource#possibleBiomes()} (common with TerraBlender).
     */
    @Nullable
    private HolderLookup<Biome> biomeRegistryLookup;

    private boolean biomeRegistryLookupResolved;

    /** Cached discover-all mod biome keys from {@link BiomeModIntegration#listDiscoverableBiomeKeys} when discovery is on. */
    @Nullable
    private List<ResourceKey<Biome>> cachedRegisteredModBiomeKeys;

    /** Delegate for vanilla carving context only (masked by {@link FloatingIslandMaskedCarvers}). */
    private NoiseBasedChunkGenerator islandCarvingNoiseDelegate;

    private Holder<NoiseGeneratorSettings> islandCarvingNoiseSettings;

    public FloatingIslandsChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    private void ensureIslandCarvingNoiseDelegate(RegistryAccess registryAccess) {
        if (islandCarvingNoiseDelegate == null) {
            islandCarvingNoiseSettings = FloatingIslandMaskedCarvers.resolveOverworldNoiseSettings(registryAccess);
            islandCarvingNoiseDelegate = new NoiseBasedChunkGenerator(getBiomeSource(), islandCarvingNoiseSettings);
        }
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

    private Optional<HolderLookup<Biome>> biomeRegistryLookup() {
        if (!biomeRegistryLookupResolved) {
            biomeRegistryLookupResolved = true;
            for (Holder<Biome> h : getBiomeSource().possibleBiomes()) {
                if (h instanceof Holder.Reference<Biome> ref) {
                    biomeRegistryLookup = ref.unwrapLookup();
                    break;
                }
            }
        }
        return Optional.ofNullable(biomeRegistryLookup);
    }

    /**
     * Prefers the live holder from {@link BiomeSource#possibleBiomes()}, then the dimension’s biome registry (needed
     * for mod biomes omitted from {@code possibleBiomes}).
     */
    private Optional<Holder<Biome>> resolveBiomeHolder(ResourceKey<Biome> key) {
        for (Holder<Biome> h : getBiomeSource().possibleBiomes()) {
            if (h.is(key)) {
                return Optional.of(h);
            }
        }
        return biomeRegistryLookup().flatMap(lookup -> lookup.get(key).map(ref -> ref));
    }

    private Holder<Biome> holderForBiome(ResourceKey<Biome> key) {
        return biomeHolderCache.computeIfAbsent(
                key,
                k -> resolveBiomeHolder(k)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Biome " + k.location()
                                                + " is not in this world’s biome registry; check id spelling and that the mod is loaded.")));
    }

    /** Whether a configured mod biome id can be rolled (present in the same registry as this dimension’s biomes). */
    private boolean isModIslandBiomeAllowed(ResourceKey<Biome> key) {
        return resolveBiomeHolder(key).isPresent();
    }

    /**
     * Discoverable mod biome ids in the dimension biome lookup when {@link Config#ISLAND_BIOME_MOD_DISCOVER_ALL_REGISTERED}
     * is {@code true}; otherwise empty (picker uses explicit config list only).
     */
    private List<ResourceKey<Biome>> registeredModBiomeKeysForPicker() {
        if (!BiomeModIntegration.anyDiscoverableBiomeModLoaded()
                || !Config.ISLAND_BIOME_MOD_DISCOVER_ALL_REGISTERED.getAsBoolean()) {
            return List.of();
        }
        if (cachedRegisteredModBiomeKeys == null) {
            cachedRegisteredModBiomeKeys =
                    biomeRegistryLookup().map(BiomeModIntegration::listDiscoverableBiomeKeys).orElse(List.of());
        }
        return cachedRegisteredModBiomeKeys;
    }

    /**
     * Island stone columns roll per-region surface biomes; open void near islands can use Levite Fields; deep void uses
     * {@link #VOID_COLUMN_BIOME}.
     */
    private Holder<Biome> biomeForSurfaceColumn(
            RandomState randomState, int blockX, int blockZ, int minY, int maxY, int columnTopY) {
        if (columnTopY > minY) {
            Optional<FloatingIslandKey> owner = FloatingIslandLayout.islandOwningSurface(blockX, blockZ, minY, maxY);
            if (owner.isPresent()) {
                FloatingIslandKey key = owner.get();
                RandomSource rnd = randomState
                        .getOrCreateRandomFactory(ISLAND_REGION_BIOME_RANDOM)
                        .at(key.regionX(), key.regionZ(), 0);
                ResourceKey<Biome> chosen = IslandRegionBiomePicker.roll(
                        rnd, this::isModIslandBiomeAllowed, this.registeredModBiomeKeysForPicker());
                return holderForBiome(chosen);
            }
        }

        if (columnEligibleForLeviteVoidBiome(blockX, blockZ, minY, maxY, randomState)) {
            return holderForBiome(BiomeModIntegration.LEVITITE_FIELDS_BIOME);
        }

        return holderForBiome(VOID_COLUMN_BIOME);
    }

    /**
     * Open void between nearby island masses (not on island stone). Uses horizontal distance to the nearest
     * procedural island ellipsoid, then {@link Config#ISLAND_LEVITE_FIELDS_VOID_BIOME_CHANCE} per column.
     */
    private boolean columnEligibleForLeviteVoidBiome(
            int blockX, int blockZ, int minY, int maxY, RandomState randomState) {
        if (!Config.leviteFieldsInVoidBetweenIslands() || !BiomeModIntegration.leviteFieldsLoaded()) {
            return false;
        }
        if (!resolveBiomeHolder(BiomeModIntegration.LEVITITE_FIELDS_BIOME).isPresent()) {
            return false;
        }
        if (FloatingIslandLayout.columnTopY(blockX, blockZ, minY, maxY) > minY) {
            return false;
        }
        Optional<Double> horiz = FloatingIslandLayout.closestProceduralIslandHoriz(blockX, blockZ);
        if (horiz.isEmpty()) {
            return false;
        }
        double h = horiz.get();
        if (h < 1.0d) {
            return false;
        }
        if (h > Config.ISLAND_LEVITE_FIELDS_VOID_MAX_HORIZ_BEYOND_EDGE.get()) {
            return false;
        }
        double chance = Config.ISLAND_LEVITE_FIELDS_VOID_BIOME_CHANCE.get();
        if (chance >= 1.0d) {
            return true;
        }
        if (chance <= 0.0d) {
            return false;
        }
        RandomSource rnd = randomState.getOrCreateRandomFactory(LEVITE_VOID_COLUMN_BIOME_RANDOM).at(blockX, blockZ, 0);
        return rnd.nextDouble() < chance;
    }

    /** Surface biome at ({@code wx},{@code wz}) used for village variant selection (matches column top roll). */
    Holder<Biome> rolledIslandSurfaceBiome(RandomState randomState, int wx, int wz, int minY, int maxY) {
        int top = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
        return biomeForSurfaceColumn(randomState, wx, wz, minY, maxY, top);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(
            RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        chunk.fillBiomesFromNoise(
                (qx, qy, qz, s) -> {
                    int bx = QuartPos.toBlock(qx) + 2;
                    int bz = QuartPos.toBlock(qz) + 2;
                    int top = FloatingIslandLayout.columnTopY(bx, bz, minY, maxY);
                    return this.biomeForSurfaceColumn(randomState, bx, bz, minY, maxY, top);
                },
                randomState.sampler());
        return CompletableFuture.completedFuture(chunk);
    }

    private static final int SALT_REGION_RARE_STRUCTURE_ROLL = 771_977;
    private static final int SALT_REGION_SETTLEMENT_ROLL = 991_871;

    private static final ResourceLocation PILLAGER_OUTPOST =
            ResourceLocation.withDefaultNamespace("pillager_outpost");

    private static final ResourceLocation MINESHAFT = ResourceLocation.withDefaultNamespace("mineshaft");

    private static final ResourceLocation STRONGHOLD = ResourceLocation.withDefaultNamespace("stronghold");

    private static final ResourceLocation MONSTER_ROOM = ResourceLocation.withDefaultNamespace("monster_room");

    private static final ResourceLocation TRIAL_CHAMBERS = ResourceLocation.withDefaultNamespace("trial_chambers");

    private static final ResourceLocation RUINED_PORTAL = ResourceLocation.withDefaultNamespace("ruined_portal");

    private static final ResourceLocation WOODLAND_MANSION = ResourceLocation.withDefaultNamespace("mansion");

    private static final ResourceLocation IGLOO = ResourceLocation.withDefaultNamespace("igloo");

    /**
     * Vanilla jigsaw villages register as {@code minecraft:village_plains}, {@code village_desert}, … — there is no
     * {@code minecraft:village} structure id in 1.21. Skip aggressive trim / void-only removal for those and outposts.
     */
    /** Same package: controlled settlement placement strips these ids. */
    static boolean isSettlementStructure(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        if (PILLAGER_OUTPOST.equals(id)) {
            return true;
        }
        return "minecraft".equals(id.getNamespace()) && id.getPath().startsWith("village_");
    }

    /**
     * Vanilla settlements plus optional **takesapillage** bastille / camp when controlled replacement for those is enabled.
     */
    static boolean shouldStripBeforeControlledSettlement(ResourceLocation id) {
        if (isSettlementStructure(id)) {
            return true;
        }
        return Config.FLOATING_ISLANDS_CONTROLLED_SETTLEMENT_PLACEMENT.getAsBoolean()
                && Config.FLOATING_ISLANDS_TAKESAPILLAGE_CONTROLLED_OUTPOST.getAsBoolean()
                && TakesAPillageIntegration.isLoaded()
                && TakesAPillageIntegration.isPillagerStructure(id);
    }

    /**
     * Skip “floating above procedural top” stripping — multi-story builds would lose upper floors (see
     * {@link #trimFloatingStructureBlocks}).
     */
    static boolean preservesStructureAboveSurfaceTrim(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        if (isSettlementStructure(id)) {
            return true;
        }
        if (TakesAPillageIntegration.isPillagerStructure(id)) {
            return true;
        }
        return WOODLAND_MANSION.equals(id) || IGLOO.equals(id);
    }

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager) {
        super.createStructures(registryAccess, structureState, structureManager, chunk, structureTemplateManager);
        if (Config.FLOATING_ISLANDS_CONTROLLED_RARE_DUNGEON_PLACEMENT.getAsBoolean()) {
            IslandRegionControlledRareStructurePlacement.stripVanillaRareDungeonStarts(registryAccess, chunk);
            IslandRegionControlledRareStructurePlacement.tryPlaceControlledRareDungeon(
                    this,
                    registryAccess,
                    structureState,
                    structureManager,
                    chunk,
                    structureTemplateManager);
        }
        FloatingIslandRareStructureVerticalSnap.snapRareStructuresVertically(registryAccess, chunk);
        removeStructureStartsWithNoIslandContact(registryAccess, chunk);
        trimFloatingStructureBlocks(registryAccess, chunk);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.WORLD_SURFACE_WG));
        if (Config.FLOATING_ISLANDS_CONTROLLED_SETTLEMENT_PLACEMENT.getAsBoolean()) {
            IslandRegionControlledSettlementPlacement.stripVanillaSettlementStarts(registryAccess, chunk);
            IslandRegionControlledSettlementPlacement.tryPlaceControlledSettlement(
                    this,
                    registryAccess,
                    structureState,
                    structureManager,
                    chunk,
                    structureTemplateManager);
        }
    }

    /**
     * Drops structure starts that never intersect floating terrain so modded/vanilla pieces are not left as pure void
     * artifacts (see {@link Config#FLOATING_ISLANDS_REMOVE_STRUCTURES_WITH_NO_LAND_CONTACT}).
     */
    private static void removeStructureStartsWithNoIslandContact(RegistryAccess registryAccess, ChunkAccess chunk) {
        if (!Config.FLOATING_ISLANDS_REMOVE_STRUCTURES_WITH_NO_LAND_CONTACT.getAsBoolean()) {
            return;
        }
        var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        for (var entry : new ArrayList<>(chunk.getAllStarts().entrySet())) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            ResourceLocation sid = structureRegistry.getKey(structure);
            if (isSettlementStructure(sid)) {
                continue;
            }
            if (Config.FLOATING_ISLANDS_CONTROLLED_SETTLEMENT_PLACEMENT.getAsBoolean()
                    && Config.FLOATING_ISLANDS_TAKESAPILLAGE_CONTROLLED_OUTPOST.getAsBoolean()
                    && TakesAPillageIntegration.isPillagerStructure(sid)) {
                continue;
            }
            BoundingBox bb = start.getBoundingBox();
            if (structureStartAnchoredOnIsland(sid, bb, chunk, minY, maxY)) {
                continue;
            }
            wipeStructureBlocksInChunk(chunk, bb);
            chunk.setStartForStructure(structure, StructureStart.INVALID_START);
        }
    }

    /**
     * Whether this structure start should be kept for floating-island collision policy. Mineshafts and strongholds can use a
     * **stricter** rule so a corner graze does not preserve huge void-spanning volumes (see config).
     */
    private static boolean structureStartAnchoredOnIsland(
            ResourceLocation sid, BoundingBox bb, ChunkAccess chunk, int minY, int maxY) {
        if (RUINED_PORTAL.equals(sid) && Config.FLOATING_ISLANDS_RUINED_PORTAL_CHUNK_LOCAL_LAND_ANCHOR.getAsBoolean()) {
            return chunkBoundingSliceMidpointHasIslandLand(bb, chunk, minY, maxY);
        }
        if (MINESHAFT.equals(sid) && Config.FLOATING_ISLANDS_MINESHAFT_STRICT_ISLAND_OVERLAP.getAsBoolean()) {
            return boundingBoxStrongIslandOverlap(
                    bb,
                    minY,
                    maxY,
                    Config.FLOATING_ISLANDS_MINESHAFT_MIN_ISLAND_COLUMN_FRACTION.getAsDouble());
        }
        if (STRONGHOLD.equals(sid) && Config.FLOATING_ISLANDS_STRONGHOLD_STRICT_ISLAND_OVERLAP.getAsBoolean()) {
            return boundingBoxStrongIslandOverlap(
                    bb,
                    minY,
                    maxY,
                    Config.FLOATING_ISLANDS_STRONGHOLD_MIN_ISLAND_COLUMN_FRACTION.getAsDouble());
        }
        return boundingBoxTouchesProceduralIsland(bb, minY, maxY);
    }

    /**
     * True when the horizontal midpoint of (structure BB ∩ this chunk) sits on a column with procedural island surface.
     * Used for {@link #RUINED_PORTAL}: vanilla’s BB can touch distant land while this chunk’s fragment is void-only.
     */
    private static boolean chunkBoundingSliceMidpointHasIslandLand(
            BoundingBox bb, ChunkAccess chunk, int minY, int maxY) {
        ChunkPos cp = chunk.getPos();
        int x0 = Math.max(bb.minX(), cp.getMinBlockX());
        int x1 = Math.min(bb.maxX(), cp.getMaxBlockX());
        int z0 = Math.max(bb.minZ(), cp.getMinBlockZ());
        int z1 = Math.min(bb.maxZ(), cp.getMaxBlockZ());
        if (x0 > x1 || z0 > z1) {
            return false;
        }
        int mx = (x0 + x1) >> 1;
        int mz = (z0 + z1) >> 1;
        return FloatingIslandLayout.columnTopY(mx, mz, minY, maxY) > minY;
    }

    /**
     * Requires BB center on island plus (unless fraction is 0) a minimum share of footprint samples with island columns.
     */
    private static boolean boundingBoxStrongIslandOverlap(BoundingBox bb, int minY, int maxY, double minFrac) {
        int midX = (bb.minX() + bb.maxX()) >> 1;
        int midZ = (bb.minZ() + bb.maxZ()) >> 1;
        if (FloatingIslandLayout.columnTopY(midX, midZ, minY, maxY) <= minY) {
            return false;
        }
        if (minFrac <= 0.0d) {
            return true;
        }
        int x0 = bb.minX();
        int x1 = bb.maxX();
        int z0 = bb.minZ();
        int z1 = bb.maxZ();
        int dx = x1 - x0 + 1;
        int dz = z1 - z0 + 1;
        int step = Mth.clamp(Math.min(dx, dz) / 12, 3, 11);
        int total = 0;
        int hits = 0;
        for (int wx = x0; wx <= x1; wx += step) {
            for (int wz = z0; wz <= z1; wz += step) {
                total++;
                if (FloatingIslandLayout.columnTopY(wx, wz, minY, maxY) > minY) {
                    hits++;
                }
            }
        }
        if (total == 0) {
            return false;
        }
        return (double) hits / (double) total >= minFrac;
    }

    private static boolean boundingBoxTouchesProceduralIsland(BoundingBox bb, int minY, int maxY) {
        int x0 = bb.minX();
        int x1 = bb.maxX();
        int z0 = bb.minZ();
        int z1 = bb.maxZ();
        int dx = x1 - x0 + 1;
        int dz = z1 - z0 + 1;
        int step = Mth.clamp(Math.min(dx, dz) / 16, 2, 20);

        for (int wx = x0; wx <= x1; wx += step) {
            for (int wz = z0; wz <= z1; wz += step) {
                if (FloatingIslandLayout.columnTopY(wx, wz, minY, maxY) > minY) {
                    return true;
                }
            }
        }
        int midX = (x0 + x1) >> 1;
        int midZ = (z0 + z1) >> 1;
        int[][] pts = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {midX, midZ}};
        for (int[] p : pts) {
            if (FloatingIslandLayout.columnTopY(p[0], p[1], minY, maxY) > minY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Per {@link FloatingIslandLayout} island region (aligned with {@link IslandRegionBiomePicker}), deterministically
     * decides whether to keep monster rooms, trial chambers, pyramids, and settlements — same weighted pattern as island
     * biomes. Samples chunk noise biome at the structure bounding-box center so temple/village ids match the island biome.
     * {@link #STRONGHOLD} starts are handled first (vertical stone overlap only — not part of the picker).
     * <p>
     * Must run during {@link #applyBiomeDecoration} (not {@link #createStructures}): at {@code STRUCTURE_STARTS} the
     * {@link ChunkAccess} has no biome data yet and {@link ProtoChunk#getNoiseBiome} throws.
     */
    private static void applyIslandRegionStructureGating(RegistryAccess registryAccess, ChunkAccess chunk, long levelSeed) {
        var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        for (var entry : new ArrayList<>(chunk.getAllStarts().entrySet())) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            ResourceLocation id = structureRegistry.getKey(structure);
            if (id == null) {
                continue;
            }
            BoundingBox bb = start.getBoundingBox();
            int cx = (bb.minX() + bb.maxX()) >> 1;
            int cz = (bb.minZ() + bb.maxZ()) >> 1;

            // Stronghold libraries / corridors are not in IslandRegionStructurePicker — vanilla ring placement ignores terrain.
            if (STRONGHOLD.equals(id)
                    && Config.FLOATING_ISLANDS_CAVE_STRUCTURE_REQUIRE_STONE_Y_OVERLAP.getAsBoolean()
                    && !structureBoundingIntersectsIslandStoneColumn(cx, cz, bb, minY, maxY)) {
                wipeStructureBlocksInChunk(chunk, bb);
                chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                continue;
            }

            if (!IslandRegionStructurePicker.isGatedStructureType(id)) {
                continue;
            }
            if (Config.FLOATING_ISLANDS_CONTROLLED_SETTLEMENT_PLACEMENT.getAsBoolean()
                    && isSettlementStructure(id)) {
                continue;
            }

            Optional<FloatingIslandKey> owner = FloatingIslandLayout.islandOwningSurface(cx, cz, minY, maxY);
            int rcx;
            int rcz;
            if (owner.isPresent()) {
                rcx = owner.get().regionX();
                rcz = owner.get().regionZ();
            } else {
                rcx = Mth.floorDiv(cx >> 4, FloatingIslandLayout.REGION_CHUNKS);
                rcz = Mth.floorDiv(cz >> 4, FloatingIslandLayout.REGION_CHUNKS);
            }
            if (!FloatingIslandLayout.regionHasIsland(rcx, rcz)) {
                continue;
            }

            RandomSource rareRnd = regionStructureRandom(levelSeed, rcx, rcz, SALT_REGION_RARE_STRUCTURE_ROLL);
            RandomSource settleRnd = regionStructureRandom(levelSeed, rcx, rcz, SALT_REGION_SETTLEMENT_ROLL);
            IslandRegionRareStructureSlot rare = IslandRegionStructurePicker.rollRare(rareRnd);
            boolean settlementOk = IslandRegionStructurePicker.rollSettlementAllowed(settleRnd);
            Holder<Biome> biome = sampleBiomeAt(chunk, cx, cz, minY, maxY);

            boolean remove = IslandRegionStructurePicker.shouldRemoveStructure(id, rare, settlementOk, biome);
            if (!remove
                    && Config.FLOATING_ISLANDS_CAVE_STRUCTURE_REQUIRE_STONE_Y_OVERLAP.getAsBoolean()
                    && (MONSTER_ROOM.equals(id) || TRIAL_CHAMBERS.equals(id))
                    && !structureBoundingIntersectsIslandStoneColumn(cx, cz, bb, minY, maxY)) {
                remove = true;
            }
            if (remove) {
                wipeStructureBlocksInChunk(chunk, bb);
                chunk.setStartForStructure(structure, StructureStart.INVALID_START);
            }
        }
    }

    /**
     * {@code true} when the structure’s Y span intersects the procedural island stone span at the bounding-box center
     * (same column math as {@link FloatingIslandLayout#columnContains}).
     */
    private static boolean structureBoundingIntersectsIslandStoneColumn(
            int cx, int cz, BoundingBox bb, int minY, int maxY) {
        int top = FloatingIslandLayout.columnTopY(cx, cz, minY, maxY);
        if (top <= minY) {
            return false;
        }
        int bottom = FloatingIslandLayout.columnBottomY(cx, cz, minY, maxY);
        int lo = bb.minY();
        int hi = bb.maxY();
        return hi >= bottom && lo <= top;
    }

    private static RandomSource regionStructureRandom(long levelSeed, int rcx, int rcz, int salt) {
        return RandomSource.create(Mth.getSeed(rcx, salt, rcz) ^ levelSeed ^ (levelSeed >>> 32));
    }

    private static Holder<Biome> sampleBiomeAt(ChunkAccess chunk, int wx, int wz, int minY, int maxY) {
        int top = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
        int y = top > minY ? top + 2 : minY + 80;
        int qx = QuartPos.fromBlock(wx);
        int qy = QuartPos.fromBlock(y);
        int qz = QuartPos.fromBlock(wz);
        return chunk.getNoiseBiome(qx, qy, qz);
    }

    static void wipeStructureBlocksInChunk(ChunkAccess chunk, BoundingBox bb) {
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
        applyIslandRegionStructureGating(level.registryAccess(), chunk, level.getSeed());
        if (Config.FLOATING_ISLANDS_TRIM_STRUCTURE_VOID_BLOCKS_AFTER_FEATURES.getAsBoolean()) {
            trimFloatingStructureBlocks(level.registryAccess(), chunk);
        }
        FloatingIslandRareStructureChains.tryPlaceDecorativeChains(level.registryAccess(), chunk);
        super.applyBiomeDecoration(level, chunk, structureManager);
        FloatingIslandsOreThinning.applyAfterDecoration(level, chunk);
        sprinkleSurfaceWaterPools(level, chunk);
        sprinkleExtraSurfaceTrees(level, chunk);
        FloatingIslandExteriorFluidStrip.applyAfterDecoration(level, chunk);
    }

    /**
     * Shallow water bowls on grass / sand / mycelium tops (vanilla biome decoration rarely produces surface lakes on
     * small custom terrain). Pools stay within this chunk and respect {@link FloatingIslandLayout#columnContains}.
     */
    private static void sprinkleSurfaceWaterPools(WorldGenLevel level, ChunkAccess chunk) {
        int maxPools = Config.FLOATING_ISLANDS_SURFACE_WATER_POOLS_PER_CHUNK.getAsInt();
        if (maxPools <= 0) {
            return;
        }
        ChunkPos cpos = chunk.getPos();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int minWX = cpos.getMinBlockX();
        int minWZ = cpos.getMinBlockZ();
        RandomSource rnd = RandomSource.create(cpos.toLong() ^ level.getSeed() ^ 0xB166E770L);
        if (rnd.nextDouble() >= Config.FLOATING_ISLANDS_SURFACE_WATER_POOL_CHUNK_CHANCE.getAsDouble()) {
            return;
        }

        List<BlockPos> candidates = new ArrayList<>();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = minWX + lx;
                int wz = minWZ + lz;
                int topY = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
                if (topY <= minY) {
                    continue;
                }
                BlockState surf = chunk.getBlockState(new BlockPos(lx, topY, lz));
                if (surf.is(Blocks.GRASS_BLOCK) || surf.is(Blocks.SAND) || surf.is(Blocks.MYCELIUM)) {
                    candidates.add(new BlockPos(wx, topY, wz));
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        int placed = 0;
        int tries = 0;
        int maxTries = maxPools * 14;
        while (placed < maxPools && tries < maxTries) {
            tries++;
            BlockPos center = candidates.get(rnd.nextInt(candidates.size()));
            int radius = 1 + rnd.nextInt(2);
            int depth = 1 + rnd.nextInt(2);
            if (tryCarveSurfaceWaterPool(chunk, minY, maxY, minWX, minWZ, center, radius, depth)) {
                placed++;
            }
        }
    }

    private static boolean tryCarveSurfaceWaterPool(
            ChunkAccess chunk,
            int minY,
            int maxY,
            int chunkMinWX,
            int chunkMinWZ,
            BlockPos centerWorldTop,
            int radius,
            int depth) {
        int cx = centerWorldTop.getX();
        int cz = centerWorldTop.getZ();
        int refTop = centerWorldTop.getY();

        List<int[]> columns = new ArrayList<>();
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) {
                    continue;
                }
                int wx = cx + dx;
                int wz = cz + dz;
                int lx = wx - chunkMinWX;
                int lzCol = wz - chunkMinWZ;
                if (lx < 0 || lx >= 16 || lzCol < 0 || lzCol >= 16) {
                    return false;
                }
                int top2 = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
                if (top2 <= minY || Mth.abs(top2 - refTop) > 2) {
                    return false;
                }
                BlockState surf = chunk.getBlockState(new BlockPos(lx, top2, lzCol));
                if (!surf.is(Blocks.GRASS_BLOCK) && !surf.is(Blocks.SAND) && !surf.is(Blocks.MYCELIUM)) {
                    return false;
                }
                for (int i = 0; i < depth; i++) {
                    int y = top2 - i;
                    if (y < minY || !FloatingIslandLayout.columnContains(wx, wz, y, minY, maxY)) {
                        return false;
                    }
                }
                columns.add(new int[] {lx, lzCol, top2});
            }
        }
        if (columns.isEmpty()) {
            return false;
        }

        BlockState water = Blocks.WATER.defaultBlockState();
        for (int[] col : columns) {
            int lx = col[0];
            int lzCol = col[1];
            int top2 = col[2];
            for (int i = 0; i < depth; i++) {
                chunk.setBlockState(new BlockPos(lx, top2 - i, lzCol), water, false);
            }
        }
        return true;
    }

    private void sprinkleExtraSurfaceTrees(WorldGenLevel level, ChunkAccess chunk) {
        int nGrassSandMycelium = Config.FLOATING_ISLANDS_EXTRA_SURFACE_TREES_PER_CHUNK.getAsInt();
        int nSnow = Config.FLOATING_ISLANDS_EXTRA_SURFACE_TREES_SNOW_PER_CHUNK.getAsInt();
        if (nGrassSandMycelium <= 0 && nSnow <= 0) {
            return;
        }
        ChunkPos cpos = chunk.getPos();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        HolderGetter<ConfiguredFeature<?, ?>> configured =
                level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
        RandomSource rnd = RandomSource.create(cpos.toLong() ^ level.getSeed());

        List<BlockPos> grassCols = new ArrayList<>();
        List<BlockPos> snowCols = new ArrayList<>();
        List<BlockPos> sandCols = new ArrayList<>();
        List<BlockPos> myceliumCols = new ArrayList<>();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = cpos.getMinBlockX() + lx;
                int wz = cpos.getMinBlockZ() + lz;
                int topY = FloatingIslandLayout.columnTopY(wx, wz, minY, maxY);
                if (topY <= minY) {
                    continue;
                }
                BlockPos surfacePos = new BlockPos(lx, topY, lz);
                BlockState surface = chunk.getBlockState(surfacePos);
                if (surface.is(Blocks.GRASS_BLOCK)) {
                    grassCols.add(new BlockPos(wx, topY, wz));
                } else if (surface.is(Blocks.SNOW_BLOCK)) {
                    snowCols.add(new BlockPos(wx, topY, wz));
                } else if (surface.is(Blocks.SAND)) {
                    sandCols.add(new BlockPos(wx, topY, wz));
                } else if (surface.is(Blocks.MYCELIUM)) {
                    myceliumCols.add(new BlockPos(wx, topY, wz));
                }
            }
        }

        for (int i = 0; i < nSnow && !snowCols.isEmpty(); i++) {
            tryPlaceExtraTree(level, this, chunk, configured, rnd, snowCols.get(rnd.nextInt(snowCols.size())));
        }
        for (int i = 0; i < nGrassSandMycelium && !grassCols.isEmpty(); i++) {
            tryPlaceExtraTree(level, this, chunk, configured, rnd, grassCols.get(rnd.nextInt(grassCols.size())));
        }
        for (int i = 0; i < nGrassSandMycelium && !sandCols.isEmpty(); i++) {
            tryPlaceExtraTree(level, this, chunk, configured, rnd, sandCols.get(rnd.nextInt(sandCols.size())));
        }
        for (int i = 0; i < nGrassSandMycelium && !myceliumCols.isEmpty(); i++) {
            tryPlaceExtraTree(level, this, chunk, configured, rnd, myceliumCols.get(rnd.nextInt(myceliumCols.size())));
        }
    }

    private static void tryPlaceExtraTree(
            WorldGenLevel level,
            ChunkGenerator generator,
            ChunkAccess chunk,
            HolderGetter<ConfiguredFeature<?, ?>> configured,
            RandomSource rnd,
            BlockPos colWorldTop) {
        int wx = colWorldTop.getX();
        int wz = colWorldTop.getZ();
        int topY = colWorldTop.getY();
        ChunkPos cpos = chunk.getPos();
        int lx = wx - cpos.getMinBlockX();
        int lz = wz - cpos.getMinBlockZ();
        BlockState surface = chunk.getBlockState(new BlockPos(lx, topY, lz));
        List<Holder<ConfiguredFeature<?, ?>>> variants = sprinkleVariantsForSurface(configured, surface);
        if (variants.isEmpty()) {
            return;
        }
        BlockPos placeAt = new BlockPos(wx, topY + 1, wz);
        Holder<ConfiguredFeature<?, ?>> feature = variants.get(rnd.nextInt(variants.size()));
        feature.value().place(level, generator, rnd, placeAt);
    }

    private static List<Holder<ConfiguredFeature<?, ?>>> sprinkleVariantsForSurface(
            HolderGetter<ConfiguredFeature<?, ?>> configured, BlockState surface) {
        if (surface.is(Blocks.GRASS_BLOCK)) {
            return List.of(
                    configured.getOrThrow(TreeFeatures.OAK),
                    configured.getOrThrow(TreeFeatures.FANCY_OAK),
                    configured.getOrThrow(TreeFeatures.BIRCH));
        }
        if (surface.is(Blocks.SNOW_BLOCK)) {
            return List.of(
                    configured.getOrThrow(TreeFeatures.SPRUCE),
                    configured.getOrThrow(TreeFeatures.PINE),
                    configured.getOrThrow(TreeFeatures.MEGA_SPRUCE),
                    configured.getOrThrow(TreeFeatures.MEGA_PINE));
        }
        if (surface.is(Blocks.MYCELIUM)) {
            return List.of(
                    configured.getOrThrow(TreeFeatures.HUGE_BROWN_MUSHROOM),
                    configured.getOrThrow(TreeFeatures.HUGE_RED_MUSHROOM));
        }
        if (surface.is(Blocks.SAND)) {
            return List.of(
                    configured.getOrThrow(TreeFeatures.ACACIA),
                    configured.getOrThrow(TreeFeatures.OAK));
        }
        return List.of();
    }

    /**
     * Removes structure blocks that sit in columns with no island, or hang in open air above the island top (unless
     * {@link #isSettlementStructure(ResourceLocation)}). {@link Config#FLOATING_ISLANDS_TRIM_STRIP_MINESHAFT_THROUGH_VOID}
     * gates whether {@code minecraft:mineshaft} pieces in void columns are cleared (default: keep corridors).
     */
    private static void trimFloatingStructureBlocks(RegistryAccess registryAccess, ChunkAccess chunk) {
        var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        ChunkPos cp = chunk.getPos();
        int minWX = cp.getMinBlockX();
        int maxWX = cp.getMaxBlockX();
        int minWZ = cp.getMinBlockZ();
        int maxWZ = cp.getMaxBlockZ();

        for (var entry : chunk.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            ResourceLocation sid = structureRegistry.getKey(structure);
            boolean trimAboveSurface = sid == null || !preservesStructureAboveSurfaceTrim(sid);

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
                            if (MINESHAFT.equals(sid)
                                    && !Config.FLOATING_ISLANDS_TRIM_STRIP_MINESHAFT_THROUGH_VOID.getAsBoolean()) {
                                continue;
                            }
                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                        } else if (trimAboveSurface && y > top + STRUCTURE_CLEARANCE_ABOVE_TOP) {
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

                Holder<Biome> biome = biomeForSurfaceColumn(randomState, wx, wz, minY, maxY, topY);
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

    /**
     * Limits {@code /locate structure} ring depth so the server thread does not synchronously generate unbounded chunks
     * (trial chambers and other jig saw-heavy starts can stall past the watchdog).
     */
    @Override
    @Nullable
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
            ServerLevel level,
            HolderSet<Structure> structure,
            BlockPos pos,
            int searchRadius,
            boolean skipKnownStructures) {
        int cap = Config.FLOATING_ISLANDS_LOCATE_STRUCTURE_MAX_RING_RADIUS.getAsInt();
        int clamped = Math.min(searchRadius, cap);
        return super.findNearestMapStructure(level, structure, pos, clamped, skipKnownStructures);
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
        ensureIslandCarvingNoiseDelegate(level.registryAccess());
        FloatingIslandMaskedCarvers.applyMaskedOverworldCarvers(
                this,
                level,
                seed,
                randomState,
                biomeManager,
                structureManager,
                chunk,
                step,
                islandCarvingNoiseDelegate,
                islandCarvingNoiseSettings);
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
        return Config.FLOATING_ISLANDS_CHUNK_GENERATOR_SEA_LEVEL.getAsInt();
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
        Holder<Biome> biome = biomeForSurfaceColumn(random, x, z, minY, maxY, topY);
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
        if (biome.is(Biomes.MUSHROOM_FIELDS)) {
            return Blocks.MYCELIUM.defaultBlockState();
        }
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
        if (surface.is(Blocks.SNOW_BLOCK) || surface.is(Blocks.MYCELIUM)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (surface.is(Blocks.SAND)) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        return Blocks.DIRT.defaultBlockState();
    }
}
