package net.projectisland.end;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.projectisland.Config;

/**
 * Optional gate on the vanilla Ender Dragon {@link net.minecraft.world.boss.ServerBossEvent}: hidden until the first
 * health loss from damage each fight, then normal updates. Re-arms when a new dragon entity joins the End (including
 * scheduled respawn).
 */
public final class DragonBossBarSupport {
    private DragonBossBarSupport() {}

    /**
     * When {@code true} for this server, vanilla may show the dragon boss bar; when {@code false}, we keep it hidden
     * each tick until first qualifying hurt.
     */
    private static final ConcurrentHashMap<MinecraftServer, Boolean> released = new ConcurrentHashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DragonBossBarSupport::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DragonBossBarSupport::onServerStopped);
        NeoForge.EVENT_BUS.addListener(DragonBossBarSupport::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(DragonBossBarSupport::onLivingDamagePost);
        NeoForge.EVENT_BUS.addListener(DragonBossBarSupport::onServerTickPost);
    }

    /** Call after {@link EndDragonFight#respawnDragon} so the bar stays hidden until first hit if a join event is late. */
    public static void rearmAfterRespawn(ServerLevel end) {
        if (!featureOn()) {
            return;
        }
        released.put(end.getServer(), Boolean.FALSE);
        applyVisibility(end, false);
    }

    private static boolean featureOn() {
        return Config.DRAGON_BOSS_BAR_HIDE_UNTIL_FIRST_DAMAGE.getAsBoolean();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (!featureOn()) {
            return;
        }
        MinecraftServer server = event.getServer();
        released.put(server, computeInitialReleased(server));
    }

    /**
     * If a dragon already has less than full health (e.g. after restart mid-fight), do not require another hit to
     * show the bar.
     */
    private static boolean computeInitialReleased(MinecraftServer server) {
        ServerLevel end = server.getLevel(Level.END);
        if (end == null || end.getDragons().isEmpty()) {
            return true;
        }
        for (EnderDragon d : end.getDragons()) {
            if (d.getHealth() < d.getMaxHealth()) {
                return true;
            }
        }
        return false;
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        released.remove(event.getServer());
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel end)) {
            return;
        }
        if (end.dimension() != Level.END || !(event.getEntity() instanceof EnderDragon)) {
            return;
        }
        if (!featureOn()) {
            return;
        }
        released.put(end.getServer(), Boolean.FALSE);
        applyVisibility(end, false);
    }

    private static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        if (!(dragon.level() instanceof ServerLevel end) || end.dimension() != Level.END) {
            return;
        }
        if (!featureOn() || event.getNewDamage() <= 0f) {
            return;
        }
        MinecraftServer server = end.getServer();
        if (Boolean.TRUE.equals(released.get(server))) {
            return;
        }
        released.put(server, Boolean.TRUE);
        applyVisibility(end, true);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (!featureOn()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!server.isReady()) {
            return;
        }
        if (Boolean.TRUE.equals(released.get(server))) {
            return;
        }
        ServerLevel end = server.getLevel(Level.END);
        if (end == null || end.getDragons().isEmpty()) {
            return;
        }
        applyVisibility(end, false);
    }

    private static void applyVisibility(ServerLevel end, boolean visible) {
        EndDragonFight fight = end.getDragonFight();
        if (fight == null) {
            return;
        }
        fight.dragonEvent.setVisible(visible);
    }
}
