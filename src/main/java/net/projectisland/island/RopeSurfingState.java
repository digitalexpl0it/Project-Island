package net.projectisland.island;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandAdvancements;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.content.ProjectIslandContent;
import net.projectisland.content.RopeAnchorBlockEntity;
import net.projectisland.network.ActionBarToastPayload;

/**
 * Server-only rope surfing: empty-hand use on a linked anchor (non-sneak) slides the player along {@link RopeCurveUtil}
 * toward the other anchor. Sneak while riding cancels; void rescue is skipped while active.
 */
public final class RopeSurfingState {
    private static final String TAG_LINK = ProjectIsland.MOD_ID + "_rope_surf_link";
    private static final String TAG_FROM = ProjectIsland.MOD_ID + "_rope_surf_from";
    private static final String TAG_T = ProjectIsland.MOD_ID + "_rope_surf_t";
    private static final String TAG_CD = ProjectIsland.MOD_ID + "_rope_surf_cd";
    private static final String TAG_SURF_START = ProjectIsland.MOD_ID + "_rope_surf_begin";

    /** Legacy PDC keys (older builds “zipline”) — migrated on tick. */
    private static final String LEGACY_TAG_LINK = ProjectIsland.MOD_ID + "_rope_zip_link";
    private static final String LEGACY_TAG_FROM = ProjectIsland.MOD_ID + "_rope_zip_from";
    private static final String LEGACY_TAG_T = ProjectIsland.MOD_ID + "_rope_zip_t";
    private static final String LEGACY_TAG_CD = ProjectIsland.MOD_ID + "_rope_zip_cd";

    private RopeSurfingState() {}

    public static boolean isSurfing(ServerPlayer player) {
        CompoundTag d = player.getPersistentData();
        if (d.hasUUID(TAG_LINK)) {
            return true;
        }
        return d.hasUUID(LEGACY_TAG_LINK);
    }

    private static void migrateLegacyTagsIfNeeded(CompoundTag d) {
        if (d.hasUUID(TAG_LINK) || !d.hasUUID(LEGACY_TAG_LINK)) {
            return;
        }
        d.putUUID(TAG_LINK, d.getUUID(LEGACY_TAG_LINK));
        d.remove(LEGACY_TAG_LINK);
        if (d.contains(LEGACY_TAG_FROM)) {
            d.putLong(TAG_FROM, d.getLong(LEGACY_TAG_FROM));
            d.remove(LEGACY_TAG_FROM);
        }
        if (d.contains(LEGACY_TAG_T)) {
            d.putDouble(TAG_T, d.getDouble(LEGACY_TAG_T));
            d.remove(LEGACY_TAG_T);
        }
        if (d.contains(LEGACY_TAG_CD)) {
            d.putLong(TAG_CD, d.getLong(LEGACY_TAG_CD));
            d.remove(LEGACY_TAG_CD);
        }
    }

    public static void clear(ServerPlayer player) {
        player.setNoGravity(false);
        CompoundTag d = player.getPersistentData();
        d.remove(TAG_LINK);
        d.remove(TAG_FROM);
        d.remove(TAG_T);
        d.remove(TAG_CD);
        d.remove(TAG_SURF_START);
        d.remove(LEGACY_TAG_LINK);
        d.remove(LEGACY_TAG_FROM);
        d.remove(LEGACY_TAG_T);
        d.remove(LEGACY_TAG_CD);
    }

    public static void setCooldown(ServerPlayer player, ServerLevel level) {
        int cd = Config.ROPE_TRAVERSAL_SURF_COOLDOWN_TICKS.getAsInt();
        if (cd > 0) {
            player.getPersistentData().putLong(TAG_CD, level.getGameTime() + cd);
        }
    }

    private static boolean onCooldown(ServerPlayer player, ServerLevel level) {
        long until = player.getPersistentData().getLong(TAG_CD);
        return until > level.getGameTime();
    }

