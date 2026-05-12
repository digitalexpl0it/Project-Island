package net.projectisland.island;

import java.util.Optional;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.Vec3;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.network.ActionBarToastPayload;
import net.projectisland.worldgen.FloatingIslandLayout;

/**
 * Void rescue for the floating-islands overworld: while unsupported, nothing runs until the **void-floor band** (see
 * {@link Config#VOID_RESCUE_TRIGGER_BLOCKS_ABOVE_MIN_Y}). There, optional {@linkplain Config#VOID_RESCUE_SNAP_TO_LAST_SAFE_ENABLED
 * last-safe snap} may run, then {@linkplain #runBedStarterOrRelocate bed → starter home → nearest island}. Join /
 * dimension change and rope knockoff use {@linkplain #rescueToBedStarterOrNearestIsland(ServerPlayer, ServerLevel)}
 * (bed / starter / nearest island) when unsupported at any height.
 */
public final class FloatingIslandVoidRescue {
    private FloatingIslandVoidRescue() {}

    /** Marked while the player is in open void (not supported); cleared after a floor rescue or when back on surface. */
    private static final String TAG_VOID_FALLING = ProjectIsland.MOD_ID + "_void_falling";

    private static final String TAG_LAST_SAFE_FEET = ProjectIsland.MOD_ID + "_last_safe_feet";

    private static final String TAG_LAST_SAFE_SNAP_COOLDOWN = ProjectIsland.MOD_ID + "_last_safe_snap_cd";

    /**
     * After a floor-band rescue that did **not** move the player meaningfully and they are still unsupported, blocks
     * {@link #runBedStarterOrRelocate} for a few ticks so we do not teleport every tick (rubber-band with last-safe
     * cooldown off snap path).
     */
    private static final String TAG_FLOOR_RESCUE_APPLY_CD = ProjectIsland.MOD_ID + "_void_floor_rescue_apply_cd";

    private static final int FLOOR_RESCUE_APPLY_TICKS = 18;

    private static final double FLOOR_RESCUE_UNMOVED_EPSILON = 0.35d * 0.35d;

    /** Throttles random rescue action-bar lines so a logic bug cannot spam the client every tick. */
    private static final String TAG_RESCUE_TOAST_GAME_TIME = ProjectIsland.MOD_ID + "_void_rescue_toast_at";

    private static final int RESCUE_TOAST_MIN_INTERVAL_TICKS = 100;

    private static final int MAX_CHUNK_RADIUS = 80;
    /** Matches the island search radius: {@value MAX_CHUNK_RADIUS} chunks → region rings. */
    private static final int MAX_REGION_CHEBYSHEV =
            (MAX_CHUNK_RADIUS + FloatingIslandLayout.REGION_CHUNKS - 1) / FloatingIslandLayout.REGION_CHUNKS;
    /** Last-resort sample offsets inside a chunk (often near rims — only used if center-based rescue failed). */
    private static final int[] LOCAL_X = {8, 2, 14, 8, 8};
    private static final int[] LOCAL_Z = {8, 8, 8, 2, 14};

    private static final String[] RESCUE_ACTIONBAR_KEYS = {
        "projectisland.rescue.sudden_death",
        "projectisland.rescue.got_you",
        "projectisland.rescue.life_saved",
        "projectisland.rescue.plucked",
        "projectisland.rescue.gravity",
        "projectisland.rescue.airship_insurance",
    };

