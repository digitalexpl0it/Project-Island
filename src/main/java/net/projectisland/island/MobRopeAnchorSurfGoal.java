package net.projectisland.island;

import java.util.EnumSet;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.projectisland.Config;
import net.projectisland.ProjectIslandDimensions;

/**
 * Walks {@link PathfinderMob} rope-surfing types toward the nearest legal anchor, then starts {@link MobRopeSurfState}
 * (bypasses bump-only {@linkplain MobRopeSurfState#shouldDeferAutoSurfForClosePlayerTarget player-target defer}). Cooldown
 * after a completed crossing is enforced in {@link MobRopeSurfState#canTryStartAtAnchor}.
 */
public final class MobRopeAnchorSurfGoal extends Goal {
    private final PathfinderMob mob;
    private BlockPos anchorPos;
    private int repathTimer;

    public MobRopeAnchorSurfGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!Config.MOB_ROPE_ANCHOR_NAVIGATION_GOAL_ENABLED.getAsBoolean()) {
            return false;
        }
        if (!Config.MOB_ROPE_SURF_ENABLED.getAsBoolean()) {
            return false;
        }
        if (mob.level().isClientSide() || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return false;
        }
        if (!MobRopeSurfState.isRopeSurfingMob(mob) || !mob.isAlive() || mob.isPassenger()) {
            return false;
        }
        if (MobRopeSurfState.isSurfing(mob)) {
            return false;
        }
        Optional<BlockPos> pick = MobRopeSurfState.pickNearestSurfableAnchor(level, mob);
        if (pick.isEmpty()) {
            anchorPos = null;
            return false;
        }
        anchorPos = pick.get();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!Config.MOB_ROPE_ANCHOR_NAVIGATION_GOAL_ENABLED.getAsBoolean()
                || !Config.MOB_ROPE_SURF_ENABLED.getAsBoolean()) {
            return false;
        }
        if (mob.level().isClientSide() || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return false;
        }
        if (!MobRopeSurfState.isRopeSurfingMob(mob) || !mob.isAlive() || mob.isPassenger()) {
            return false;
        }
        if (MobRopeSurfState.isSurfing(mob)) {
            return false;
        }
        return anchorPos != null && MobRopeSurfState.anchorStillSurfable(level, mob, anchorPos);
    }

    @Override
    public void start() {
        repathTimer = 0;
        pathToAnchor();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel sl) || anchorPos == null) {
            return;
        }
        int interval = Math.max(5, Config.MOB_ROPE_GOAL_REPATH_INTERVAL_TICKS.getAsInt());
        if (--repathTimer <= 0) {
            repathTimer = interval;
            pathToAnchor();
        }
        Vec3 target = Vec3.atBottomCenterOf(anchorPos);
        if (mob.position().distanceToSqr(target) <= Mth.square(Config.MOB_ROPE_GOAL_ANCHOR_START_DIST_BLOCKS.getAsDouble())) {
            if (MobRopeSurfState.tryStart(sl, mob, anchorPos)) {
                stop();
            }
        } else {
            mob.getLookControl().setLookAt(target.x, target.y, target.z, 30.0f, (float) mob.getMaxHeadXRot());
        }
    }

    @Override
    public void stop() {
        anchorPos = null;
        mob.getNavigation().stop();
    }

    private void pathToAnchor() {
        if (anchorPos == null) {
            return;
        }
        Vec3 t = Vec3.atBottomCenterOf(anchorPos);
        mob.getNavigation().moveTo(t.x, t.y, t.z, 1.05);
    }
}