    /**
     * @return SUCCESS if started, FAIL if blocked, PASS if nothing to do
     */
    public static InteractionResult tryStart(ServerLevel level, ServerPlayer player, BlockPos anchorPos) {
        if (!Config.ROPE_TRAVERSAL_SURF_ENABLED.getAsBoolean()) {
            return InteractionResult.PASS;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            ActionBarToastPayload.send(player, "projectisland.rope.surf.bad_world");
            return InteractionResult.FAIL;
        }
        if (isSurfing(player)) {
            if (surfTimedOut(player, level)) {
                clear(player);
            } else {
                ActionBarToastPayload.send(player, "projectisland.rope.surf.busy");
                return InteractionResult.FAIL;
            }
        }
        if (onCooldown(player, level)) {
            ActionBarToastPayload.send(player, "projectisland.rope.surf.cooldown");
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockEntity(anchorPos) instanceof RopeAnchorBlockEntity be) || !be.hasLink()) {
            ActionBarToastPayload.send(player, "projectisland.rope.surf.no_link");
            return InteractionResult.FAIL;
        }
        UUID lid = be.getLinkId();
        if (lid == null) {
            return InteractionResult.FAIL;
        }
        FloatingIslandSavedData data = IslandWorld.get(level);
        Optional<RopeLink> linkOpt = data.getRopeLink(lid);
        if (linkOpt.isEmpty()) {
            ActionBarToastPayload.send(player, "projectisland.rope.surf.no_link");
            return InteractionResult.FAIL;
        }
        RopeLink link = linkOpt.get();
        BlockPos other = link.otherAnchor(anchorPos);
        if (other == null) {
            ActionBarToastPayload.send(player, "projectisland.rope.surf.no_link");
            return InteractionResult.FAIL;
        }
        float minHp = (float) Config.ROPE_TRAVERSAL_SURF_MIN_HEALTH_FRACTION.getAsDouble();
        if (link.healthFraction() < minHp) {
            ActionBarToastPayload.send(player, "projectisland.rope.surf.low_health");
            return InteractionResult.FAIL;
        }
        double arc = RopeCurveUtil.arcLengthBlocks(anchorPos, other);
        if (arc < 0.75d) {
            return InteractionResult.PASS;
        }
        CompoundTag d = player.getPersistentData();
        d.putUUID(TAG_LINK, link.id());
        d.putLong(TAG_FROM, anchorPos.asLong());
        d.putDouble(TAG_T, 0.0);
        d.putLong(TAG_SURF_START, level.getGameTime());
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        // Void rescue skips ticks while surfing — last-safe feet would otherwise stay at pre-surf ground (e.g. a tree
        // anchor), then snap-back after a knockoff repeats a fall loop.
        FloatingIslandVoidRescue.clearLastSafeFeet(player);
        ActionBarToastPayload.send(player, "projectisland.rope.surf.started");
        return InteractionResult.SUCCESS;
    }

    private static boolean surfTimedOut(ServerPlayer player, ServerLevel level) {
        CompoundTag d = player.getPersistentData();
        if (!d.contains(TAG_SURF_START)) {
            return true;
        }
        long start = d.getLong(TAG_SURF_START);
        return level.getGameTime() - start > Config.ROPE_TRAVERSAL_SURF_MAX_DURATION_TICKS.getAsInt();
    }

    public static void tick(ServerPlayer player, ServerLevel level) {
        if (!Config.ROPE_TRAVERSAL_SURF_ENABLED.getAsBoolean()) {
            clear(player);
            return;
        }
        CompoundTag d = player.getPersistentData();
        migrateLegacyTagsIfNeeded(d);
        if (!d.hasUUID(TAG_LINK)) {
            return;
        }
        if (surfTimedOut(player, level)) {
            clear(player);
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            clear(player);
            return;
        }
        if (player.isShiftKeyDown()) {
            finish(player, level, false);
            return;
        }
        UUID lid = d.getUUID(TAG_LINK);
        BlockPos from = BlockPos.of(d.getLong(TAG_FROM));
        FloatingIslandSavedData data = IslandWorld.get(level);
        RopeLink link = data.getRopeLink(lid).orElse(null);
        if (link == null) {
            clear(player);
            return;
        }
        BlockPos other = link.otherAnchor(from);
        if (other == null
                || (!link.fromAnchorPos().equals(from) && !link.toAnchorPos().equals(from))) {
            clear(player);
            return;
        }
        float minHp = (float) Config.ROPE_TRAVERSAL_SURF_MIN_HEALTH_FRACTION.getAsDouble();
        if (link.healthFraction() < minHp) {
            ActionBarToastPayload.send(player, "projectisland.rope.surf.low_health");
            finish(player, level, false);
            return;
        }
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, from.getX(), from.getZ(), 2);
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, other.getX(), other.getZ(), 2);
        BlockState fs = level.getBlockState(from);
        BlockState os = level.getBlockState(other);
        if (fs.getBlock() != ProjectIslandContent.ROPE_ANCHOR || os.getBlock() != ProjectIslandContent.ROPE_ANCHOR) {
            clear(player);
            return;
        }

        double arc = RopeCurveUtil.arcLengthBlocks(from, other);
        if (arc < 1e-3) {
            clear(player);
            return;
        }
        double speed = Config.ROPE_TRAVERSAL_SURF_SPEED_BLOCKS_PER_SECOND.getAsDouble();
        double dt = speed / (arc * 20.0d);
        double t = d.getDouble(TAG_T) + dt;
        if (t >= 1.0) {
            landAtEnd(player, level, from, other);
            finish(player, level, true);
            return;
        }
        d.putDouble(TAG_T, t);
        Vec3 p = RopeCurveUtil.sagPoint(from, other, t);
        player.fallDistance = 0.0f;
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, Mth.floor(p.x), Mth.floor(p.z), 2);
        deferSurfTeleport(level.getServer(), player, level, p, "tick");
    }

    private static void landAtEnd(ServerPlayer player, ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 end = RopeCurveUtil.sagPoint(from, to, 1.0);
        player.fallDistance = 0.0f;
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, Mth.floor(end.x), Mth.floor(end.z), 2);
        deferSurfTeleport(level.getServer(), player, level, end, "end");
    }

    private static void deferSurfTeleport(
            @Nullable MinecraftServer server, ServerPlayer player, ServerLevel level, Vec3 p, String phase) {
        Runnable apply =
                () -> {
                    if (player.isRemoved() || !RopeSurfingState.isSurfing(player)) {
                        return;
                    }
                    if (player.serverLevel() != level) {
                        return;
                    }
                    try {
                        player.fallDistance = 0.0f;
                        player.setDeltaMovement(Vec3.ZERO);
                        player.teleportTo(
                                level, p.x, p.y, p.z, Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
                        player.setOnGround(true);
                        player.setDeltaMovement(Vec3.ZERO);
                    } catch (Exception e) {
                        ProjectIsland.LOGGER.warn("Rope surf teleport failed ({}): clear surf state for {}", phase, player, e);
                        RopeSurfingState.clear(player);
                    }
                };
        if (server == null) {
            apply.run();
        } else {
            server.execute(apply);
        }
    }

    private static void finish(ServerPlayer player, ServerLevel level, boolean completedRun) {
        if (!completedRun) {
            FloatingIslandVoidRescue.clearLastSafeFeet(player);
        }
        clear(player);
        if (completedRun) {
            ProjectIslandAdvancements.tryGrant(player, ProjectIslandAdvancements.ROPE_SURF_COMPLETE);
            setCooldown(player, level);
        }
    }

    /** Called when the player takes damage: cancel without applying cooldown. */
    public static void cancelOnDamage(ServerPlayer player) {
        if (isSurfing(player)) {
            FloatingIslandVoidRescue.clearLastSafeFeet(player);
            clear(player);
        }
    }
}
