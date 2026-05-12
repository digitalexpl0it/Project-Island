package net.projectisland.island;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.projectisland.Config;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.ProjectIslandEntityTypeTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server hooks for rope surfing (tick + lifecycle + cancel on damage) and mob rope surf auto-start. */
public final class RopeTraversalEvents {
    private RopeTraversalEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RopeTraversalEvents::onPlayerTickPost);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(RopeTraversalEvents::onEntityTravelToDimension);
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

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel sl)
                || !ProjectIslandDimensions.isFloatingIslandsGameplay(sl)) {
            return;
        }
        if (!event.getEntity().getType().is(ProjectIslandEntityTypeTags.ROPE_SURFING_MOBS)) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (MobRopeSurfState.isSurfing(mob)) {
            return;
        }
        if (mob instanceof PathfinderMob && Config.MOB_ROPE_ANCHOR_NAVIGATION_GOAL_ENABLED.getAsBoolean()) {
            return;
        }
        MobRopeSurfState.tryAutoStartNearAnchors(mob);
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof PathfinderMob pm)) {
            return;
        }
        if (!MobRopeSurfState.isRopeSurfingMob(pm)) {
            return;
        }
        if (!Config.MOB_ROPE_ANCHOR_NAVIGATION_GOAL_ENABLED.getAsBoolean()
                || !Config.MOB_ROPE_SURF_ENABLED.getAsBoolean()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel) || event.getLevel().isClientSide()) {
            return;
        }
        int pri = Mth.clamp(Config.MOB_ROPE_GOAL_PRIORITY.getAsInt(), 1, 15);
        pm.goalSelector.addGoal(pri, new MobRopeAnchorSurfGoal(pm));
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        MobRopeSurfState.clear(mob);
    }

    private static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (MobRopeSurfState.isSurfing(mob)) {
            MobRopeSurfState.clear(mob);
        }
    }
}
