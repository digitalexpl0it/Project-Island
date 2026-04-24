package net.projectisland.worldgen;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.projectisland.ProjectIsland;

public final class ProjectIslandWorldgen {
    private ProjectIslandWorldgen() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ProjectIslandWorldgen::registerChunkGenerators);
    }

    private static void registerChunkGenerators(RegisterEvent event) {
        if (!Objects.equals(event.getRegistryKey(), Registries.CHUNK_GENERATOR)) {
            return;
        }
        event.register(
                Registries.CHUNK_GENERATOR,
                FloatingIslandsChunkGenerator.ID,
                () -> FloatingIslandsChunkGenerator.CODEC);
    }
}
