package net.projectisland.worldgen;

import java.util.ArrayDeque;
import java.util.Queue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;

/**
 * Optional spawn-area chunk pregeneration (Chebyshev neighborhood around shared spawn) to reduce first-join hitching.
 */
public final class FloatingIslandsSpawnPregen {
    private FloatingIslandsSpawnPregen() {}

    private static PregenJob active;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnPregen::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnPregen::onLevelUnloadEvent);
        NeoForge.EVENT_BUS.addListener(FloatingIslandsSpawnPregen::onServerTickPost);
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        int radius = Config.SPAWN_PREGEN_CHUNK_RADIUS.getAsInt();
        if (radius <= 0) {
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        ChunkPos center = new ChunkPos(spawn);
        Queue<ChunkPos> queue = new ArrayDeque<>((2 * radius + 1) * (2 * radius + 1));
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                queue.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        active = new PregenJob(level, queue, radius);
        ProjectIsland.LOGGER.info(
                "Spawn pregeneration queued: Chebyshev chunk radius {} around chunk {}, {} ({} chunks, {} per tick).",
                radius,
                center.x,
                center.z,
                queue.size(),
                Config.SPAWN_PREGEN_CHUNKS_PER_TICK.getAsInt());
    }

    private static void onLevelUnloadEvent(LevelEvent.Unload event) {
        if (active != null && active.level == event.getLevel()) {
            active = null;
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        PregenJob job = active;
        if (job == null) {
            return;
        }
        if (!job.level.dimension().equals(Level.OVERWORLD) || job.level.getServer() == null) {
            active = null;
            return;
        }
        int budget = Math.max(1, Config.SPAWN_PREGEN_CHUNKS_PER_TICK.getAsInt());
        ServerLevel level = job.level;
        for (int i = 0; i < budget; i++) {
            ChunkPos next = job.queue.poll();
            if (next == null) {
                ProjectIsland.LOGGER.info(
                        "Spawn pregeneration finished (Chebyshev chunk radius {} around shared spawn).",
                        job.radius);
                active = null;
                return;
            }
            level.getChunkSource().getChunk(next.x, next.z, ChunkStatus.FULL, true);
        }
    }

    private record PregenJob(ServerLevel level, Queue<ChunkPos> queue, int radius) {}
}
