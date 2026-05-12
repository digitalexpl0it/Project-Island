package net.projectisland.island;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.worldgen.FloatingIslandLayout;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * Places one loot chest per starter island region (shared hub or per-player), near the procedural island center but
 * offset from the usual spawn column. Uses {@link RandomizableContainerBlockEntity} + {@link #STARTER_SUPPLY_LOOT}; when
 * <strong>Lootr</strong> is installed it can convert the chest on interaction per its config. Placement position is stored in
 * {@link FloatingIslandSavedData} so the block resists explosions and survival breaks (creative instabuild can still remove it).
 */
public final class StarterIslandSupplyChest {
    public static final ResourceKey<LootTable> STARTER_SUPPLY_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "chests/starter_supply"));

    private StarterIslandSupplyChest() {}

    /**
     * {@code true} when this position must not be broken or destroyed by explosions on the floating overworld: unopened
     * chest still bound to {@link #STARTER_SUPPLY_LOOT}, or a persisted starter column that is still a vanilla {@link ChestBlock}.
     */
    public static boolean isSupplyChestProtectedFromRemoval(ServerLevel level, BlockPos pos) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return false;
        }
        if (hasStarterLootTable(level, pos)) {
            return true;
        }
        if (!IslandWorld.get(level).isStarterSupplyChestPackedPos(pos.asLong())) {
            return false;
        }
        return level.getBlockState(pos).getBlock() instanceof ChestBlock;
    }

    private static boolean hasStarterLootTable(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity lootable)) {
            return false;
        }
        return lootable.getLootTable() != null && lootable.getLootTable().equals(STARTER_SUPPLY_LOOT);
    }

    public static void placeIfNeeded(ServerLevel level, FloatingIslandSavedData data, FloatingIslandKey island) {
        if (!Config.STARTER_ISLAND_SUPPLY_CHEST_ENABLED.getAsBoolean()) {
            return;
        }
        if (data.hasStarterSupplyChest(island)) {
            return;
        }
        Optional<BlockPos> posOpt = findChestPos(level, island);
        if (posOpt.isEmpty()) {
            ProjectIsland.LOGGER.warn(
                    "StarterIslandSupplyChest: no surface spot for starter supply chest on island {}", island);
            return;
        }
        BlockPos pos = posOpt.get();
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, pos.getX(), pos.getZ(), 4);
        Direction facing = Direction.from2DDataValue(level.getRandom().nextInt(4));
        BlockState chestState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, facing)
                .setValue(ChestBlock.TYPE, ChestType.SINGLE);
        if (!level.getBlockState(pos).canBeReplaced()) {
            return;
        }
        if (!level.setBlock(pos, chestState, 3)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity lootable)) {
            level.removeBlock(pos, false);
            return;
        }
        long seed = level.getRandom().nextLong();
        lootable.setLootTable(STARTER_SUPPLY_LOOT, seed);
        data.addStarterSupplyChest(island, pos);
    }

    private static Optional<BlockPos> findChestPos(ServerLevel level, FloatingIslandKey island) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        FloatingIslandLayout.IslandParams params = new FloatingIslandLayout.IslandParams();
        FloatingIslandLayout.regionIsland(island.regionX(), island.regionZ(), params);
        int cx = params.centerX;
        int cz = params.centerZ;
        // Offset from (0,0) ring so we rarely collide with the first spawn column from findOpenFeetNear.
        for (int ring = 2; ring <= 12; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int wx = cx + dx;
                    int wz = cz + dz;
                    int top = FloatingIslandsChunkGenerator.islandSurfaceBlockY(gen, wx, wz, minY, maxY);
                    if (top == Integer.MIN_VALUE) {
                        continue;
                    }
                    BlockPos chest = new BlockPos(wx, top + 1, wz);
                    if (goodChestSite(level, chest)) {
                        return Optional.of(chest);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean goodChestSite(ServerLevel level, BlockPos chest) {
        if (!level.getBlockState(chest).canBeReplaced()) {
            return false;
        }
        BlockPos above = chest.above();
        if (!level.getBlockState(above).canBeReplaced()) {
            return false;
        }
        BlockPos below = chest.below();
        BlockState floor = level.getBlockState(below);
        return floor.isFaceSturdy(level, below, Direction.UP);
    }
}
