package net.projectisland.island;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.Vec3;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.worldgen.FloatingIslandLayout;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * Phase 4: first-join starter island — region spiral for {@link IslandState#AVAILABLE}, atomic claim + starter-home
 * map entry, teleport to procedural island center (same anchor as {@link IslandHudServerSync} beacons).
 *
 * @return {@code true} if the player was kicked because no starter could be assigned and a kick message is configured.
 */
public final class FloatingIslandStarterPlacement {
    private FloatingIslandStarterPlacement() {}

    public static boolean handlePlayerLoggedIn(ServerPlayer player, ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return false;
        }
        FloatingIslandSavedData data = IslandWorld.get(level);
        UUID uuid = player.getUUID();
        Optional<FloatingIslandKey> home = data.getStarterHome(uuid);
        if (home.isPresent()) {
            maybeTeleportToStarterHome(player, level, home.get());
            return false;
        }
        if (!Config.STARTER_ISLAND_AUTO_ASSIGN_ENABLED.getAsBoolean()) {
            return false;
        }
        if (tryAssignFirstStarter(player, level, data)) {
            return false;
        }
        return tryKickOnStarterFailure(player);
    }

    private static boolean tryKickOnStarterFailure(ServerPlayer player) {
        String msg = Config.STARTER_ISLAND_FAILURE_KICK_MESSAGE.get();
        if (msg != null && !msg.isBlank()) {
            player.connection.disconnect(Component.literal(msg));
            return true;
        }
        ProjectIsland.LOGGER.warn(
                "FloatingIslandStarterPlacement: no AVAILABLE starter island within search radius for player {}",
                player.getGameProfile().getName());
        return false;
    }

    private static boolean tryAssignFirstStarter(ServerPlayer player, ServerLevel level, FloatingIslandSavedData data) {
        UUID owner = player.getUUID();
        long gameTime = level.getGameTime();
        int maxR = Config.STARTER_ISLAND_MAX_REGION_SEARCH_RADIUS.getAsInt();
        int minSep = Config.STARTER_ISLAND_MIN_REGION_SEPARATION.getAsInt();

        int originRx;
        int originRz;
        if (Config.STARTER_ISLAND_SEARCH_FROM_WORLD_SPAWN.getAsBoolean()) {
            BlockPos spawn = level.getSharedSpawnPos();
            int scx = spawn.getX() >> 4;
            int scz = spawn.getZ() >> 4;
            originRx = Mth.floorDiv(scx, FloatingIslandLayout.REGION_CHUNKS);
            originRz = Mth.floorDiv(scz, FloatingIslandLayout.REGION_CHUNKS);
        } else {
            BlockPos feet = player.blockPosition();
            originRx = Mth.floorDiv(feet.getX() >> 4, FloatingIslandLayout.REGION_CHUNKS);
            originRz = Mth.floorDiv(feet.getZ() >> 4, FloatingIslandLayout.REGION_CHUNKS);
        }

        for (int r = 0; r <= maxR; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int rx = originRx + dx;
                    int rz = originRz + dz;
                    if (!FloatingIslandLayout.regionHasIsland(rx, rz)) {
                        continue;
                    }
                    FloatingIslandKey key = new FloatingIslandKey(rx, rz);
                    if (minSep > 0 && !passesMinSeparation(key, minSep, data)) {
                        continue;
                    }
                    Optional<FloatingIslandKey> claimed = data.tryClaimStarterIsland(key, owner, gameTime);
                    if (claimed.isPresent()) {
                        teleportToIslandCenter(player, level, claimed.get());
                        if (Config.DEBUG_LOGGING.getAsBoolean()) {
                            ProjectIsland.LOGGER.debug(
                                    "Assigned starter island {} to {}", claimed.get(), player.getGameProfile().getName());
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean passesMinSeparation(FloatingIslandKey candidate, int minSep, FloatingIslandSavedData data) {
        for (FloatingIslandKey other : data.listStarterIslandKeys()) {
            int d = Math.max(
                    Math.abs(candidate.regionX() - other.regionX()), Math.abs(candidate.regionZ() - other.regionZ()));
            if (d < minSep) {
                return false;
            }
        }
        return true;
    }

    private static void maybeTeleportToStarterHome(ServerPlayer player, ServerLevel level, FloatingIslandKey key) {
        if (FloatingIslandVoidRescue.isSupportedOnIslandSurface(player, level)) {
            return;
        }
        // Do not yank explorers in dungeons / builds: only snap to starter when actually in the void-floor band.
        if (!FloatingIslandVoidRescue.isDeepVoidDangerZone(player, level)) {
            return;
        }
        if (teleportToIslandCenter(player, level, key)) {
            FloatingIslandVoidRescue.showVoidRescueActionBar(player);
        }
    }

    /** Feet position (center XZ, one above surface) for {@code key}, or empty if layout has no solid column there. */
    public static Optional<Vec3> optionalFeetAtIslandCenter(ServerLevel level, FloatingIslandKey key) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        FloatingIslandLayout.IslandParams params = new FloatingIslandLayout.IslandParams();
        FloatingIslandLayout.regionIsland(key.regionX(), key.regionZ(), params);
        int wx = params.centerX;
        int wz = params.centerZ;
        int top = FloatingIslandsChunkGenerator.islandSurfaceBlockY(gen, wx, wz, minY, maxY);
        if (top == Integer.MIN_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new Vec3(wx + 0.5d, top + 1.0d, wz + 0.5d));
    }

    /**
     * @return {@code true} if a surface was found and the player was teleported.
     */
    public static boolean teleportToIslandCenter(ServerPlayer player, ServerLevel level, FloatingIslandKey key) {
        Optional<Vec3> vecOpt = optionalFeetAtIslandCenter(level, key);
        if (vecOpt.isEmpty()) {
            ProjectIsland.LOGGER.warn(
                    "FloatingIslandStarterPlacement: layout had no surface at center for starter island {}",
                    key);
            return false;
        }
        Vec3 vec = vecOpt.get();
        IslandChunkLoader.ensureChunksAroundWorldBlock(level, Mth.floor(vec.x), Mth.floor(vec.z));
        player.teleportTo(level, vec.x, vec.y, vec.z, player.getYRot(), player.getXRot());
        return true;
    }
}
