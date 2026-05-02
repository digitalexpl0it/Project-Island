package net.projectisland.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.projectisland.ProjectIslandDimensions;
import org.jetbrains.annotations.Nullable;

/**
 * Overworld level seed for deterministic rolls that must stay aligned with {@link FloatingIslandLayout} (e.g. controlled
 * settlement branch) while layout math stays usable from non-generator code. Bound on overworld {@link LevelEvent.Load}
 * so spawn pregen and early chunk generation agree with gameplay callers.
 */
public final class FloatingIslandLayoutSeed {
    @Nullable
    private static volatile Long levelSeed;

    private FloatingIslandLayoutSeed() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FloatingIslandLayoutSeed::onLevelLoad);
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
        bind(level.getSeed());
    }

    public static void bind(long seed) {
        levelSeed = seed;
    }

    @Nullable
    public static Long getOrNull() {
        return levelSeed;
    }
}
