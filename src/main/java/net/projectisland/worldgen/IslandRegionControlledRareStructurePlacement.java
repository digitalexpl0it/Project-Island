package net.projectisland.worldgen;

import java.util.ArrayList;

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
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.projectisland.Config;

/**
 * Optional strip-and-regenerate pass for {@code minecraft:monster_room} and {@code minecraft:trial_chambers}, mirroring
 * {@link IslandRegionControlledSettlementPlacement} timing ({@code STRUCTURE_STARTS}).
 */
public final class IslandRegionControlledRareStructurePlacement {
    /** Must match {@link FloatingIslandsChunkGenerator} rare-structure roll salt. */
    private static final int SALT_REGION_RARE_STRUCTURE_ROLL = 771_977;

    private static final int SALT_CONTROLLED_RARE_PLACE_TRY = 662_911;

    private static final ResourceLocation MONSTER_ROOM = ResourceLocation.withDefaultNamespace("monster_room");
    private static final ResourceLocation TRIAL_CHAMBERS = ResourceLocation.withDefaultNamespace("trial_chambers");

    private IslandRegionControlledRareStructurePlacement() {}

    private static RandomSource regionRandom(long levelSeed, int rcx, int rcz, int salt) {
        return RandomSource.create(Mth.getSeed(rcx, salt, rcz) ^ levelSeed ^ (levelSeed >>> 32));
    }

    public static void stripVanillaRareDungeonStarts(RegistryAccess registryAccess, ChunkAccess chunk) {
        var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        for (var entry : new ArrayList<>(chunk.getAllStarts().entrySet())) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            ResourceLocation id = structureRegistry.getKey(structure);
            if (!MONSTER_ROOM.equals(id) && !TRIAL_CHAMBERS.equals(id)) {
                continue;
            }
            FloatingIslandsChunkGenerator.wipeStructureBlocksInChunk(chunk, start.getBoundingBox());
            chunk.setStartForStructure(structure, StructureStart.INVALID_START);
        }
    }

    public static void tryPlaceControlledRareDungeon(
            FloatingIslandsChunkGenerator generator,
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager) {
        if (!Config.FLOATING_ISLANDS_CONTROLLED_RARE_DUNGEON_PLACEMENT.getAsBoolean()) {
            return;
        }
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

        RandomSource rareRnd = regionRandom(levelSeed, rcx, rcz, SALT_REGION_RARE_STRUCTURE_ROLL);
        IslandRegionRareStructureSlot rare = IslandRegionStructurePicker.rollRare(rareRnd);
        ResourceKey<Structure> structureKey;
        if (rare == IslandRegionRareStructureSlot.MONSTER_ROOM) {
            structureKey = ResourceKey.create(Registries.STRUCTURE, MONSTER_ROOM);
        } else if (rare == IslandRegionRareStructureSlot.TRIAL_CHAMBERS) {
            structureKey = ResourceKey.create(Registries.STRUCTURE, TRIAL_CHAMBERS);
        } else {
            return;
        }

        double placeChance = Config.CONTROLLED_RARE_DUNGEON_PLACE_TRY_CHANCE.getAsDouble();
        if (placeChance <= 0.0d) {
            return;
        }
        if (placeChance < 1.0d) {
            RandomSource placeTryRnd = regionRandom(levelSeed, rcx, rcz, SALT_CONTROLLED_RARE_PLACE_TRY);
            if (placeTryRnd.nextDouble() > placeChance) {
                return;
            }
        }

        Structure structure = registryAccess.lookupOrThrow(Registries.STRUCTURE).getOrThrow(structureKey).value();

        StructureStart start = structure.generate(
                registryAccess,
                generator,
                generator.getBiomeSource(),
                randomState,
                structureTemplateManager,
                levelSeed,
                cpos,
                0,
                chunk,
                IslandRegionControlledRareStructurePlacement::allowAnyBiome);

        if (!start.isValid()) {
            return;
        }
        structureManager.setStartForStructure(SectionPos.bottomOf(chunk), structure, start, chunk);
    }

    private static boolean allowAnyBiome(Holder<Biome> biome) {
        return true;
    }
}
