package net.projectisland.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import net.projectisland.ProjectIsland;

/**
 * Server → client: show a short, wrapped “action bar” style message with a dim backdrop (vanilla action bar does not wrap).
 */
public record ActionBarToastPayload(String translationKey, List<String> stringArgs, int visibleTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ActionBarToastPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "action_bar_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActionBarToastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ActionBarToastPayload::translationKey,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8, 32),
            ActionBarToastPayload::stringArgs,
            ByteBufCodecs.VAR_INT,
            ActionBarToastPayload::visibleTicks,
            ActionBarToastPayload::new);

    /** ~5.5s at 20 TPS — general harpoon / claim toasts. */
    public static final int DEFAULT_VISIBLE_TICKS = 110;

    /** Longer read when the message explains a failed action (e.g. rolled-back first anchor). */
    public static final int LONG_READ_VISIBLE_TICKS = 180;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Translation key only, default duration. */
    public static void send(ServerPlayer player, String translationKey) {
        dispatch(player, translationKey, DEFAULT_VISIBLE_TICKS, new Object[0]);
    }

    /** Translation key only, explicit duration (avoids overload ambiguity with numeric format args). */
    public static void send(ServerPlayer player, String translationKey, int visibleTicks) {
        dispatch(player, translationKey, visibleTicks, new Object[0]);
    }

    /** Key + format args, default duration. */
    public static void sendWithArgs(ServerPlayer player, String translationKey, Object... args) {
        dispatch(player, translationKey, DEFAULT_VISIBLE_TICKS, args);
    }

    /** Key + explicit duration + format args (separate name so ints are never mistaken for ticks). */
    public static void sendForDuration(ServerPlayer player, String translationKey, int visibleTicks, Object... args) {
        dispatch(player, translationKey, visibleTicks, args);
    }

    private static void dispatch(ServerPlayer player, String translationKey, int visibleTicks, Object[] args) {
        List<String> strings = Arrays.stream(args).map(String::valueOf).toList();
        int ticks = Mth.clamp(visibleTicks, 20, 400);
        PacketDistributor.sendToPlayer(player, new ActionBarToastPayload(translationKey, strings, ticks));
    }
}
