package net.projectisland;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * Floating-island survival uses the vanilla overworld dimension id; the mod replaces its generator via datapack.
 */
public final class ProjectIslandDimensions {
    private ProjectIslandDimensions() {}

    /** Same as {@link Level#OVERWORLD} — gameplay lives in the overworld with {@link FloatingIslandsChunkGenerator}. */
    public static final ResourceKey<Level> FLOATING_ISLANDS = Level.OVERWORLD;

    public static boolean isFloatingIslandsGameplay(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return false;
        }
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        if (gen instanceof FloatingIslandsChunkGenerator) {
            return true;
        }
        return unwrapFloatingIslandsGenerator(gen) != null;
    }

    /**
     * Some loaders or future wrappers may not expose our chunk generator as the direct runtime type of
     * {@link net.minecraft.world.level.chunk.ChunkGenerator} returned from the chunk cache;
     * walk a shallow delegate chain when fields are present.
     */
    private static FloatingIslandsChunkGenerator unwrapFloatingIslandsGenerator(ChunkGenerator gen) {
        ChunkGenerator current = gen;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (current instanceof FloatingIslandsChunkGenerator floating) {
                return floating;
            }
            ChunkGenerator next = tryDelegateChunkGenerator(current);
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        return null;
    }

    private static ChunkGenerator tryDelegateChunkGenerator(ChunkGenerator gen) {
        for (var field : gen.getClass().getDeclaredFields()) {
            if (!ChunkGenerator.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object v = field.get(gen);
                if (v instanceof ChunkGenerator delegate) {
                    return delegate;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through to next field / null
            }
        }
        return null;
    }
}
