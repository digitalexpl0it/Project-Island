package net.projectisland.end;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.network.ActionBarToastPayload;
import net.projectisland.network.DragonCountdownSyncPayload;

/**
 * Scheduled Ender Dragon respawn: witnesses are players in the End when the dragon dies; countdown starts once none
 * of them remain in the End, then runs for {@link Config#DRAGON_RESET_DELAY_TICKS}.
 */
public final class DragonResetHandler {
    private DragonResetHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DragonResetHandler::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(DragonResetHandler::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(DragonResetHandler::onPlayerLoggedIn);
    }

    private static DragonResetSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DragonResetSavedData.FACTORY, DragonResetSavedData.FILE_ID);
    }

    private static void broadcastCountdown(MinecraftServer server) {
        DragonResetSavedData d = data(server);
        boolean active = d.state() == DragonResetSavedData.STATE_COUNTDOWN;
        long target = active ? d.targetGameTime() : 0L;
        DragonCountdownSyncPayload payload = new DragonCountdownSyncPayload(active, target);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

    private static void sendCountdownTo(ServerPlayer player) {
        DragonResetSavedData d = data(player.getServer());
        boolean active = d.state() == DragonResetSavedData.STATE_COUNTDOWN;
        long target = active ? d.targetGameTime() : 0L;
        PacketDistributor.sendToPlayer(player, new DragonCountdownSyncPayload(active, target));
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.getServer() == null) {
            return;
        }
        sendCountdownTo(sp);
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon) || !(event.getEntity().level() instanceof ServerLevel end)) {
            return;
        }
        if (end.dimension() != Level.END || !Config.DRAGON_RESET_ENABLED.getAsBoolean()) {
            return;
        }
        MinecraftServer server = end.getServer();
        Set<UUID> witnesses = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() == Level.END && p.isAlive() && !p.isSpectator()) {
                witnesses.add(p.getUUID());
            }
        }
        DragonResetSavedData d = data(server);
        d.resetToIdle();
        d.setWitnesses(witnesses);
        d.setState(DragonResetSavedData.STATE_AWAIT_LEAVE_END);
        broadcastCountdown(server);
        ProjectIsland.LOGGER.info(
                "Project Island: Ender Dragon defeated — {} witness(es) in the End; respawn arms when they all leave.",
                witnesses.size());
    }

    private static boolean anyWitnessInEnd(ServerLevel end, DragonResetSavedData d) {
        MinecraftServer server = end.getServer();
        for (UUID id : d.witnessesView()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.level().dimension() == Level.END) {
                return true;
            }
        }
        return false;
    }

    private static void startCountdown(MinecraftServer server, DragonResetSavedData d, long now) {
        int delay = Config.DRAGON_RESET_DELAY_TICKS.getAsInt();
        d.setState(DragonResetSavedData.STATE_COUNTDOWN);
        d.setTargetGameTime(now + delay);
        broadcastCountdown(server);
        if (Config.DRAGON_RESET_PLAY_SOUNDS.getAsBoolean()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.35f, 1.2f);
            }
        }
        ProjectIsland.LOGGER.info(
                "Project Island: Ender Dragon respawn countdown started — {} ticks (game time target {}).",
                delay,
                d.targetGameTime());
    }

    private static void performRespawn(MinecraftServer server, ServerLevel end) {
        EndDragonFight fight = end.getDragonFight();
        if (fight == null) {
            ProjectIsland.LOGGER.warn("Project Island: End has no dragon fight — cannot respawn dragon.");
            return;
        }
        List<EnderDragon> existing = new ArrayList<>(end.getDragons());
        for (EnderDragon dragon : existing) {
            dragon.discard();
        }
        fight.resetSpikeCrystals();
        fight.respawnDragon(new ArrayList<>());
        DragonBossBarSupport.rearmAfterRespawn(end);
        if (Config.DRAGON_RESET_PLAY_SOUNDS.getAsBoolean()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.9f, 0.75f);
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ActionBarToastPayload.sendForDuration(p, "projectisland.dragon.respawned", ActionBarToastPayload.LONG_READ_VISIBLE_TICKS);
        }
        ProjectIsland.LOGGER.info("Project Island: Ender Dragon respawn executed.");
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!server.isReady()) {
            return;
        }
        DragonResetSavedData d = data(server);
        if (!Config.DRAGON_RESET_ENABLED.getAsBoolean()) {
            if (d.state() != DragonResetSavedData.STATE_IDLE) {
                d.resetToIdle();
                broadcastCountdown(server);
            }
            return;
        }
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        switch (d.state()) {
            case DragonResetSavedData.STATE_IDLE -> {}
            case DragonResetSavedData.STATE_AWAIT_LEAVE_END -> {
                if (!anyWitnessInEnd(end, d)) {
                    startCountdown(server, d, now);
                }
            }
            case DragonResetSavedData.STATE_COUNTDOWN -> {
                if (Config.DRAGON_RESET_CANCEL_ON_WITNESS_REENTER.getAsBoolean() && anyWitnessInEnd(end, d)) {
                    d.setState(DragonResetSavedData.STATE_AWAIT_LEAVE_END);
                    d.setTargetGameTime(0L);
                    broadcastCountdown(server);
                    ProjectIsland.LOGGER.info("Project Island: Ender Dragon respawn countdown paused — a witness re-entered the End.");
                    return;
                }
                if (now >= d.targetGameTime()) {
                    performRespawn(server, end);
                    d.resetToIdle();
                    broadcastCountdown(server);
                }
            }
            default -> d.resetToIdle();
        }
    }
}
