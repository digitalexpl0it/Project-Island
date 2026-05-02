package net.projectisland.island;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.projectisland.Config;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.network.IslandHudSyncPayload;
import net.projectisland.network.IslandHudSyncPayload.IslandHudBeacon;
import net.projectisland.worldgen.FloatingIslandLayout;

public final class IslandHudServerSync {
    private IslandHudServerSync() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(IslandHudServerSync::onServerTickPost);
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
            ServerLevel level = player.serverLevel();
            if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
                continue;
            }
            if (!Config.ISLAND_HUD_SYNC_ENABLED.getAsBoolean()) {
                PacketDistributor.sendToPlayer(player, new IslandHudSyncPayload(List.of()));
                continue;
            }
            List<IslandHudBeacon> beacons = buildBeacons(player, level);
            PacketDistributor.sendToPlayer(player, new IslandHudSyncPayload(beacons));
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
            return List.of(createBeacon(params, rx, rz, heightAbovePeak));
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
                out.add(createBeacon(params, rx, rz, heightAbovePeak));
            }
        }
        return out;
    }

    private static IslandHudBeacon createBeacon(FloatingIslandLayout.IslandParams params, int rx, int rz, int heightAbovePeak) {
        FloatingIslandLayout.regionIsland(rx, rz, params);
        float x = params.centerX + 0.5f;
        float z = params.centerZ + 0.5f;
        int peak = FloatingIslandLayout.peakSurfaceYAtIslandCenter(params);
        float y = peak + heightAbovePeak;
        String title = FloatingIslandDisplayName.forRegion(rx, rz);
        return new IslandHudBeacon(x, y, z, title);
    }
}