    /** Hotbar-style action bar, same channel as {@link net.projectisland.content.HarpoonGunItem} feedback. */
    public static void showVoidRescueActionBar(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) {
            return;
        }
        long now = sl.getGameTime();
        CompoundTag d = player.getPersistentData();
        long last = d.getLong(TAG_RESCUE_TOAST_GAME_TIME);
        if (now - last < RESCUE_TOAST_MIN_INTERVAL_TICKS) {
            return;
        }
        d.putLong(TAG_RESCUE_TOAST_GAME_TIME, now);
        String key = RESCUE_ACTIONBAR_KEYS[player.getRandom().nextInt(RESCUE_ACTIONBAR_KEYS.length)];
        ActionBarToastPayload.send(player, key);
    }

    /** Clears fall velocity after a rescue/starter teleport without forcing {@code onGround} (used before bbox validation). */
    public static void clearRescueMotionNoGround(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.fallDistance = 0.0f;
    }

    /** Clears downward velocity after a rescue/starter teleport so join momentum does not carry through open sky. */
    public static void stabilizeAfterIslandTeleport(ServerPlayer player) {
        clearRescueMotionNoGround(player);
        player.setOnGround(true);
    }

    /**
     * Same rules as {@link #isSupportedOnIslandSurface} for gameplay ticks, but **without** the {@link Player#onGround()}
     * shortcut — that flag can be wrong for one tick right after {@code setOnGround(true)}, which made starter / void
     * teleports report success while still in open air beside an island.
     */
    public static boolean hasBboxIslandSurfaceSupport(ServerPlayer player, ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return true;
        }
        if (player.isSpectator()) {
            return true;
        }
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        double ey = player.getY();
        var feet = player.blockPosition();
        if (!FloatingIslandSurfaceSupport.columnSupportsFeet(level, feet.getX(), feet.getZ(), ey, minY, maxY)) {
            return false;
        }
        return FloatingIslandSurfaceSupport.bboxSupported(level, player.getBoundingBox(), ey, minY, maxY);
    }

    /**
     * Teleports to feet, clears motion, then only sets {@code onGround} if {@link #hasBboxIslandSurfaceSupport} passes.
     *
     * @return {@code true} if the player ended on bbox-valid island footing
     */
    /** Sub-block Y nudges so thin floors / post-teleport collision probes match vanilla stand height. */
    private static final double[] TELEPORT_FOOT_Y_NUDGES = {
        0.0d, 0.0625d, 0.125d, 0.25d, 0.5d, 1.0d, -0.0625d, -0.125d, -0.25d
    };

    public static boolean teleportToFeetWithIslandBboxCheck(
            ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, Mth.floor(x), Mth.floor(z), 3);
        for (double dy : TELEPORT_FOOT_Y_NUDGES) {
            double tryY = y + dy;
            player.teleportTo(level, x, tryY, z, Set.<RelativeMovement>of(), yRot, xRot);
            clearRescueMotionNoGround(player);
            if (hasBboxIslandSurfaceSupport(player, level)) {
                player.setOnGround(true);
                return true;
            }
        }
        return false;
    }

    /**
     * Absolute teleport with an empty {@link RelativeMovement} set (same pattern as {@link RopeSurfingState} surf moves)
     * so the server position tracker matches the client, then {@linkplain #stabilizeAfterIslandTeleport stabilization}.
     */
    public static void teleportAbsoluteSync(
            ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        player.teleportTo(level, x, y, z, Set.<RelativeMovement>of(), yRot, xRot);
        stabilizeAfterIslandTeleport(player);
    }

    /**
     * Vanilla increments {@code aboveGroundTickCount} / {@code clientIsFloating} on the play connection when
     * {@code allow-flight=false} and the client moves without ground support — long void falls hit
     * {@code MAXIMUM_FLYING_TICKS} and disconnect ("floating too long" / flying kick). Reset each tick while we know
     * the player is in intentional unsupported void on the floating overworld.
     */
    public static void maybeResetVanillaFloatingPacketCounters(ServerPlayer player, ServerLevel level) {
        if (!Config.VOID_RESCUE_RESET_VANILLA_FLOATING_PACKET_COUNTERS.getAsBoolean()) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || player.getAbilities().flying) {
            return;
        }
        if (RopeSurfingState.isSurfing(player)) {
            return;
        }
        if (isSupportedOnIslandSurface(player, level)) {
            return;
        }
        if (!(player.connection instanceof ServerGamePacketListenerImpl conn)) {
            return;
        }
        conn.aboveGroundTickCount = 0;
        conn.aboveGroundVehicleTickCount = 0;
        conn.clientIsFloating = false;
        conn.clientVehicleIsFloating = false;
    }

    /** Clears saved last-safe feet (e.g. dimension change / respawn). */
    public static void clearLastSafeFeet(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(TAG_LAST_SAFE_FEET);
        data.remove(TAG_LAST_SAFE_SNAP_COOLDOWN);
        data.remove(ProjectIsland.MOD_ID + "_void_floor_rescue_fail_cd");
        data.remove(TAG_FLOOR_RESCUE_APPLY_CD);
        data.remove(TAG_RESCUE_TOAST_GAME_TIME);
    }

    /**
     * While supported, saves feet for {@link #trySnapToLastSafeFeet}. When unsupported above the void-floor band,
     * does nothing (no mid-air snap, no bed/starter/relocate). In the band: optional last-safe snap, then
     * {@link #runBedStarterOrRelocate}.
     */
    public static void tickVoidRescue(ServerPlayer player, ServerLevel level) {
        if (!Config.VOID_RESCUE_EACH_TICK.getAsBoolean()) {
            return;
        }
        if (RopeSurfingState.isSurfing(player)) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        int cd = data.getInt(TAG_LAST_SAFE_SNAP_COOLDOWN);
        if (cd > 0) {
            data.putInt(TAG_LAST_SAFE_SNAP_COOLDOWN, cd - 1);
        }
        if (isSupportedOnIslandSurface(player, level)) {
            data.remove(TAG_VOID_FALLING);
            data.remove(TAG_FLOOR_RESCUE_APPLY_CD);
            saveLastSafeFeet(player, data);
            return;
        }
        if (!isDeepVoidDangerZone(player, level)) {
            data.remove(TAG_VOID_FALLING);
            data.remove(TAG_FLOOR_RESCUE_APPLY_CD);
            return;
        }
        if (Config.VOID_RESCUE_SNAP_TO_LAST_SAFE_ENABLED.getAsBoolean()
                && trySnapToLastSafeFeet(player, level, data)) {
            return;
        }
        int applyCd = data.getInt(TAG_FLOOR_RESCUE_APPLY_CD);
        if (applyCd > 0) {
            data.putInt(TAG_FLOOR_RESCUE_APPLY_CD, applyCd - 1);
            return;
        }
        Vec3 pre = player.position();
        runBedStarterOrRelocate(player, level, data);
        if (!isSupportedOnIslandSurface(player, level)
                && player.position().distanceToSqr(pre) < FLOOR_RESCUE_UNMOVED_EPSILON) {
            data.putInt(TAG_FLOOR_RESCUE_APPLY_CD, FLOOR_RESCUE_APPLY_TICKS);
        }
    }

    /**
     * {@linkplain #runBedStarterOrRelocate Void-floor rescue} delegates here. **Bed / anchor** in this dimension always
     * wins: after teleport we do not fall through to starter just because {@link #isSupportedOnIslandSurface} is still
     * false for a tick (rim / thin floor / bed on non-procedural footing).
     */
    public static void rescueToBedStarterOrNearestIsland(ServerPlayer player, ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        if (isSupportedOnIslandSurface(player, level)) {
            return;
        }
        Optional<Vec3> bed = FloatingIslandSurfaceSupport.findRespawnStandUp(level, player);
        if (bed.isPresent()) {
            Vec3 p = bed.get();
            IslandChunkLoader.ensureChunksAroundWorldBlock(level, (int) Mth.floor(p.x), (int) Mth.floor(p.z), 3);
            teleportAbsoluteSync(player, level, p.x, p.y, p.z, player.getYRot(), player.getXRot());
            showVoidRescueActionBar(player);
            return;
        }
        Optional<FloatingIslandKey> home = IslandWorld.get(level).getStarterHome(player.getUUID());
        if (home.isPresent()) {
            if (FloatingIslandStarterPlacement.teleportToIslandCenter(player, level, home.get())) {
                showVoidRescueActionBar(player);
                return;
            }
            // Critical: do not spiral from the player's current XZ (e.g. world spawn / void beside another island) —
            // that lands them on the wrong island while the HUD still reflects their starter region.
            relocatePlayerFromVoidAroundStarterHome(player, level, home.get());
            return;
        }
        relocatePlayerFromVoid(player, level);
    }

    /**
     * Bottom-band rescue only: {@link #rescueToBedStarterOrNearestIsland} after optional last-safe snap (same chain as
     * join / dimension / rope knockoff).
     */
    private static void runBedStarterOrRelocate(ServerPlayer player, ServerLevel level, CompoundTag data) {
        data.putBoolean(TAG_VOID_FALLING, true);
        try {
            rescueToBedStarterOrNearestIsland(player, level);
        } finally {
            data.remove(TAG_VOID_FALLING);
        }
    }

    private static void saveLastSafeFeet(ServerPlayer player, CompoundTag data) {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", player.getX());
        t.putDouble("y", player.getY());
        t.putDouble("z", player.getZ());
        t.putFloat("yr", player.getYRot());
        data.put(TAG_LAST_SAFE_FEET, t);
    }

    /**
     * Only called from {@link #tickVoidRescue} when the player is already in the void-floor band. If the saved column
     * is still bad after teleport, {@link #rescueToBedStarterOrNearestIsland} runs only while still in that band.
     *
     * @return {@code true} if this tick applied a snap attempt (caller should not also run floor rescue this tick).
     */
    private static boolean trySnapToLastSafeFeet(ServerPlayer player, ServerLevel level, CompoundTag data) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (player.getAbilities().flying) {
            return false;
        }
        if (player.isFallFlying()) {
            return false;
        }
        if (data.getInt(TAG_LAST_SAFE_SNAP_COOLDOWN) > 0) {
            return false;
        }
        if (!data.contains(TAG_LAST_SAFE_FEET, CompoundTag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag feet = data.getCompound(TAG_LAST_SAFE_FEET);
        double sx = feet.getDouble("x");
        double sy = feet.getDouble("y");
        double sz = feet.getDouble("z");
        float yr = feet.contains("yr", CompoundTag.TAG_FLOAT) ? feet.getFloat("yr") : player.getYRot();
        int minFall = Config.VOID_RESCUE_SNAP_TO_LAST_SAFE_MIN_FALL_BLOCKS.getAsInt();
        if (player.getY() > sy - minFall) {
            return false;
        }
        if (player.getDeltaMovement().y > 0.15d) {
            return false;
        }
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, Mth.floor(sx), Mth.floor(sz), 3);
        boolean snapOk = teleportToFeetWithIslandBboxCheck(player, level, sx, sy, sz, yr, player.getXRot());
        int cooldown = Config.VOID_RESCUE_SNAP_TO_LAST_SAFE_COOLDOWN_TICKS.getAsInt();
        if (cooldown > 0) {
            data.putInt(TAG_LAST_SAFE_SNAP_COOLDOWN, cooldown);
        }
        if (snapOk) {
            showVoidRescueActionBar(player);
            return true;
        }
        // Snap target was a bad column (e.g. tree canopy / rope rim). Only chain into relocate from the void-floor band
        // so we never yank to an island at overworld height while still under the island / in a structure.
        data.remove(TAG_LAST_SAFE_FEET);
        if (isDeepVoidDangerZone(player, level)) {
            rescueToBedStarterOrNearestIsland(player, level);
        }
        return true;
    }

    /**
     * True when the player should be pulled out of the void. Uses the entity {@linkplain Player#getBoundingBox() bounding
     * box} on XZ plus a one-block margin: at island rims {@code floor(getX())} often lies in a void column while the
     * player still stands on the edge block.
     */
    public static boolean needsVoidRelocate(ServerPlayer player, ServerLevel level) {
        return !isSupportedOnIslandSurface(player, level);
    }

    /**
     * {@code true} when the player has real ground under their feet column and the usual footprint probe passes (see
     * {@link #hasBboxIslandSurfaceSupport}), or vanilla-style {@code onGround} while not flying (thin floors / stairs).
     */
    public static boolean isSupportedOnIslandSurface(ServerPlayer player, ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return true;
        }
        if (player.isSpectator()) {
            return true;
        }
        // Stairs / thin floors often fail the bbox collision probe for one tick; ground contact matches player reality.
        if (!player.getAbilities().flying && player.onGround()) {
            return true;
        }
        return hasBboxIslandSurfaceSupport(player, level);
    }

    /**
     * True when feet Y is at or below {@code minBuildHeight + voidRescueTriggerBlocksAboveMinY} — the “vice” near the
     * world bottom where void rescue should run.
     */
    public static boolean isDeepVoidDangerZone(ServerPlayer player, ServerLevel level) {
        int minY = level.getMinBuildHeight();
        int band = Math.max(0, Config.VOID_RESCUE_TRIGGER_BLOCKS_ABOVE_MIN_Y.getAsInt());
        return player.getY() <= (double) minY + band;
    }

    public static void relocatePlayerFromVoid(ServerPlayer player, ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        if (isSupportedOnIslandSurface(player, level)) {
            return;
        }
        int originX = Mth.floor(player.getX());
        int originZ = Mth.floor(player.getZ());
        for (int r = 0; r <= MAX_REGION_CHEBYSHEV; r++) {
            for (int drx = -r; drx <= r; drx++) {
                for (int drz = -r; drz <= r; drz++) {
                    if (Math.max(Math.abs(drx), Math.abs(drz)) != r) {
                        continue;
                    }
                    if (tryTeleportToRegionCenterRescue(player, level, drx, drz)) {
                        return;
                    }
                }
            }
        }
        fallbackRelocateToChunkSamplePoints(player, level, originX, originZ);
    }

    /**
     * Same as {@link #relocatePlayerFromVoid} but the region spiral is anchored on the player’s **starter home**
     * region (then neighbors), not their current position — used when {@code teleportToIslandCenter} failed so we do
     * not send them to “nearest land from world spawn”.
     */
    private static void relocatePlayerFromVoidAroundStarterHome(
            ServerPlayer player, ServerLevel level, FloatingIslandKey home) {
        if (isSupportedOnIslandSurface(player, level)) {
            return;
        }
        int crx = home.regionX();
        int crz = home.regionZ();
        for (int r = 0; r <= MAX_REGION_CHEBYSHEV; r++) {
            for (int drx = -r; drx <= r; drx++) {
                for (int drz = -r; drz <= r; drz++) {
                    if (Math.max(Math.abs(drx), Math.abs(drz)) != r) {
                        continue;
                    }
                    if (tryTeleportToIslandRegionCenter(player, level, crx + drx, crz + drz)) {
                        return;
                    }
                }
            }
        }
        relocatePlayerFromVoid(player, level);
    }

    /**
     * Teleport to procedural center feet for island region ({@code rx},{@code rz}) if a valid column exists and bbox
     * footing passes.
     */
    private static boolean tryTeleportToIslandRegionCenter(ServerPlayer player, ServerLevel level, int rx, int rz) {
        if (!FloatingIslandLayout.regionHasIsland(rx, rz)) {
            return false;
        }
        FloatingIslandKey key = new FloatingIslandKey(rx, rz);
        Optional<Vec3> feet = FloatingIslandStarterPlacement.optionalFeetAtIslandCenter(level, key);
        if (feet.isEmpty()) {
            return false;
        }
        Vec3 f = feet.get();
        if (teleportToFeetWithIslandBboxCheck(player, level, f.x, f.y, f.z, player.getYRot(), player.getXRot())) {
            showVoidRescueActionBar(player);
            return true;
        }
        return false;
    }

    /**
     * Prefer each region’s procedural island center (same anchor as the starter / HUD) — edge samples in
     * {@link #fallbackRelocateToChunkSamplePoints} often land on rims where the next tick still looks “unsupported”,
     * causing fall → rescue loops and disconnects.
     */
    private static boolean tryTeleportToRegionCenterRescue(ServerPlayer player, ServerLevel level, int dRegionX, int dRegionZ) {
        int chunkX = Mth.floorDiv(Mth.floor(player.getX()), 16);
        int chunkZ = Mth.floorDiv(Mth.floor(player.getZ()), 16);
        int rcx = Mth.floorDiv(chunkX, FloatingIslandLayout.REGION_CHUNKS);
        int rcz = Mth.floorDiv(chunkZ, FloatingIslandLayout.REGION_CHUNKS);
        return tryTeleportToIslandRegionCenter(player, level, rcx + dRegionX, rcz + dRegionZ);
    }

    private static void fallbackRelocateToChunkSamplePoints(
            ServerPlayer player, ServerLevel level, int originX, int originZ) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int ox = originX >> 4;
        int oz = originZ >> 4;

        for (int r = 0; r <= MAX_CHUNK_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = ox + dx;
                    int cz = oz + dz;
                    int bx = cx << 4;
                    int bz = cz << 4;
                    for (int s = 0; s < LOCAL_X.length; s++) {
                        int wx = bx + LOCAL_X[s];
                        int wz = bz + LOCAL_Z[s];
                        Optional<Vec3> clear =
                                FloatingIslandStarterPlacement.findOpenFeetNear(
                                        level, generator, wx, wz, minY, maxY, 24);
                        if (clear.isEmpty()) {
                            continue;
                        }
                        Vec3 p = clear.get();
                        if (teleportToFeetWithIslandBboxCheck(
                                player, level, p.x, p.y, p.z, player.getYRot(), player.getXRot())) {
                            showVoidRescueActionBar(player);
                            return;
                        }
                    }
                }
            }
        }

        ProjectIsland.LOGGER.warn(
                "Could not find a supported island surface within {} chunks of block {}, {} in overworld — player left in void",
                MAX_CHUNK_RADIUS,
                originX,
                originZ);
    }

    public static Optional<Vec3> findNearestIslandFeet(ServerLevel level, double x, double z) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return Optional.empty();
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int originX = Mth.floor(x);
        int originZ = Mth.floor(z);
        int chunkX = Mth.floorDiv(originX, 16);
        int chunkZ = Mth.floorDiv(originZ, 16);
        int rcx = Mth.floorDiv(chunkX, FloatingIslandLayout.REGION_CHUNKS);
        int rcz = Mth.floorDiv(chunkZ, FloatingIslandLayout.REGION_CHUNKS);

        for (int r = 0; r <= MAX_REGION_CHEBYSHEV; r++) {
            for (int drx = -r; drx <= r; drx++) {
                for (int drz = -r; drz <= r; drz++) {
                    if (Math.max(Math.abs(drx), Math.abs(drz)) != r) {
                        continue;
                    }
                    int arx = rcx + drx;
                    int arz = rcz + drz;
                    if (!FloatingIslandLayout.regionHasIsland(arx, arz)) {
                        continue;
                    }
                    Optional<Vec3> o = FloatingIslandStarterPlacement.optionalFeetAtIslandCenter(
                            level, new FloatingIslandKey(arx, arz));
                    if (o.isPresent() && columnFeetPlausible(level, o.get(), minY, maxY)) {
                        return o;
                    }
                }
            }
        }

        int ox = originX >> 4;
        int oz = originZ >> 4;
        for (int r = 0; r <= MAX_CHUNK_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = ox + dx;
                    int cz = oz + dz;
                    int bx = cx << 4;
                    int bz = cz << 4;
                    for (int s = 0; s < LOCAL_X.length; s++) {
                        int wx = bx + LOCAL_X[s];
                        int wz = bz + LOCAL_Z[s];
                        Optional<Vec3> clear =
                                FloatingIslandStarterPlacement.findOpenFeetNear(
                                        level, generator, wx, wz, minY, maxY, 24);
                        if (clear.isPresent() && columnFeetPlausible(level, clear.get(), minY, maxY)) {
                            return clear;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean columnFeetPlausible(ServerLevel level, Vec3 feet, int minY, int maxY) {
        return FloatingIslandSurfaceSupport.columnSupportsFeet(
                level, Mth.floor(feet.x), Mth.floor(feet.z), feet.y, minY, maxY);
    }
}
