package net.projectisland.island;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.projectisland.Config;
import net.projectisland.compat.WaystoneActivatedIslandHitsMerge;
import net.projectisland.compat.WaystoneIslandHudTitle;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.network.IslandHudSyncPayload;
import net.projectisland.network.IslandHudSyncPayload.IslandHudBeacon;
import net.projectisland.worldgen.FloatingIslandLayout;

public final class IslandHudServerSync {
    private IslandHudServerSync() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(IslandHudServerSync::onServerTickPost);
        // After Waystones (and other mods) update saved waystone state on use.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, IslandHudServerSync::onRightClickBlock);
    }

    /**
     * Bust waystone title cache when a waystone block is used, then push HUD immediately so the client does not wait
     * for {@link Config#ISLAND_HUD_SYNC_INTERVAL_TICKS}.
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (be == null || !WaystoneIslandHudTitle.isWaystoneBlockEntity(be)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        /*
         * NeoForge's RightClickBlock can run before the block's use() path (where Waystones calls
         * PlayerWaystoneManager.activateWaystone and assigns the generated name). Syncing in the same call stack
         * used to cache an empty title for islandHudWaystoneTitleCacheTicks. Run invalidate + HUD push on the server
         * task queue so it executes after Waystones has updated the WaystoneImpl in WaystoneManagerImpl.
         */
        MinecraftServer server = level.getServer();
        server.execute(() -> {
            IslandWorld.markWaystoneHitsForHudSync(level, sp.getUUID(), event.getPos(), sp.blockPosition());
            if (Config.ISLAND_HUD_WAYSTONE_TITLE_WHEN_LOADED.getAsBoolean()) {
                WaystoneIslandHudTitle.invalidateCache();
            }
            syncHudPayloadToPlayer(sp);
        });
    }

    private static void syncHudPayloadToPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        /*
         * Waystones is the source of truth for “have I ever activated this stone?” Re-merge each HUD tick so reconnect
         * and edge cases repopulate island hit keys even if our saved map drifted (Xaero gold / persistence follows).
         */
        WaystoneActivatedIslandHitsMerge.tryMergeActivatedWaystones(player, level);
        if (!Config.ISLAND_HUD_SYNC_ENABLED.getAsBoolean()) {
            PacketDistributor.sendToPlayer(player, new IslandHudSyncPayload(List.of(), List.of()));
            return;
        }
        List<Long> visited = IslandWorld.get(level).copyWaystoneIslandHits(player.getUUID());
        List<IslandHudBeacon> built = buildBeacons(player, level);
        /*
         * Merge Waystone visits into the beacon list only in void/navigation modes (0 or many scan beacons). On solid
         * island surface, {@link #buildBeacons} returns exactly one beacon — merging every visited region would balloon
         * the payload and force Xaero through the multi-beacon upsert path every tick (waypoint/minimap clutter).
         */
        List<IslandHudBeacon> beacons =
                built.size() == 1 ? built : mergeVisitedRegionsIntoBeacons(level, built, visited);
        PacketDistributor.sendToPlayer(player, new IslandHudSyncPayload(beacons, visited));
    }

    /**
     * Drive HUD sync from the server tick so it does not depend on {@code PlayerTickEvent} delivery quirks.
     * Still throttled per player with {@link Config#ISLAND_HUD_SYNC_INTERVAL_TICKS}.
     */
    private static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int interval = Math.max(1, Config.ISLAND_HUD_SYNC_INTERVAL_TICKS.getAsInt());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.tickCount % interval != 0) {
                continue;
            }
            syncHudPayloadToPlayer(player);
        }
    }

    /**
     * When the player's column has procedural surface ({@link FloatingIslandLayout#islandOwningSurface}), only the
     * **winning** region's beacon is sent — wide merges can span several grid cells, so neighbor-suppression alone
     * still doubled HUDs. In open void ({@code islandOwningSurface} empty), all islands out to the scan radius are listed
     * for navigation.
     */
    private static List<IslandHudBeacon> buildBeacons(ServerPlayer player, ServerLevel level) {
        BlockPos feet = player.blockPosition();
        int pcx = Mth.floorDiv(feet.getX(), 16);
        int pcz = Mth.floorDiv(feet.getZ(), 16);
        int rcx = Mth.floorDiv(pcx, FloatingIslandLayout.REGION_CHUNKS);
        int rcz = Mth.floorDiv(pcz, FloatingIslandLayout.REGION_CHUNKS);

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        Optional<FloatingIslandKey> surfaceOwner =
                FloatingIslandLayout.islandOwningSurface(feet.getX(), feet.getZ(), minY, maxY);

        FloatingIslandLayout.IslandParams params = new FloatingIslandLayout.IslandParams();
        int heightAbovePeak = Config.ISLAND_HUD_HEIGHT_ABOVE_PEAK_BLOCKS.getAsInt();

        if (surfaceOwner.isPresent()) {
            FloatingIslandKey owner = surfaceOwner.get();
            int rx = owner.regionX();
            int rz = owner.regionZ();
            if (!FloatingIslandLayout.regionHasIsland(rx, rz)) {
                return List.of();
            }
            return List.of(createBeacon(level, params, rx, rz, heightAbovePeak, minY, maxY));
        }

        List<IslandHudBeacon> out = new ArrayList<>();
        int radius = Config.ISLAND_HUD_REGION_SCAN_RADIUS.getAsInt();
        for (int drx = -radius; drx <= radius; drx++) {
            for (int drz = -radius; drz <= radius; drz++) {
                int rx = rcx + drx;
                int rz = rcz + drz;
                if (!FloatingIslandLayout.regionHasIsland(rx, rz)) {
                    continue;
                }
                out.add(createBeacon(level, params, rx, rz, heightAbovePeak, minY, maxY));
            }
        }
        return out;
    }

    /**
     * Ensures every Waystone-visited procedural region has a beacon in the payload when not in the single-surface
     * {@linkplain #buildBeacons(ServerPlayer, ServerLevel) one-beacon} case. Called from void/empty/multi-scan paths only;
     * skipping merge on exactly-one beacon avoids flooding Xaero with every visited island while standing on an island.
     */
    private static List<IslandHudBeacon> mergeVisitedRegionsIntoBeacons(
            ServerLevel level, List<IslandHudBeacon> positionBeacons, List<Long> visitedPackedKeys) {
        if (visitedPackedKeys.isEmpty()) {
            return positionBeacons;
        }
        Map<Long, IslandHudBeacon> byPackedKey = new LinkedHashMap<>();
        for (IslandHudBeacon b : positionBeacons) {
            byPackedKey.put(packIslandRegionKey(b.regionX(), b.regionZ()), b);
        }
        FloatingIslandLayout.IslandParams params = new FloatingIslandLayout.IslandParams();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int heightAbovePeak = Config.ISLAND_HUD_HEIGHT_ABOVE_PEAK_BLOCKS.getAsInt();
        for (Long pk : visitedPackedKeys) {
            int rx = (int) (pk >> 32);
            int rz = (int) (long) pk;
            if (!FloatingIslandLayout.regionHasIsland(rx, rz)) {
                continue;
            }
            long key = packIslandRegionKey(rx, rz);
            if (!byPackedKey.containsKey(key)) {
                byPackedKey.put(key, createBeacon(level, params, rx, rz, heightAbovePeak, minY, maxY));
            }
        }
        return List.copyOf(byPackedKey.values());
    }

    private static long packIslandRegionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xffffffffL);
    }

    private static IslandHudBeacon createBeacon(
            ServerLevel level,
            FloatingIslandLayout.IslandParams params,
            int rx,
            int rz,
            int heightAbovePeak,
            int minY,
            int maxY) {
        FloatingIslandLayout.regionIsland(rx, rz, params);
        float x = params.centerX + 0.5f;
        float z = params.centerZ + 0.5f;
        int peak = FloatingIslandLayout.peakSurfaceYAtIslandCenter(params);
        float y = peak + heightAbovePeak;
        String procedural = FloatingIslandDisplayName.forRegion(rx, rz);
        String title = WaystoneIslandHudTitle.resolve(level, rx, rz, params, minY, maxY).orElse(procedural);
        return new IslandHudBeacon(x, y, z, title, rx, rz);
    }
}
