package net.projectisland.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.projectisland.ProjectIsland;

/**
 * Syncs Ender Dragon scheduled-respawn countdown to clients ({@code respawnTargetGameTime} is {@link
 * net.minecraft.world.level.Level#getGameTime()} when the respawn should run).
 */
public record DragonCountdownSyncPayload(boolean countdownActive, long respawnTargetGameTime)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DragonCountdownSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "dragon_countdown_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DragonCountdownSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    DragonCountdownSyncPayload::countdownActive,
                    ByteBufCodecs.VAR_LONG,
                    DragonCountdownSyncPayload::respawnTargetGameTime,
                    DragonCountdownSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientbound(DragonCountdownSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class.forName("net.projectisland.client.DragonCountdownOverlay")
                        .getMethod("updateFromNetwork", boolean.class, long.class)
                        .invoke(null, payload.countdownActive(), payload.respawnTargetGameTime());
            } catch (Throwable t) {
                ProjectIsland.LOGGER.warn("Project Island: dragon countdown client dispatch failed", t);
            }
        });
    }
}
