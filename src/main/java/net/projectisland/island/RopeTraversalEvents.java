package net.projectisland.island;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server hooks for rope surfing (tick + lifecycle + cancel on damage). */
public final class RopeTraversalEvents {
    private RopeTraversalEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RopeTraversalEvents::onPlayerTickPost);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onPlayerLoggedOut);
    }

    private static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        RopeSurfingState.tick(player, level);
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        RopeSurfingState.clear(sp);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        RopeSurfingState.clear(sp);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        RopeSurfingState.clear(sp);
    }

    private static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        RopeSurfingState.cancelOnDamage(sp);
    }
}
