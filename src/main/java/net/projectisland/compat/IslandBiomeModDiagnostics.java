package net.projectisland.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * One-time startup diagnostics when discoverable biome mods are present so operators can see registry discovery /
 * overrides.
 */
public final class IslandBiomeModDiagnostics {
    private IslandBiomeModDiagnostics() {}

    public static void logOnServerStart(MinecraftServer server) {
        if (!BiomeModIntegration.anyDiscoverableBiomeModLoaded()) {
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        ChunkGenerator gen = overworld.getChunkSource().getGenerator();
        if (!(gen instanceof FloatingIslandsChunkGenerator)) {
            ProjectIsland.LOGGER.warn(
                    "Optional biome mods are loaded but the overworld chunk generator is not Project Island floating islands — per-island biome merge does not apply.");
            return;
        }
        if (!Config.ISLAND_BIOME_MOD_INTEGRATION_ENABLED.getAsBoolean()) {
            ProjectIsland.LOGGER.info(
                    "Optional biome mods present; islandBiomeModIntegrationEnabled=false — mod biome pool is not used.");
            return;
        }

        Optional<HolderLookup<Biome>> registry = biomeRegistryLookup(gen);

        if (Config.ISLAND_BIOME_MOD_DISCOVER_ALL_REGISTERED.getAsBoolean()) {
            for (String ns : List.of(BiomeModIntegration.BIOMES_O_PLENTY_MOD_ID, BiomeModIntegration.LEVITE_FIELDS_MOD_ID)) {
                if (!BiomeModIntegration.namespaceIsDiscoverable(ns)) {
                    continue;
                }
                List<ResourceKey<Biome>> registered =
                        registry.map(lookup -> BiomeModIntegration.listRegisteredBiomeKeys(lookup, ns)).orElse(List.of());
                int resolvable = 0;
                for (ResourceKey<Biome> key : registered) {
                    if (isBiomeResolvable(gen, registry, key)) {
                        resolvable++;
                    }
                }
                ProjectIsland.LOGGER.info(
                        "Floating-island biome merge ({}): discover-all — {} registered {}:* ids; {} resolve for island holders ({} skipped).",
                        ns,
                        registered.size(),
                        ns,
                        resolvable,
                        registered.size() - resolvable);
            }
            ProjectIsland.LOGGER.info(
                    "  Override lines in islandBiomeModWeightedEntries: {} (optional per-id weights; empty = default {}).",
                    Config.ISLAND_BIOME_MOD_WEIGHTED_ENTRIES.get().size(),
                    Config.ISLAND_BIOME_MOD_DISCOVERED_DEFAULT_WEIGHT.getAsInt());
        } else {
            List<String> accepted = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (String entry : Config.ISLAND_BIOME_MOD_WEIGHTED_ENTRIES.get()) {
                int eq = entry.lastIndexOf('=');
                if (eq <= 0 || eq >= entry.length() - 1) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.parse(entry.substring(0, eq));
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
                if (isBiomeResolvable(gen, registry, key)) {
                    accepted.add(id.toString());
                } else {
                    missing.add(id.toString());
                }
            }

            int configured = Config.ISLAND_BIOME_MOD_WEIGHTED_ENTRIES.get().size();
            ProjectIsland.LOGGER.info(
                    "Floating-island biome merge: curated-list mode — {} of {} configured ids resolve in the biome registry.",
                    accepted.size(),
                    configured);
            ProjectIsland.LOGGER.info(
                    "  Accepted into island rolls: {}", accepted.isEmpty() ? "(none)" : String.join(", ", accepted));
            ProjectIsland.LOGGER.info(
                    "  Not registered / typo / wrong namespace: {}",
                    missing.isEmpty() ? "(none)" : String.join(", ", missing));

            if (accepted.isEmpty() && configured > 0) {
                ProjectIsland.LOGGER.warn(
                        "No configured mod biome ids resolved — check spelling or set islandBiomeModDiscoverAllRegistered=true.");
            }
        }

        if (BiomeModIntegration.leviteFieldsLoaded()) {
            ResourceKey<Biome> levite = BiomeModIntegration.LEVITITE_FIELDS_BIOME;
            boolean ok = isBiomeResolvable(gen, registry, levite);
            ProjectIsland.LOGGER.info(
                    "  Levite Fields ({}): {} — placement **{}** (void horiz ≤ {}, void biome chance {} when void/both).",
                    levite.location(),
                    ok ? "resolves" : "does NOT resolve",
                    Config.leviteFieldsPlacementMode().name().toLowerCase(java.util.Locale.ROOT),
                    Config.ISLAND_LEVITE_FIELDS_VOID_MAX_HORIZ_BEYOND_EDGE.get(),
                    Config.ISLAND_LEVITE_FIELDS_VOID_BIOME_CHANCE.get());
            if (!ok) {
                ProjectIsland.LOGGER.warn(
                        "levmod is loaded but {} is missing from the biome registry — Levite void columns cannot be assigned.",
                        levite.location());
            }
        }

        ProjectIsland.LOGGER.info(
                "New island chunks roll biomes from this pool — already-generated chunks keep their saved palette; explore fresh area or use a new world to see mixed biomes.");
    }

    private static Optional<HolderLookup<Biome>> biomeRegistryLookup(ChunkGenerator gen) {
        for (Holder<Biome> h : gen.getBiomeSource().possibleBiomes()) {
            if (h instanceof Holder.Reference<Biome> ref) {
                return Optional.of(ref.unwrapLookup());
            }
        }
        return Optional.empty();
    }

    /** Matches {@link FloatingIslandsChunkGenerator} resolution: possibleBiomes first, then registry lookup. */
    private static boolean isBiomeResolvable(
            ChunkGenerator gen, Optional<HolderLookup<Biome>> registry, ResourceKey<Biome> key) {
        for (Holder<Biome> h : gen.getBiomeSource().possibleBiomes()) {
            if (h.is(key)) {
                return true;
            }
        }
        return registry.flatMap(l -> l.get(key)).isPresent();
    }
}
