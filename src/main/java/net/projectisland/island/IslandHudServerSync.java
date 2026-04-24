package net.projectisland.island;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
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
        NeoForge.EVENT_BUS.addListener(IslandHudServerSync::onPlayerPostTick);
    }

    private static void onPlayerPostTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        int interval = Math.max(1, Config.ISLAND_HUD_SYNC_INTERVAL_TICKS.getAsInt());
        if (player.tickCount % interval != 0) {
            return;
        }
        if (!Config.ISLAND_HUD_SYNC_ENABLED.getAsBoolean()) {
            PacketDistributor.sendToPlayer(player, new IslandHudSyncPayload(List.of()));
            return;
        }
        List<IslandHudBeacon> beacons = buildBeacons(player, level);
        PacketDistributor.sendToPlayer(player, new IslandHudSyncPayload(beacons));
    }

    private static List<IslandHudBeacon> buildBeacons(ServerPlayer player, ServerLevel level) {
        MinecraftServer server = player.getServer();
        BlockPos feet = player.blockPosition();
        int pcx = feet.getX() >> 4;
        int pcz = feet.getZ() >> 4;
        int rcx = Mth.floorDiv(pcx, FloatingIslandLayout.REGION_CHUNKS);
        int rcz = Mth.floorDiv(pcz, FloatingIslandLayout.REGION_CHUNKS);

        FloatingIslandSavedData data = IslandWorld.get(level);
        FloatingIslandLayout.IslandParams params = new FloatingIslandLayout.IslandParams();
        List<IslandHudBeacon> out = new ArrayList<>();
        int radius = Config.ISLAND_HUD_REGION_SCAN_RADIUS.getAsInt();
        int heightAbovePeak = Config.ISLAND_HUD_HEIGHT_ABOVE_PEAK_BLOCKS.getAsInt();

        for (int drx = -radius; drx <= radius; drx++) {
            for (int drz = -radius; drz <= radius; drz++) {
                int rx = rcx + drx;
                int rz = rcz + drz;
                if (!FloatingIslandLayout.regionHasIsland(rx, rz)) {
                    continue;
                }
                FloatingIslandKey key = new FloatingIslandKey(rx, rz);
                IslandRecord rec = data.peek(key).orElse(null);
                IslandState state = rec == null ? IslandState.AVAILABLE : rec.state();
                FloatingIslandLayout.regionIsland(rx, rz, params);
                float x = params.centerX + 0.5f;
                float z = params.centerZ + 0.5f;
                int peak = FloatingIslandLayout.peakSurfaceYAtIslandCenter(params);
                float y = peak + heightAbovePeak;

                String title = FloatingIslandDisplayName.forRegion(rx, rz);
                String idKey = key.toStorageKey();
                String status;
                int titleColor;
                int statusColor;
                int stateKind = state.ordinal();
                switch (state) {
                    case AVAILABLE:
                        status = "Available";
                        titleColor = 0xFFB8FFC8;
                        statusColor = 0xFFE8FFF0;
                        break;
                    case CLAIMED:
                        UUID owner = rec != null ? rec.owner() : null;
                        if (owner != null && server != null) {
                            String name = server.getProfileCache()
                                    .get(owner)
                                    .map(GameProfile::getName)
                                    .orElseGet(() -> owner.toString().substring(0, 8) + "…");
                            status = "Claimed · " + name;
                        } else {
                            status = "Claimed";
                        }
                        titleColor = 0xFFFFE8A0;
                        statusColor = 0xFFFFF4D8;
                        break;
                    case CONTESTED:
                        status = "Contested";
                        titleColor = 0xFFFF9A9A;
                        statusColor = 0xFFFFE0E0;
                        break;
                    default:
                        status = "?";
                        titleColor = 0xFFFFFFFF;
                        statusColor = 0xFFE0E0E0;
                        break;
                }
                out.add(new IslandHudBeacon(x, y, z, title, status, idKey, titleColor, statusColor, stateKind));
            }
        }
        return out;
    }
}
