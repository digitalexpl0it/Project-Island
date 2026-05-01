package net.projectisland.island;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.Vec3;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.network.ActionBarToastPayload;
import net.projectisland.worldgen.FloatingIslandLayout;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * Void rescue for the floating-islands overworld: {@linkplain #tickVoidRescue(ServerPlayer, ServerLevel) per-tick} can
 * {@linkplain Config#VOID_RESCUE_SNAP_TO_LAST_SAFE_ENABLED snap} you to the last supported feet position mid-fall, then
 * (if needed) when you reach the **void-floor band** (see {@link Config#VOID_RESCUE_TRIGGER_BLOCKS_ABOVE_MIN_Y}) runs bed /
 * starter / nearest island. Join / dimension change still uses {@linkplain #relocatePlayerFromVoid(ServerPlayer, ServerLevel)}
 * when unsupported at any height.
 */
public final class FloatingIslandVoidRescue {
    private FloatingIslandVoidRescue() {}

    /** Marked while the player is in open void (not supported); cleared after a floor rescue or when back on surface. */
    private static final String TAG_VOID_FALLING = ProjectIsland.MOD_ID + "_void_falling";

    private static final String TAG_LAST_SAFE_FEET = ProjectIsland.MOD_ID + "_last_safe_feet";

    private static final String TAG_LAST_SAFE_SNAP_COOLDOWN = ProjectIsland.MOD_ID + "_last_safe_snap_cd";

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
        String key = RESCUE_ACTIONBAR_KEYS[player.getRandom().nextInt(RESCUE_ACTIONBAR_KEYS.length)];
        ActionBarToastPayload.send(player, key);
    }

    /** Clears downward velocity after a rescue/starter teleport so join momentum does not carry through open sky. */
    public static void stabilizeAfterIslandTeleport(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    /** Clears saved last-safe feet (e.g. dimension change / respawn). */
    public static void clearLastSafeFeet(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(TAG_LAST_SAFE_FEET);
        data.remove(TAG_LAST_SAFE_SNAP_COOLDOWN);
    }

    /**
     * While supported, saves feet for {@link #trySnapToLastSafeFeet}. When unsupported: optional last-safe snap, then
     * when feet enter the **deep void band**, bed → starter → {@link #relocatePlayerFromVoid}.
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
            saveLastSafeFeet(player, data);
            return;
        }
        if (Config.VOID_RESCUE_SNAP_TO_LAST_SAFE_ENABLED.getAsBoolean()
                && trySnapToLastSafeFeet(player, level, data)) {
            return;
        }
        if (!isDeepVoidDangerZone(player, level)) {
            data.remove(TAG_VOID_FALLING);
            return;
        }
        data.putBoolean(TAG_VOID_FALLING, true);
        Optional<Vec3> bed = FloatingIslandSurfaceSupport.findRespawnStandUp(level, player);
        if (bed.isPresent()) {
            Vec3 p = bed.get();
            IslandChunkLoader.ensureChunksAroundWorldBlock(level, (int) Mth.floor(p.x), (int) Mth.floor(p.z), 3);
            player.teleportTo(level, p.x, p.y, p.z, player.getYRot(), player.getXRot());
            stabilizeAfterIslandTeleport(player);
            if (isSupportedOnIslandSurface(player, level)) {
                data.remove(TAG_VOID_FALLING);
                showVoidRescueActionBar(player);
                return;
            }
        }
        Optional<FloatingIslandKey> home = IslandWorld.get(level).getStarterHome(player.getUUID());
        if (home.isPresent()) {
            if (FloatingIslandStarterPlacement.teleportToIslandCenter(player, level, home.get())) {
                data.remove(TAG_VOID_FALLING);
                showVoidRescueActionBar(player);
                return;
            }
        }
        relocatePlayerFromVoid(player, level);
        data.remove(TAG_VOID_FALLING);
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
     * If the player fell far enough below their last saved supported feet Y, teleport back there (void between islands).
     *
     * @return {@code true} if this tick handled rescue (snap or handoff to {@link #relocatePlayerFromVoid}).
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
        player.teleportTo(level, sx, sy, sz, yr, player.getXRot());
        stabilizeAfterIslandTeleport(player);
        int cooldown = Config.VOID_RESCUE_SNAP_TO_LAST_SAFE_COOLDOWN_TICKS.getAsInt();
        if (cooldown > 0) {
            data.putInt(TAG_LAST_SAFE_SNAP_COOLDOWN, cooldown);
        }
        if (isSupportedOnIslandSurface(player, level)) {
            showVoidRescueActionBar(player);
            return true;
        }
        relocatePlayerFromVoid(player, level);
        showVoidRescueActionBar(player);
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
     * {@code true} if any column under the player's horizontal footprint (with margin) has procedural island surface
     * (including tall structures above that surface) or solid footing from the loaded world.
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
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        double ey = player.getY();
        return FloatingIslandSurfaceSupport.bboxSupported(level, generator, player.getBoundingBox(), ey, minY, maxY);
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
     * Prefer each region’s procedural island center (same anchor as the starter / HUD) — edge samples in
     * {@link #fallbackRelocateToChunkSamplePoints} often land on rims where the next tick still looks “unsupported”,
     * causing fall → rescue loops and disconnects.
     */
    private static boolean tryTeleportToRegionCenterRescue(ServerPlayer player, ServerLevel level, int dRegionX, int dRegionZ) {
        int chunkX = Mth.floorDiv(Mth.floor(player.getX()), 16);
        int chunkZ = Mth.floorDiv(Mth.floor(player.getZ()), 16);
        int rcx = Mth.floorDiv(chunkX, FloatingIslandLayout.REGION_CHUNKS);
        int rcz = Mth.floorDiv(chunkZ, FloatingIslandLayout.REGION_CHUNKS);
        int rx = rcx + dRegionX;
        int rz = rcz + dRegionZ;
        if (!FloatingIslandLayout.regionHasIsland(rx, rz)) {
            return false;
        }
        FloatingIslandKey key = new FloatingIslandKey(rx, rz);
        Optional<Vec3> feet = FloatingIslandStarterPlacement.optionalFeetAtIslandCenter(level, key);
        if (feet.isEmpty()) {
            return false;
        }
        Vec3 f = feet.get();
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, Mth.floor(f.x), Mth.floor(f.z), 3);
        player.teleportTo(level, f.x, f.y, f.z, player.getYRot(), player.getXRot());
        stabilizeAfterIslandTeleport(player);
        if (isSupportedOnIslandSurface(player, level)) {
            showVoidRescueActionBar(player);
            return true;
        }
        return false;
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
                        IslandChunkLoader.ensureChunksAroundWorldBlock(level, Mth.floor(p.x), Mth.floor(p.z), 3);
                        player.teleportTo(level, p.x, p.y, p.z, player.getYRot(), player.getXRot());
                        stabilizeAfterIslandTeleport(player);
                        if (isSupportedOnIslandSurface(player, level)) {
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
                    if (o.isPresent()
                            && columnFeetPlausible(
                                    level, generator, o.get(), minY, maxY)) {
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
                        if (clear.isPresent()
                                && columnFeetPlausible(level, generator, clear.get(), minY, maxY)) {
                            return clear;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean columnFeetPlausible(
            ServerLevel level, ChunkGenerator generator, Vec3 feet, int minY, int maxY) {
        return FloatingIslandSurfaceSupport.columnSupportsFeet(
                level, generator, Mth.floor(feet.x), Mth.floor(feet.z), feet.y, minY, maxY);
    }
}
