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

/**
 * Void rescue for the floating-islands overworld: {@linkplain #tickVoidRescue(ServerPlayer, ServerLevel) per-tick}
 * path fires **once per fall** when the player reaches the **void-floor band** (see {@link Config#VOID_RESCUE_TRIGGER_BLOCKS_ABOVE_MIN_Y})
 * while not supported. Join / dimension change still uses {@linkplain #relocatePlayerFromVoid(ServerPlayer, ServerLevel)} when unsupported at any height.
 */
public final class FloatingIslandVoidRescue {
    private FloatingIslandVoidRescue() {}

    /** Marked while the player is in open void (not supported); cleared after a floor rescue or when back on surface. */
    private static final String TAG_VOID_FALLING = ProjectIsland.MOD_ID + "_void_falling";

    private static final int MAX_CHUNK_RADIUS = 80;
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

    /**
     * Once per void fall: when **unsupported** and feet are in the **deep void band** (near {@link Level#getMinBuildHeight()}),
     * try **bed / respawn anchor**, then starter island center, then nearest procedural surface. Does not run while you
     * are merely underground or on structures well above the world floor.
     */
    public static void tickVoidRescue(ServerPlayer player, ServerLevel level) {
        if (!Config.VOID_RESCUE_EACH_TICK.getAsBoolean()) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (isSupportedOnIslandSurface(player, level)) {
            data.remove(TAG_VOID_FALLING);
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
            IslandChunkLoader.ensureChunksAroundWorldBlock(level, (int) Mth.floor(p.x), (int) Mth.floor(p.z));
            player.teleportTo(level, p.x, p.y, p.z, player.getYRot(), player.getXRot());
            if (isSupportedOnIslandSurface(player, level)) {
                data.remove(TAG_VOID_FALLING);
                showVoidRescueActionBar(player);
                return;
            }
        }
        Optional<FloatingIslandKey> home = IslandWorld.get(level).getStarterHome(player.getUUID());
        if (home.isPresent()) {
            if (FloatingIslandStarterPlacement.teleportToIslandCenter(player, level, home.get())
                    && isSupportedOnIslandSurface(player, level)) {
                data.remove(TAG_VOID_FALLING);
                showVoidRescueActionBar(player);
                return;
            }
        }
        relocatePlayerFromVoid(player, level);
        data.remove(TAG_VOID_FALLING);
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
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        int originX = Mth.floor(player.getX());
        int originZ = Mth.floor(player.getZ());
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
                        int top = net.projectisland.worldgen.FloatingIslandsChunkGenerator.islandSurfaceBlockY(
                                generator, wx, wz, minY, maxY);
                        if (top != Integer.MIN_VALUE) {
                            IslandChunkLoader.ensureChunksAroundWorldBlock(level, wx, wz);
                            player.teleportTo(level, wx + 0.5d, top + 1.0d, wz + 0.5d, player.getYRot(), player.getXRot());
                            showVoidRescueActionBar(player);
                            return;
                        }
                    }
                }
            }
        }

        ProjectIsland.LOGGER.warn(
                "Could not find an island surface within {} chunks of block {}, {} in overworld — player left at void position",
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
                        int top = net.projectisland.worldgen.FloatingIslandsChunkGenerator.islandSurfaceBlockY(
                                generator, wx, wz, minY, maxY);
                        if (top != Integer.MIN_VALUE) {
                            IslandChunkLoader.ensureChunksAroundWorldBlock(level, wx, wz);
                            return Optional.of(new Vec3(wx + 0.5d, top + 1.0d, wz + 0.5d));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
