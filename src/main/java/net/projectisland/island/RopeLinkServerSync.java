package net.projectisland.island;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.projectisland.Config;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.network.RopeLinkSyncPayload;
import net.projectisland.network.RopeLinkSyncPayload.RopeLinkSegment;

public final class RopeLinkServerSync {
    private RopeLinkServerSync() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RopeLinkServerSync::onServerTickPost);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        RopeLinkStress.tick(server);
        RopeLinkProgressionUpgrade.tick(server);
        int interval = Math.max(1, Config.ROPE_LINK_SYNC_INTERVAL_TICKS.getAsInt());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.tickCount % interval != 0) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
                continue;
            }
            if (!Config.ROPE_LINK_SYNC_ENABLED.getAsBoolean()) {
                PacketDistributor.sendToPlayer(player, new RopeLinkSyncPayload(List.of()));
                continue;
            }
            List<RopeLinkSegment> segments = buildSegmentsForPlayer(player, IslandWorld.get(level));
            PacketDistributor.sendToPlayer(player, new RopeLinkSyncPayload(segments));
        }
    }

    private static List<RopeLinkSegment> buildSegmentsForPlayer(ServerPlayer player, FloatingIslandSavedData data) {
        int radius = Math.max(1, Config.ROPE_LINK_SYNC_CULL_RADIUS_BLOCKS.getAsInt());
        BlockPos feet = player.blockPosition();
        int px = feet.getX();
        int py = feet.getY();
        int pz = feet.getZ();
        List<RopeLinkSegment> out = new ArrayList<>();
        for (RopeLink link : data.copyRopeLinks()) {
            if (segmentNearPlayer(link.fromAnchorPos(), link.toAnchorPos(), px, py, pz, radius)) {
                out.add(new RopeLinkSegment(
                        link.fromAnchorPos().asLong(), link.toAnchorPos().asLong(), link.healthFraction()));
            }
        }
        return out;
    }

    /**
     * True if either anchor or the segment midpoint is within {@code radius} blocks of the player on XZ (Chebyshev)
     * and within {@code radius} on Y from the player feet.
     */
    private static boolean segmentNearPlayer(BlockPos a, BlockPos b, int px, int py, int pz, int radius) {
        int midX = (a.getX() + b.getX()) / 2;
        int midZ = (a.getZ() + b.getZ()) / 2;
        int midY = (a.getY() + b.getY()) / 2;
        if (chebAndY(px, py, pz, midX, midY, midZ, radius)) {
            return true;
        }
        if (chebAndY(px, py, pz, a.getX(), a.getY(), a.getZ(), radius)) {
            return true;
        }
        return chebAndY(px, py, pz, b.getX(), b.getY(), b.getZ(), radius);
    }

    private static boolean chebAndY(int px, int py, int pz, int x, int y, int z, int radius) {
        int dx = Math.abs(x - px);
        int dz = Math.abs(z - pz);
        if (Math.max(dx, dz) > radius) {
            return false;
        }
        return Math.abs(y - py) <= radius;
    }
}
