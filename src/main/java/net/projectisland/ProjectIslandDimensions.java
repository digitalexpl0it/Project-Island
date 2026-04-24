package net.projectisland;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * Floating-island survival uses the vanilla overworld dimension id; the mod replaces its generator via datapack.
 */
public final class ProjectIslandDimensions {
    private ProjectIslandDimensions() {}

    /** Same as {@link Level#OVERWORLD} — gameplay lives in the overworld with {@link FloatingIslandsChunkGenerator}. */
    public static final ResourceKey<Level> FLOATING_ISLANDS = Level.OVERWORLD;

    public static boolean isFloatingIslandsGameplay(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof FloatingIslandsChunkGenerator;
    }
}
