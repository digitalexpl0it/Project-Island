package net.projectisland.island;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.ProjectIslandEntityTypeTags;
import net.projectisland.content.ProjectIslandContent;
import net.projectisland.content.RopeAnchorBlockEntity;
import net.projectisland.network.ActionBarToastPayload;

/**
 * Server-only mob rope surfing along {@link RopeCurveUtil} for types in {@code rope_surfing_mobs}. Completing a crossing
 * increments {@link RopeLink#mobCrossingsCompleted()} and applies configurable link HP loss (optional hard cap on
 * crossings). Auto-start uses {@link Config#MOB_ROPE_AUTO_START_HORIZONTAL_CHEB_BLOCKS} / {@link
 * Config#MOB_ROPE_AUTO_START_VERTICAL_BLOCKS} when non-zero; {@linkplain #sagPathUnobstructedBetweenAnchors sag LOS}
 * supplements eye-ray checks so vertical spans under islands still qualify.
 */
public final class MobRopeSurfState {
    private static final String TAG_LINK = ProjectIsland.MOD_ID + "_mob_rope_link";
    private static final String TAG_FROM = ProjectIsland.MOD_ID + "_mob_rope_from";
    private static final String TAG_T = ProjectIsland.MOD_ID + "_mob_rope_t";
    private static final String TAG_SURF_START = ProjectIsland.MOD_ID + "_mob_rope_begin";
    private static final String TAG_TRY_AFTER = ProjectIsland.MOD_ID + "_mob_rope_try_after";
    private static final String TAG_CROSS_COOLDOWN_UNTIL = ProjectIsland.MOD_ID + "_mob_rope_cross_cd_until";
    private static final String TAG_FOLLOW_LINK = ProjectIsland.MOD_ID + "_mob_rope_follow_link";
    private static final String TAG_FOLLOW_ANCHOR = ProjectIsland.MOD_ID + "_mob_rope_follow_anchor";
    private static final String TAG_FOLLOW_UNTIL = ProjectIsland.MOD_ID + "_mob_rope_follow_until";
    private static final String TAG_FOLLOW_PLAYER = ProjectIsland.MOD_ID + "_mob_rope_follow_player";

    private static final Map<ResourceKey<Level>, Set<UUID>> SURFING_MOBS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<UUID, Integer>> RIDERS_PER_LINK = new ConcurrentHashMap<>();
    /** Mob UUID → link id while surfing (orphan tick cleanup + rider counts). */
    private static final Map<ResourceKey<Level>, Map<UUID, UUID>> SURF_MOB_TO_LINK = new ConcurrentHashMap<>();

    private MobRopeSurfState() {}

    public static boolean isRopeSurfingMob(Mob mob) {
        return mob.getType().is(ProjectIslandEntityTypeTags.ROPE_SURFING_MOBS);
    }

    public static boolean isSurfing(Mob mob) {
        return mob.getPersistentData().hasUUID(TAG_LINK);
    }

    public static boolean isPostCrossingCooldownActive(Mob mob, ServerLevel level) {
        long until = mob.getPersistentData().getLong(TAG_CROSS_COOLDOWN_UNTIL);
        return until > level.getGameTime();
    }

    public static void markPostCrossingCooldown(Mob mob, ServerLevel level) {
        int cd = Math.max(0, Config.MOB_ROPE_POST_CROSSING_COOLDOWN_TICKS.getAsInt());
        if (cd <= 0) {
            mob.getPersistentData().remove(TAG_CROSS_COOLDOWN_UNTIL);
            return;
        }
        mob.getPersistentData().putLong(TAG_CROSS_COOLDOWN_UNTIL, level.getGameTime() + cd);
    }

    /**
     * Goal continuation: link still usable while the mob paths toward the anchor. Uses a **relaxed** path check from far
     * away (sag-only between anchors) so navigation is not aborted every tick when eye LOS to the peer fails mid-approach;
     * within a few blocks of the anchor center, the same check as {@link #tryStart} applies.
     */
    public static boolean anchorStillSurfable(ServerLevel level, Mob mob, BlockPos anchorPos) {
        return surfAnchorLinkContext(level, mob, anchorPos)
                .map(ctx -> pathClearForNavigationTowardAnchor(level, mob, anchorPos, ctx.other()))
                .orElse(false);
    }

