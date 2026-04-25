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
import net.projectisland.client.RopeLinkClientCache;

public record RopeLinkSyncPayload(List<RopeLinkSegment> segments) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RopeLinkSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "rope_link_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RopeLinkSegment> SEGMENT_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeLong(s.fromPacked());
                buf.writeLong(s.toPacked());
            },
            buf -> new RopeLinkSegment(buf.readLong(), buf.readLong()));

    public static final StreamCodec<RegistryFriendlyByteBuf, RopeLinkSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, SEGMENT_CODEC, 512),
            RopeLinkSyncPayload::segments,
            RopeLinkSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(RopeLinkSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RopeLinkClientCache.replace(payload.segments()));
    }

    /** Packed {@link net.minecraft.core.BlockPos#asLong()} endpoints. */
    public record RopeLinkSegment(long fromPacked, long toPacked) {}
}
