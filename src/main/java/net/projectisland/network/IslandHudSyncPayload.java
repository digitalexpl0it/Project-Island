package net.projectisland.network;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.client.IslandHudClientCache;

/**
 * @param billboardBeacons    World HUD / navigation ring only — {@linkplain net.projectisland.client.IslandHudRenderer}
 *                            uses this list (one beacon on island surface, scan ring in void).
 * @param waypointSyncBeacons Minimap / Xaero mirror — always includes merged Waystone visits (see
 *                            {@link net.projectisland.island.IslandHudServerSync}) so pins update on island, not only in void.
 */
public record IslandHudSyncPayload(
        List<IslandHudBeacon> billboardBeacons,
        List<IslandHudBeacon> waypointSyncBeacons,
        List<Long> waystoneVisitedRegionKeys)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<IslandHudSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "island_hud_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IslandHudBeacon> BEACON_STREAM_CODEC =
            StreamCodec.of(IslandHudSyncPayload::encodeBeacon, IslandHudSyncPayload::decodeBeacon);

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> VISITED_REGION_KEY_CODEC =
            StreamCodec.of((buf, v) -> buf.writeVarLong(v), RegistryFriendlyByteBuf::readVarLong);

    private static final StreamCodec<RegistryFriendlyByteBuf, List<IslandHudBeacon>> BEACON_LIST_CODEC =
            ByteBufCodecs.collection(ArrayList::new, BEACON_STREAM_CODEC, 512);

    public static final StreamCodec<RegistryFriendlyByteBuf, IslandHudSyncPayload> STREAM_CODEC = StreamCodec.composite(
            BEACON_LIST_CODEC,
            IslandHudSyncPayload::billboardBeacons,
            BEACON_LIST_CODEC,
            IslandHudSyncPayload::waypointSyncBeacons,
            ByteBufCodecs.collection(ArrayList::new, VISITED_REGION_KEY_CODEC, 512),
            IslandHudSyncPayload::waystoneVisitedRegionKeys,
            IslandHudSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(IslandHudSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            IslandHudClientCache.replace(payload.billboardBeacons(), payload.waystoneVisitedRegionKeys());
            if (FMLEnvironment.dist == Dist.CLIENT) {
                try {
                    Class.forName("net.projectisland.client.compat.XaeroIslandWaypointSync")
                            .getMethod("onHudBeacons", List.class, List.class)
                            .invoke(null, payload.waypointSyncBeacons(), payload.waystoneVisitedRegionKeys());
                } catch (Throwable t) {
                    if (Config.DEBUG_LOGGING.getAsBoolean()) {
                        ProjectIsland.LOGGER.debug("Island HUD Xaero waypoint sync hook failed", t);
                    }
                }
            }
        });
    }

    private static void encodeBeacon(RegistryFriendlyByteBuf buf, IslandHudBeacon b) {
        buf.writeFloat(b.x());
        buf.writeFloat(b.y());
        buf.writeFloat(b.z());
        buf.writeUtf(b.title());
        buf.writeVarInt(b.regionX());
        buf.writeVarInt(b.regionZ());
    }

    private static IslandHudBeacon decodeBeacon(RegistryFriendlyByteBuf buf) {
        return new IslandHudBeacon(
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
    }

    /**
     * World-space anchor, display title, and authoritative procedural {@linkplain net.projectisland.island.FloatingIslandKey
     * region} indices (for client island identity — avoids region mismatches from beacon float coords alone).
     */
    public record IslandHudBeacon(float x, float y, float z, String title, int regionX, int regionZ) {}
}
