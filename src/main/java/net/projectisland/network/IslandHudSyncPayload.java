package net.projectisland.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.projectisland.ProjectIsland;
import net.projectisland.client.IslandHudClientCache;

public record IslandHudSyncPayload(List<IslandHudBeacon> beacons) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<IslandHudSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "island_hud_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IslandHudBeacon> BEACON_STREAM_CODEC =
            StreamCodec.of(IslandHudSyncPayload::encodeBeacon, IslandHudSyncPayload::decodeBeacon);

    public static final StreamCodec<RegistryFriendlyByteBuf, IslandHudSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, BEACON_STREAM_CODEC, 256),
            IslandHudSyncPayload::beacons,
            IslandHudSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(IslandHudSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> IslandHudClientCache.replace(payload.beacons()));
    }

    private static void encodeBeacon(RegistryFriendlyByteBuf buf, IslandHudBeacon b) {
        buf.writeFloat(b.x());
        buf.writeFloat(b.y());
        buf.writeFloat(b.z());
        buf.writeUtf(b.title());
    }

    private static IslandHudBeacon decodeBeacon(RegistryFriendlyByteBuf buf) {
        return new IslandHudBeacon(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf());
    }

    /** World-space anchor and procedural island display name only. */
    public record IslandHudBeacon(float x, float y, float z, String title) {}
}
