package net.projectisland.worldgen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.projectisland.Config;

/**
 * One overworld biome per {@link net.projectisland.island.FloatingIslandKey} (island region), from config weights
 * and a deterministic {@link RandomSource}. Vanilla {@code multi_noise} is not used for land columns — it often
 * resolves to river/ocean for void-style worlds at every Y.
 */
public final class IslandRegionBiomePicker {
    private IslandRegionBiomePicker() {}

    private record Weighted(ResourceKey<Biome> key, int weight) {}

    /**
     * Rolls which overworld biome this island region uses. Weights of {@code 0} exclude a biome from the pool.
     */
    public static ResourceKey<Biome> roll(RandomSource random) {
        List<Weighted> pool = new ArrayList<>();
        add(pool, Biomes.RIVER, Config.ISLAND_BIOME_WEIGHT_RIVER.getAsInt());
        add(pool, Biomes.PLAINS, Config.ISLAND_BIOME_WEIGHT_PLAINS.getAsInt());
        add(pool, Biomes.FOREST, Config.ISLAND_BIOME_WEIGHT_FOREST.getAsInt());
        add(pool, Biomes.TAIGA, Config.ISLAND_BIOME_WEIGHT_TAIGA.getAsInt());
        add(pool, Biomes.DESERT, Config.ISLAND_BIOME_WEIGHT_DESERT.getAsInt());
        add(pool, Biomes.SNOWY_PLAINS, Config.ISLAND_BIOME_WEIGHT_SNOWY_PLAINS.getAsInt());
        add(pool, Biomes.JUNGLE, Config.ISLAND_BIOME_WEIGHT_JUNGLE.getAsInt());
        add(pool, Biomes.MUSHROOM_FIELDS, Config.ISLAND_BIOME_WEIGHT_MUSHROOM_FIELDS.getAsInt());
        add(pool, Biomes.BADLANDS, Config.ISLAND_BIOME_WEIGHT_BADLANDS.getAsInt());
        add(pool, Biomes.WINDSWEPT_FOREST, Config.ISLAND_BIOME_WEIGHT_WINDSWEPT_FOREST.getAsInt());
        add(pool, Biomes.SWAMP, Config.ISLAND_BIOME_WEIGHT_SWAMP.getAsInt());
        add(pool, Biomes.DARK_FOREST, Config.ISLAND_BIOME_WEIGHT_DARK_FOREST.getAsInt());
        add(pool, Biomes.SNOWY_TAIGA, Config.ISLAND_BIOME_WEIGHT_SNOWY_TAIGA.getAsInt());
        if (pool.isEmpty()) {
            return Biomes.PLAINS;
        }
        int total = 0;
        for (Weighted w : pool) {
            total += w.weight;
        }
        int roll = random.nextInt(total);
        int acc = 0;
        for (Weighted w : pool) {
            acc += w.weight;
            if (roll < acc) {
                return w.key;
            }
        }
        return pool.get(pool.size() - 1).key;
    }

    private static void add(List<Weighted> pool, ResourceKey<Biome> key, int weight) {
        if (weight > 0) {
            pool.add(new Weighted(key, weight));
        }
    }
}
