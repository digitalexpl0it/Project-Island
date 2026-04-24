package net.projectisland;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * When a player is in the floating-islands overworld and their feet are over void (first join, bad spawn, or
 * fixed teleport Y), move them onto the nearest procedural island column.
 */
public final class FloatingIslandsSpawnEvents {
    private FloatingIslandsSpawnEvents() {}

    private static final int MAX_CHUNK_RADIUS = 80;

    /** A few samples per chunk so small islands away from chunk center are still found. */
    private static final int[] LOCAL_X = {8, 2, 14, 8, 8};
    private static final int[] LOCAL_Z = {8, 8, 8, 2, 14};

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnEvents::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnEvents::onPlayerLoggedIn);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        maybeRelocateFromVoid(player, level);
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getTo().equals(Level.OVERWORLD)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }
        maybeRelocateFromVoid(player, level);
    }

    private static void maybeRelocateFromVoid(ServerPlayer player, ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        int originX = Mth.floor(player.getX());
        int originZ = Mth.floor(player.getZ());
        int topHere = FloatingIslandsChunkGenerator.islandSurfaceBlockY(generator, originX, originZ, minY, maxY);
        if (topHere != Integer.MIN_VALUE) {
            double y = player.getY();
            if (y >= topHere - 0.5d && y <= topHere + 8.0d) {
                return;
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
                        int top = FloatingIslandsChunkGenerator.islandSurfaceBlockY(generator, wx, wz, minY, maxY);
                        if (top != Integer.MIN_VALUE) {
                            player.teleportTo(level, wx + 0.5d, top + 1.0d, wz + 0.5d, player.getYRot(), player.getXRot());
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
}