    /**
     * Nearest anchor the mob may start from (same radii as bump auto-start; if horizontal config is **0**, search uses
     * **10** / **128** so the navigation goal still has a sensible range).
     */
    public static Optional<BlockPos> pickNearestSurfableAnchor(ServerLevel level, Mob mob) {
        if (!Config.MOB_ROPE_SURF_ENABLED.getAsBoolean() || !ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return Optional.empty();
        }
        if (!isRopeSurfingMob(mob) || !mob.isAlive() || mob.isPassenger() || isSurfing(mob)) {
            return Optional.empty();
        }
        tickInvalidateFollowSurfIntent(mob, level);
        Optional<BlockPos> followDepart = pickFollowIntentDepartureAnchorIfValid(level, mob);
        if (followDepart.isPresent()) {
            return followDepart;
        }
        int horizCfg = Config.MOB_ROPE_AUTO_START_HORIZONTAL_CHEB_BLOCKS.getAsInt();
        int horiz = horizCfg > 0 ? horizCfg : 10;
        int vertCfg = Config.MOB_ROPE_AUTO_START_VERTICAL_BLOCKS.getAsInt();
        int vert = vertCfg > 0 ? vertCfg : 128;
        BlockPos feet = mob.blockPosition();
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, feet.getX(), feet.getZ(), 2);
        FloatingIslandSavedData data = IslandWorld.get(level);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 mp = mob.position();
        for (RopeLink link : data.copyRopeLinks()) {
            for (BlockPos anchor : new BlockPos[] {link.fromAnchorPos(), link.toAnchorPos()}) {
                if (!mobFeetNearAnchorForAutoStart(feet, anchor, horiz, vert)) {
                    continue;
                }
                if (!canTryStartAtAnchor(level, mob, anchor)) {
                    continue;
                }
                Vec3 c = Vec3.atCenterOf(anchor);
                double d = mp.distanceToSqr(c);
                if (d < bestD) {
                    bestD = d;
                    best = anchor;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static void clear(Mob mob) {
        mob.setNoGravity(false);
        CompoundTag d = mob.getPersistentData();
        if (mob.level() instanceof ServerLevel sl) {
            UUID mobId = mob.getUUID();
            Map<UUID, UUID> smap = SURF_MOB_TO_LINK.get(sl.dimension());
            UUID lid = smap != null ? smap.remove(mobId) : null;
            if (lid == null && d.hasUUID(TAG_LINK)) {
                lid = d.getUUID(TAG_LINK);
            }
            if (lid != null) {
                decrementRiders(sl, lid);
            }
            removeSurfing(sl, mobId);
        }
        d.remove(TAG_LINK);
        d.remove(TAG_FROM);
        d.remove(TAG_T);
        d.remove(TAG_SURF_START);
        removeFollowSurfIntentData(d);
    }

    /** Auto-start probe: whitelisted mob near a linked anchor with LOS (cooldown in PDC). */
    public static void tryAutoStartNearAnchors(Mob mob) {
        if (!Config.MOB_ROPE_SURF_ENABLED.getAsBoolean()) {
            return;
        }
        if (mob.level().isClientSide() || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        if (!isRopeSurfingMob(mob) || !mob.isAlive() || mob.isPassenger()) {
            return;
        }
        if (isSurfing(mob)) {
            return;
        }
        long tryAfter = mob.getPersistentData().getLong(TAG_TRY_AFTER);
        if (level.getGameTime() < tryAfter) {
            return;
        }
        if (isPostCrossingCooldownActive(mob, level)) {
            return;
        }
        tickInvalidateFollowSurfIntent(mob, level);
        CompoundTag fd = mob.getPersistentData();
        if (fd.contains(TAG_FOLLOW_UNTIL) && level.getGameTime() <= fd.getLong(TAG_FOLLOW_UNTIL) && fd.contains(TAG_FOLLOW_ANCHOR)) {
            BlockPos depart = BlockPos.of(fd.getLong(TAG_FOLLOW_ANCHOR));
            int hCfg = Config.MOB_ROPE_AUTO_START_HORIZONTAL_CHEB_BLOCKS.getAsInt();
            int horizFollow = hCfg > 0 ? hCfg : 10;
            int vCfg = Config.MOB_ROPE_AUTO_START_VERTICAL_BLOCKS.getAsInt();
            int vertFollow = vCfg > 0 ? vCfg : 128;
            BlockPos feetF = mob.blockPosition();
            if (mobFeetNearAnchorForAutoStart(feetF, depart, horizFollow, vertFollow) || mobTouchesAnchor(mob, depart, level)) {
                if (tryStart(level, mob, depart)) {
                    return;
                }
            }
        }
        FloatingIslandSavedData data = IslandWorld.get(level);
        int horiz = Config.MOB_ROPE_AUTO_START_HORIZONTAL_CHEB_BLOCKS.getAsInt();
        int vertCfg = Config.MOB_ROPE_AUTO_START_VERTICAL_BLOCKS.getAsInt();
        if (horiz > 0) {
            int vert = vertCfg > 0 ? vertCfg : 128;
            BlockPos feet = mob.blockPosition();
            for (RopeLink link : data.copyRopeLinks()) {
                BlockPos a = link.fromAnchorPos();
                BlockPos b = link.toAnchorPos();
                if (mobFeetNearAnchorForAutoStart(feet, a, horiz, vert)) {
                    if (tryAutoStartAtAnchor(level, mob, link, a, b)) {
                        return;
                    }
                }
                if (mobFeetNearAnchorForAutoStart(feet, b, horiz, vert)) {
                    if (tryAutoStartAtAnchor(level, mob, link, b, a)) {
                        return;
                    }
                }
            }
        } else {
            BlockPos feet = mob.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 2, 2))) {
                if (!(level.getBlockEntity(p) instanceof RopeAnchorBlockEntity be) || !be.hasLink()) {
                    continue;
                }
                if (!mobTouchesAnchor(mob, p, level)) {
                    continue;
                }
                UUID lid = be.getLinkId();
                if (lid == null) {
                    continue;
                }
                RopeLink link = data.getRopeLink(lid).orElse(null);
                if (link == null) {
                    continue;
                }
                BlockPos other = link.otherAnchor(p);
                if (other == null) {
                    continue;
                }
                if (tryAutoStartAtAnchor(level, mob, link, p, other)) {
                    return;
                }
            }
        }
        mob.getPersistentData().putLong(TAG_TRY_AFTER, level.getGameTime() + Config.MOB_ROPE_AUTO_TRY_COOLDOWN_TICKS.getAsInt());
    }

    private static boolean mobFeetNearAnchorForAutoStart(BlockPos feet, BlockPos anchor, int horizCheb, int vert) {
        int dx = Math.abs(feet.getX() - anchor.getX());
        int dz = Math.abs(feet.getZ() - anchor.getZ());
        int dy = Math.abs(feet.getY() - anchor.getY());
        return Math.max(dx, dz) <= horizCheb && dy <= vert;
    }

    /**
     * @param other the peer anchor for {@code anchorPos}
     * @return true if surfing started
     */
    private static boolean tryAutoStartAtAnchor(ServerLevel level, Mob mob, RopeLink link, BlockPos anchorPos, BlockPos other) {
        if (!(level.getBlockEntity(anchorPos) instanceof RopeAnchorBlockEntity be) || !be.hasLink()) {
            return false;
        }
        if (!be.getLinkId().equals(link.id())) {
            return false;
        }
        if (shouldDeferAutoSurfForClosePlayerTarget(mob, anchorPos, other)) {
            mob.getPersistentData()
                    .putLong(TAG_TRY_AFTER, level.getGameTime() + Config.MOB_ROPE_AUTO_TRY_COOLDOWN_TICKS.getAsInt());
            return true;
        }
        return tryStart(level, mob, anchorPos);
    }

    /**
     * True when the mob is close enough to auto-start: collision overlap with the anchor shape, or a small horizontal
     * expansion so face-adjacent and diagonal neighbors (common on grass next to the block) still count.
     */
    private static boolean mobTouchesAnchor(Mob mob, BlockPos anchorPos, ServerLevel level) {
        BlockState st = level.getBlockState(anchorPos);
        VoxelShape shape = st.getCollisionShape(level, anchorPos);
        if (shape.isEmpty()) {
            shape = Shapes.block();
        }
        var shapeWorld = shape.bounds().move(anchorPos);
        if (mob.getBoundingBox().intersects(shapeWorld)) {
            return true;
        }
        var blockCube = Shapes.block().bounds().move(anchorPos);
        return mob.getBoundingBox().inflate(0.45, 0.35, 0.45).intersects(blockCube);
    }

    /**
     * Skips auto rope surf while the mob targets a nearby player <strong>and</strong> crossing the link would move them
     * horizontally <strong>away</strong> from that player (rim / hallway chase). If the far anchor is closer to the
     * player than the anchor the mob is at, crossing is treated as pursuit — not deferred. Disabled when
     * {@link Config#MOB_ROPE_SURF_DEFER_AUTO_WHEN_PLAYER_TARGET_WITHIN_BLOCKS} is 0. Never defers when the target player
     * is actively rope surfing.
     */
    private static boolean shouldDeferAutoSurfForClosePlayerTarget(Mob mob, BlockPos anchorPos, BlockPos otherPos) {
        double range = Config.MOB_ROPE_SURF_DEFER_AUTO_WHEN_PLAYER_TARGET_WITHIN_BLOCKS.getAsDouble();
        if (range <= 0d) {
            return false;
        }
        if (mob.level() instanceof ServerLevel sl && hasActiveFollowSurfIntentForAnchor(mob, sl, anchorPos)) {
            return false;
        }
        if (!(mob.getTarget() instanceof Player player) || !player.isAlive()) {
            return false;
        }
        if (mob.level() != player.level()) {
            return false;
        }
        if (player instanceof ServerPlayer sp && RopeSurfingState.isSurfing(sp)) {
            return false;
        }
        if (mob.distanceToSqr(player) > range * range) {
            return false;
        }
        Vec3 pv = player.position();
        double distThis = horizontalDistSqrToBlock(pv, anchorPos);
        double distOther = horizontalDistSqrToBlock(pv, otherPos);
        // Far end is closer to player than this anchor → allow (zombie can ride toward player on the other island).
        if (distOther < distThis - 1.0) {
            return false;
        }
        return distOther > distThis + 1.0;
    }

    private static double horizontalDistSqrToBlock(Vec3 v, BlockPos p) {
        double dx = v.x - (p.getX() + 0.5);
        double dz = v.z - (p.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    /** True when follow-player-rope PDC points at this anchor and has not expired (target still matches). */
    private static boolean hasActiveFollowSurfIntentForAnchor(Mob mob, ServerLevel level, BlockPos anchorPos) {
        if (!Config.MOB_ROPE_FOLLOW_PLAYER_SURF_ENABLED.getAsBoolean()) {
            return false;
        }
        CompoundTag d = mob.getPersistentData();
        if (!d.contains(TAG_FOLLOW_UNTIL) || level.getGameTime() > d.getLong(TAG_FOLLOW_UNTIL)) {
            return false;
        }
        if (!d.hasUUID(TAG_FOLLOW_PLAYER) || !d.contains(TAG_FOLLOW_ANCHOR)) {
            return false;
        }
        if (!anchorPos.equals(BlockPos.of(d.getLong(TAG_FOLLOW_ANCHOR)))) {
            return false;
        }
        return mob.getTarget() instanceof Player tp
                && tp.isAlive()
                && tp.getUUID().equals(d.getUUID(TAG_FOLLOW_PLAYER))
                && mob.level() == tp.level();
    }

    private record SurfLinkCtx(RopeLink link, BlockPos other) {}

    /**
     * Shared link / HP / arc / rider checks for mob surf at {@code anchorPos}. Does **not** include mob eye path to the
     * peer anchor.
     */
    private static Optional<SurfLinkCtx> surfAnchorLinkContext(ServerLevel level, Mob mob, BlockPos anchorPos) {
        if (!Config.MOB_ROPE_SURF_ENABLED.getAsBoolean()) {
            return Optional.empty();
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return Optional.empty();
        }
        if (!isRopeSurfingMob(mob) || !mob.isAlive() || mob.isPassenger()) {
            return Optional.empty();
        }
        if (isPostCrossingCooldownActive(mob, level)) {
            return Optional.empty();
        }
        if (!(level.getBlockEntity(anchorPos) instanceof RopeAnchorBlockEntity be) || !be.hasLink()) {
            return Optional.empty();
        }
        UUID lid = be.getLinkId();
        if (lid == null) {
            return Optional.empty();
        }
        FloatingIslandSavedData data = IslandWorld.get(level);
        RopeLink link = data.getRopeLink(lid).orElse(null);
        if (link == null) {
            return Optional.empty();
        }
        BlockPos other = link.otherAnchor(anchorPos);
        if (other == null
                || (!link.fromAnchorPos().equals(anchorPos) && !link.toAnchorPos().equals(anchorPos))) {
            return Optional.empty();
        }
        int maxRiders = Config.MOB_ROPE_MAX_SURFING_PER_LINK.getAsInt();
        if (maxRiders > 0 && riderCount(level, lid) >= maxRiders) {
            return Optional.empty();
        }
        float minHp = (float) Config.ROPE_TRAVERSAL_SURF_MIN_HEALTH_FRACTION.getAsDouble();
        if (link.healthFraction() < minHp) {
            return Optional.empty();
        }
        double arc = RopeCurveUtil.arcLengthBlocks(anchorPos, other);
        if (arc < 0.75d) {
            return Optional.empty();
        }
        return Optional.of(new SurfLinkCtx(link, other));
    }

    /** While pathing to an anchor, allow sag-only clearance until close; then require full mob LOS like {@link #tryStart}. */
    private static boolean pathClearForNavigationTowardAnchor(ServerLevel level, Mob mob, BlockPos anchorPos, BlockPos other) {
        double relax = Math.max(6.0, Config.MOB_ROPE_GOAL_ANCHOR_START_DIST_BLOCKS.getAsDouble() * 3.0);
        if (mob.position().distanceToSqr(Vec3.atCenterOf(anchorPos)) > relax * relax) {
            return sagPathUnobstructedBetweenAnchors(level, anchorPos, other);
        }
        return mobPathClearToOtherAnchor(level, mob, anchorPos, other);
    }

    private static void removeFollowSurfIntentData(CompoundTag d) {
        d.remove(TAG_FOLLOW_LINK);
        d.remove(TAG_FOLLOW_ANCHOR);
        d.remove(TAG_FOLLOW_UNTIL);
        d.remove(TAG_FOLLOW_PLAYER);
    }

    /** Clears follow-player-rope intent (e.g. after surf starts or on clear). */
    public static void clearFollowSurfIntent(Mob mob) {
        removeFollowSurfIntentData(mob.getPersistentData());
    }

    /** Expires or clears follow intent when the target / link / time no longer matches. */
    private static void tickInvalidateFollowSurfIntent(Mob mob, ServerLevel level) {
        if (!Config.MOB_ROPE_FOLLOW_PLAYER_SURF_ENABLED.getAsBoolean()) {
            return;
        }
        CompoundTag d = mob.getPersistentData();
        if (!d.contains(TAG_FOLLOW_UNTIL)) {
            return;
        }
        long until = d.getLong(TAG_FOLLOW_UNTIL);
        if (level.getGameTime() > until) {
            removeFollowSurfIntentData(d);
            return;
        }
        if (!d.hasUUID(TAG_FOLLOW_PLAYER) || !d.hasUUID(TAG_FOLLOW_LINK) || !d.contains(TAG_FOLLOW_ANCHOR)) {
            removeFollowSurfIntentData(d);
            return;
        }
        UUID wantPlayer = d.getUUID(TAG_FOLLOW_PLAYER);
        if (!(mob.getTarget() instanceof Player tp) || !tp.isAlive() || !tp.getUUID().equals(wantPlayer)) {
            removeFollowSurfIntentData(d);
            return;
        }
        if (mob.level() != tp.level()) {
            removeFollowSurfIntentData(d);
            return;
        }
        BlockPos depart = BlockPos.of(d.getLong(TAG_FOLLOW_ANCHOR));
        UUID wantLink = d.getUUID(TAG_FOLLOW_LINK);
        if (!(level.getBlockEntity(depart) instanceof RopeAnchorBlockEntity be) || !be.hasLink() || !wantLink.equals(be.getLinkId())) {
            removeFollowSurfIntentData(d);
            return;
        }
        FloatingIslandSavedData data = IslandWorld.get(level);
        RopeLink link = data.getRopeLink(wantLink).orElse(null);
        if (link == null) {
            removeFollowSurfIntentData(d);
            return;
        }
        BlockPos other = link.otherAnchor(depart);
        if (other == null
                || (!link.fromAnchorPos().equals(depart) && !link.toAnchorPos().equals(depart))) {
            removeFollowSurfIntentData(d);
        }
    }

    /**
     * When a player starts rope surfing, mobs in range that target them prefer the same departure anchor for a short
     * time; mobs already at the anchor may start immediately.
     */
    public static void notifyPlayerStartedRopeSurf(ServerLevel level, ServerPlayer player, UUID linkId, BlockPos departAnchor) {
        if (!Config.MOB_ROPE_SURF_ENABLED.getAsBoolean()
                || !Config.MOB_ROPE_FOLLOW_PLAYER_SURF_ENABLED.getAsBoolean()
                || !ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        double expand = Config.MOB_ROPE_FOLLOW_PLAYER_SURF_ASSIGN_RANGE_BLOCKS.getAsDouble();
        if (expand <= 0d) {
            return;
        }
        int intentTicks = Math.max(20, Config.MOB_ROPE_FOLLOW_PLAYER_SURF_INTENT_TICKS.getAsInt());
        long until = level.getGameTime() + intentTicks;
        AABB box = player.getBoundingBox().inflate(expand, expand, expand);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> isRopeSurfingMob(m) && m.isAlive() && !isSurfing(m) && !m.isPassenger())) {
            if (mob.getTarget() != player) {
                continue;
            }
            CompoundTag d = mob.getPersistentData();
            d.putUUID(TAG_FOLLOW_LINK, linkId);
            d.putLong(TAG_FOLLOW_ANCHOR, departAnchor.asLong());
            d.putLong(TAG_FOLLOW_UNTIL, until);
            d.putUUID(TAG_FOLLOW_PLAYER, player.getUUID());
            if (mob.position().distanceToSqr(Vec3.atCenterOf(departAnchor))
                    <= Mth.square(Config.MOB_ROPE_GOAL_ANCHOR_START_DIST_BLOCKS.getAsDouble() * 2.0)) {
                tryStart(level, mob, departAnchor);
            }
        }
    }

    private static Optional<BlockPos> pickFollowIntentDepartureAnchorIfValid(ServerLevel level, Mob mob) {
        if (!Config.MOB_ROPE_FOLLOW_PLAYER_SURF_ENABLED.getAsBoolean()) {
            return Optional.empty();
        }
        CompoundTag d = mob.getPersistentData();
        if (!d.contains(TAG_FOLLOW_UNTIL) || level.getGameTime() > d.getLong(TAG_FOLLOW_UNTIL)) {
            return Optional.empty();
        }
        if (!d.hasUUID(TAG_FOLLOW_PLAYER) || !d.hasUUID(TAG_FOLLOW_LINK) || !d.contains(TAG_FOLLOW_ANCHOR)) {
            return Optional.empty();
        }
        if (!(mob.getTarget() instanceof Player tp) || !tp.isAlive() || !tp.getUUID().equals(d.getUUID(TAG_FOLLOW_PLAYER))) {
            return Optional.empty();
        }
        BlockPos depart = BlockPos.of(d.getLong(TAG_FOLLOW_ANCHOR));
        UUID wantLink = d.getUUID(TAG_FOLLOW_LINK);
        if (!(level.getBlockEntity(depart) instanceof RopeAnchorBlockEntity be) || !be.hasLink() || !wantLink.equals(be.getLinkId())) {
            return Optional.empty();
        }
        Optional<SurfLinkCtx> ctxOpt = surfAnchorLinkContext(level, mob, depart);
        if (ctxOpt.isEmpty() || !ctxOpt.get().link().id().equals(wantLink)) {
            return Optional.empty();
        }
        SurfLinkCtx ctx = ctxOpt.get();
        double relax = Math.max(8.0, Config.MOB_ROPE_GOAL_ANCHOR_START_DIST_BLOCKS.getAsDouble() * 4.0);
        Vec3 c = Vec3.atCenterOf(depart);
        boolean pathOk =
                mob.position().distanceToSqr(c) > relax * relax
                        ? sagPathUnobstructedBetweenAnchors(level, depart, ctx.other())
                        : mobPathClearToOtherAnchor(level, mob, depart, ctx.other());
        return pathOk ? Optional.of(depart) : Optional.empty();
    }

    /**
     * Dry-run for {@link #tryStart}: enabled world, mob tag, cooldown, link HP/arc/path/riders (not already surfing —
     * {@link #tryStart} clears timed-out surf first).
     */
    public static boolean canTryStartAtAnchor(ServerLevel level, Mob mob, BlockPos anchorPos) {
        return surfAnchorLinkContext(level, mob, anchorPos)
                .map(ctx -> mobPathClearToOtherAnchor(level, mob, anchorPos, ctx.other()))
                .orElse(false);
    }

    /**
     * @return true if surfing started
     */
    public static boolean tryStart(ServerLevel level, Mob mob, BlockPos anchorPos) {
        if (isSurfing(mob)) {
            if (surfTimedOut(mob, level)) {
                clear(mob);
            } else {
                return false;
            }
        }
        if (!canTryStartAtAnchor(level, mob, anchorPos)) {
            return false;
        }
        if (!(level.getBlockEntity(anchorPos) instanceof RopeAnchorBlockEntity be) || !be.hasLink()) {
            return false;
        }
        UUID lid = be.getLinkId();
        if (lid == null) {
            return false;
        }
        FloatingIslandSavedData data = IslandWorld.get(level);
        RopeLink link = data.getRopeLink(lid).orElse(null);
        if (link == null) {
            return false;
        }

        removeFollowSurfIntentData(mob.getPersistentData());
        CompoundTag d = mob.getPersistentData();
        d.putUUID(TAG_LINK, link.id());
        d.putLong(TAG_FROM, anchorPos.asLong());
        d.putDouble(TAG_T, 0.0);
        d.putLong(TAG_SURF_START, level.getGameTime());
        mob.setNoGravity(true);
        mob.setDeltaMovement(Vec3.ZERO);
        incrementRiders(level, link.id());
        addSurfing(level, mob.getUUID());
        SURF_MOB_TO_LINK.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(mob.getUUID(), link.id());
        sendNearbyPlayerMobSurfWarning(level, mob);
        return true;
    }

    private static void sendNearbyPlayerMobSurfWarning(ServerLevel level, Mob mob) {
        if (!Config.MOB_ROPE_NEARBY_PLAYER_WARNING_ENABLED.getAsBoolean()) {
            return;
        }
        double r = Config.MOB_ROPE_NEARBY_PLAYER_WARNING_RANGE_BLOCKS.getAsDouble();
        if (r <= 0d) {
            return;
        }
        Vec3 c = mob.position();
        AABB box = new AABB(c, c).inflate(r, r, r);
        String label = mob.getDisplayName().getString();
        for (ServerPlayer sp :
                level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator())) {
            ActionBarToastPayload.sendWithArgs(sp, "projectisland.rope.surf.mob_nearby_warning", label);
        }
    }

    private static boolean lineOfSightToAnchor(ServerLevel level, Mob mob, BlockPos other) {
        Vec3 from = mob.getEyePosition(1f);
        Vec3 to = Vec3.atCenterOf(other);
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        if (hit instanceof BlockHitResult br) {
            return br.getBlockPos().distManhattan(other) <= 1;
        }
        return false;
    }

    /**
     * Eye ray to far anchor (horizontal zips) plus {@linkplain #sagPathUnobstructedBetweenAnchors sag sampling} when that
     * fails (vertical void ropes: the ray often hits the island above before reaching the peer anchor).
     */
    private static boolean mobPathClearToOtherAnchor(ServerLevel level, Mob mob, BlockPos anchorPos, BlockPos other) {
        if (lineOfSightToAnchor(level, mob, other)) {
            return true;
        }
        return sagPathUnobstructedBetweenAnchors(level, anchorPos, other);
    }

    private static boolean nearRopeEndpoint(BlockPos sample, BlockPos ep) {
        return Math.abs(sample.getX() - ep.getX()) <= 1
                && Math.abs(sample.getY() - ep.getY()) <= 1
                && Math.abs(sample.getZ() - ep.getZ()) <= 1;
    }

    private static boolean sagPathUnobstructedBetweenAnchors(ServerLevel level, BlockPos from, BlockPos to) {
        double arc = RopeCurveUtil.arcLengthBlocks(from, to);
        int steps = Mth.clamp(Mth.ceil(arc * 2.0), 6, 200);
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Vec3 p = RopeCurveUtil.sagPoint(from, to, t);
            BlockPos c = BlockPos.containing(p.x, p.y, p.z);
            if (nearRopeEndpoint(c, from) || nearRopeEndpoint(c, to)) {
                continue;
            }
            BlockState st = level.getBlockState(c);
            if (st.getBlock() == ProjectIslandContent.ROPE_ANCHOR) {
                continue;
            }
            if (st.blocksMotion()) {
                return false;
            }
            if (!st.getCollisionShape(level, c).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean surfTimedOut(Mob mob, ServerLevel level) {
        CompoundTag d = mob.getPersistentData();
        if (!d.contains(TAG_SURF_START)) {
            return true;
        }
        long start = d.getLong(TAG_SURF_START);
        return level.getGameTime() - start > Config.MOB_ROPE_SURF_MAX_DURATION_TICKS.getAsInt();
    }

    public static void tickSurfingMobs(ServerLevel level) {
        if (!Config.MOB_ROPE_SURF_ENABLED.getAsBoolean()) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        Set<UUID> ids = SURFING_MOBS.get(level.dimension());
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(ids)) {
            if (!(level.getEntity(id) instanceof Mob mob)) {
                orphanSurfingMob(level, id);
                continue;
            }
            if (!isSurfing(mob)) {
                ids.remove(id);
                continue;
            }
            tick(mob, level);
        }
    }

    private static void orphanSurfingMob(ServerLevel level, UUID mobId) {
        removeSurfing(level, mobId);
        Map<UUID, UUID> smap = SURF_MOB_TO_LINK.get(level.dimension());
        if (smap != null) {
            UUID lid = smap.remove(mobId);
            if (lid != null) {
                decrementRiders(level, lid);
            }
        }
    }

    private static void tick(Mob mob, ServerLevel level) {
        CompoundTag d = mob.getPersistentData();
        if (!d.hasUUID(TAG_LINK)) {
            clear(mob);
            return;
        }
        if (surfTimedOut(mob, level)) {
            clear(mob);
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            clear(mob);
            return;
        }
        UUID lid = d.getUUID(TAG_LINK);
        BlockPos from = BlockPos.of(d.getLong(TAG_FROM));
        FloatingIslandSavedData data = IslandWorld.get(level);
        RopeLink link = data.getRopeLink(lid).orElse(null);
        if (link == null) {
            clear(mob);
            return;
        }
        BlockPos other = link.otherAnchor(from);
        if (other == null
                || (!link.fromAnchorPos().equals(from) && !link.toAnchorPos().equals(from))) {
            clear(mob);
            return;
        }
        float minHp = (float) Config.ROPE_TRAVERSAL_SURF_MIN_HEALTH_FRACTION.getAsDouble();
        if (link.healthFraction() < minHp) {
            clear(mob);
            return;
        }
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, from.getX(), from.getZ(), 2);
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, other.getX(), other.getZ(), 2);
        BlockState fs = level.getBlockState(from);
        BlockState os = level.getBlockState(other);
        if (fs.getBlock() != ProjectIslandContent.ROPE_ANCHOR || os.getBlock() != ProjectIslandContent.ROPE_ANCHOR) {
            clear(mob);
            return;
        }

        double arc = RopeCurveUtil.arcLengthBlocks(from, other);
        if (arc < 1e-3) {
            clear(mob);
            return;
        }
        double perAdvanceDmg = Config.MOB_ROPE_DAMAGE_PER_ADVANCE_DURING_CROSSING.getAsDouble();
        if (perAdvanceDmg > 0d) {
            if (!applyLinkDamageOnly(level, data, link, (float) perAdvanceDmg, mob)) {
                return;
            }
            link = data.getRopeLink(lid).orElse(null);
            if (link == null) {
                clear(mob);
                return;
            }
        }

        double speed = Config.MOB_ROPE_SURF_SPEED_BLOCKS_PER_SECOND.getAsDouble();
        double dt = speed / (arc * 20.0d);
        double t = d.getDouble(TAG_T) + dt;
        if (t >= 1.0) {
            Vec3 end = RopeCurveUtil.sagPoint(from, other, 1.0);
            mob.fallDistance = 0.0f;
            IslandChunkLoader.ensureChunksAroundWorldBlock(level, Mth.floor(end.x), Mth.floor(end.z), 2);
            try {
                mob.setDeltaMovement(Vec3.ZERO);
                mob.teleportTo(
                        level, end.x, end.y, end.z, Set.<RelativeMovement>of(), mob.getYRot(), mob.getXRot());
                mob.setOnGround(true);
                mob.setDeltaMovement(Vec3.ZERO);
            } catch (Exception e) {
                ProjectIsland.LOGGER.warn("Mob rope surf end teleport failed for {}", mob, e);
            }
            onCompletedCrossing(level, data, lid, mob);
            return;
        }
        d.putDouble(TAG_T, t);
        Vec3 p = RopeCurveUtil.sagPoint(from, other, t);
        mob.fallDistance = 0.0f;
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, Mth.floor(p.x), Mth.floor(p.z), 2);
        applyMobSurfPositionSync(mob, level, p);
    }

    /**
     * @return false if link severed / mob cleared
     */
    private static boolean applyLinkDamageOnly(
            ServerLevel level, FloatingIslandSavedData data, RopeLink link, float damage, Mob mob) {
        if (damage <= 0f) {
            return true;
        }
        RopeLink cur = data.getRopeLink(link.id()).orElse(null);
        if (cur == null) {
            clear(mob);
            return false;
        }
        float nh = cur.health() - damage;
        if (nh <= 0f) {
            RopeAnchorBlockEntity.severLinkFromSavedData(level, cur);
            clear(mob);
            return false;
        }
        data.putRopeLink(cur.withHealth(nh));
        RopeLinkServerSync.sendRopeLinkSyncForLevel(level);
        return true;
    }

    private static void onCompletedCrossing(ServerLevel level, FloatingIslandSavedData data, UUID lid, Mob mob) {
        try {
            RopeLink link = data.getRopeLink(lid).orElse(null);
            if (link != null) {
                int newCount = link.mobCrossingsCompleted() + 1;
                float dmg = (float) Config.MOB_ROPE_DAMAGE_PER_COMPLETED_CROSSING.getAsDouble();
                float nh = link.health() - dmg;
                int maxCross = Config.MOB_ROPE_MAX_CROSSINGS_BEFORE_SEVER.getAsInt();
                boolean severByCount = maxCross > 0 && newCount >= maxCross;
                boolean severByHp = nh <= 0f;
                RopeLink updated = link.withMobCrossingsCompleted(newCount).withHealth(Math.max(0f, nh));
                if (severByHp || severByCount) {
                    data.putRopeLink(updated);
                    RopeAnchorBlockEntity.severLinkFromSavedData(level, updated);
                } else {
                    data.putRopeLink(updated);
                }
                RopeLinkServerSync.sendRopeLinkSyncForLevel(level);
            }
        } finally {
            clear(mob);
            if (mob.isAlive() && !mob.isRemoved() && mob.level() instanceof ServerLevel sl) {
                markPostCrossingCooldown(mob, sl);
            }
        }
    }

    private static void applyMobSurfPositionSync(Mob mob, ServerLevel level, Vec3 p) {
        if (mob.isRemoved() || !isSurfing(mob)) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel mobLevel) || mobLevel != level) {
            return;
        }
        try {
            mob.fallDistance = 0.0f;
            mob.setDeltaMovement(Vec3.ZERO);
            mob.teleportTo(level, p.x, p.y, p.z, Set.<RelativeMovement>of(), mob.getYRot(), mob.getXRot());
            mob.setOnGround(true);
            mob.setDeltaMovement(Vec3.ZERO);
        } catch (Exception e) {
            ProjectIsland.LOGGER.warn("Mob rope surf tick teleport failed: clear surf state for {}", mob, e);
            clear(mob);
        }
    }

    private static void addSurfing(ServerLevel level, UUID mobId) {
        SURFING_MOBS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(mobId);
    }

    private static void removeSurfing(ServerLevel level, UUID mobId) {
        Set<UUID> set = SURFING_MOBS.get(level.dimension());
        if (set != null) {
            set.remove(mobId);
        }
    }

    private static int riderCount(ServerLevel level, UUID linkId) {
        Map<UUID, Integer> m = RIDERS_PER_LINK.get(level.dimension());
        if (m == null) {
            return 0;
        }
        return m.getOrDefault(linkId, 0);
    }

    private static void incrementRiders(ServerLevel level, UUID linkId) {
        RIDERS_PER_LINK.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).merge(linkId, 1, Integer::sum);
    }

    private static void decrementRiders(ServerLevel level, UUID linkId) {
        Map<UUID, Integer> m = RIDERS_PER_LINK.get(level.dimension());
        if (m == null) {
            return;
        }
        m.compute(linkId, (k, v) -> {
            if (v == null || v <= 1) {
                return null;
            }
            return v - 1;
        });
    }
}
