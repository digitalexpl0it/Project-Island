package net.projectisland;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.projectisland.island.FloatingIslandStarterPlacement;
import net.projectisland.island.FloatingIslandVoidRescue;

/**
 * When a player is in the floating-islands overworld and their feet are over void (first join, bad spawn, or
 * fixed teleport Y), move them onto the nearest procedural island column. Mid-void {@linkplain net.projectisland.island.FloatingIslandVoidRescue
 * last-safe snap} plus floor-band rescue reduce long falls that can trigger vanilla “flying” kicks with {@code allow-flight=false}.
 */
public final class FloatingIslandsSpawnEvents {
    private FloatingIslandsSpawnEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnEvents::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnEvents::onPlayerTickPre);
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FloatingIslandVoidRescue.clearLastSafeFeet(player);
        }
    }

    private static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FloatingIslandVoidRescue.tickVoidRescue(player, player.serverLevel());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (FloatingIslandStarterPlacement.handlePlayerLoggedIn(player, level)) {
            return;
        }
        FloatingIslandVoidRescue.relocatePlayerFromVoid(player, level);
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FloatingIslandVoidRescue.clearLastSafeFeet(player);
        if (!event.getTo().equals(Level.OVERWORLD)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }
        FloatingIslandVoidRescue.relocatePlayerFromVoid(player, level);
    }
}
