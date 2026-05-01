package net.projectisland.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.projectisland.Config;

/**
 * Decorative {@link Blocks#CHAIN} columns between a hanging rare structure’s roof and the procedural island underside.
 */
public final class FloatingIslandRareStructureChains {
    private static final ResourceLocation MONSTER_ROOM = ResourceLocation.withDefaultNamespace("monster_room");
    private static final ResourceLocation TRIAL_CHAMBERS = ResourceLocation.withDefaultNamespace("trial_chambers");
    private static final ResourceLocation MINESHAFT = ResourceLocation.withDefaultNamespace("mineshaft");

    private FloatingIslandRareStructureChains() {}

    public static void tryPlaceDecorativeChains(RegistryAccess registryAccess, ChunkAccess chunk) {
        if (!Config.FLOATING_ISLANDS_RARE_STRUCTURE_DECORATIVE_CHAINS.getAsBoolean()) {
            return;
        }
        int maxGap = Config.FLOATING_ISLANDS_RARE_STRUCTURE_CHAIN_MAX_GAP_BLOCKS.getAsInt();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        ChunkPos cp = chunk.getPos();
        int chunkMinX = cp.getMinBlockX();
        int chunkMinZ = cp.getMinBlockZ();
        var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

        for (var entry : chunk.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            ResourceLocation id = structureRegistry.getKey(structure);
            if (!isChainTarget(id)) {
                continue;
            }
            BoundingBox bb = start.getBoundingBox();
            int cx = (bb.minX() + bb.maxX()) >> 1;
            int cz = (bb.minZ() + bb.maxZ()) >> 1;
            if ((cx >> 4) != cp.x || (cz >> 4) != cp.z) {
                continue;
            }
            int lx = cx - chunkMinX;
            int lz = cz - chunkMinZ;

            int islandBottom = FloatingIslandLayout.columnBottomY(cx, cz, minY, maxY);
            if (islandBottom >= maxY) {
                continue;
            }
            int roof = bb.maxY();
            if (roof >= islandBottom - 1) {
                continue;
            }
            int gap = islandBottom - roof - 1;
            if (gap <= 0) {
                continue;
            }
            if (maxGap > 0 && gap > maxGap) {
                continue;
            }

            for (int y = roof + 1; y < islandBottom; y++) {
                if (y < minY || y >= maxY) {
                    break;
                }
                if (!FloatingIslandLayout.columnContains(cx, cz, y, minY, maxY)) {
                    break;
                }
                BlockPos pos = new BlockPos(lx, y, lz);
                if (chunk.getBlockState(pos).isAir()) {
                    chunk.setBlockState(pos, Blocks.CHAIN.defaultBlockState(), false);
                }
            }
        }
    }

    private static boolean isChainTarget(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        return MONSTER_ROOM.equals(id) || TRIAL_CHAMBERS.equals(id) || MINESHAFT.equals(id);
    }
}
