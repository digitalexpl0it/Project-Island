package net.projectisland.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.projectisland.Config;
import net.projectisland.compat.BiomeModIntegration;

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
     *
     * @param biomePresent mod biome ids only: {@code true} when the id exists in this dimension’s biome registry (not
     *            gated on {@link net.minecraft.world.level.biome.BiomeSource#possibleBiomes()} alone — TerraBlender can omit mod entries there)
     * @param registeredBopBiomeKeys every {@code biomesoplenty:*} id from {@link BiomeModIntegration#listRegisteredBiomeKeys};
     *            may be empty when discovery is off or registry lookup failed
     */
    public static ResourceKey<Biome> roll(
            RandomSource random,
            Predicate<ResourceKey<Biome>> biomePresent,
            List<ResourceKey<Biome>> registeredBopBiomeKeys) {
        List<Weighted> vanillaPool = new ArrayList<>();
        add(vanillaPool, Biomes.RIVER, Config.ISLAND_BIOME_WEIGHT_RIVER.getAsInt());
        add(vanillaPool, Biomes.PLAINS, Config.ISLAND_BIOME_WEIGHT_PLAINS.getAsInt());
        add(vanillaPool, Biomes.FOREST, Config.ISLAND_BIOME_WEIGHT_FOREST.getAsInt());
        add(vanillaPool, Biomes.TAIGA, Config.ISLAND_BIOME_WEIGHT_TAIGA.getAsInt());
        add(vanillaPool, Biomes.DESERT, Config.ISLAND_BIOME_WEIGHT_DESERT.getAsInt());
        add(vanillaPool, Biomes.SNOWY_PLAINS, Config.ISLAND_BIOME_WEIGHT_SNOWY_PLAINS.getAsInt());
        add(vanillaPool, Biomes.JUNGLE, Config.ISLAND_BIOME_WEIGHT_JUNGLE.getAsInt());
        add(vanillaPool, Biomes.MUSHROOM_FIELDS, Config.ISLAND_BIOME_WEIGHT_MUSHROOM_FIELDS.getAsInt());
        add(vanillaPool, Biomes.BADLANDS, Config.ISLAND_BIOME_WEIGHT_BADLANDS.getAsInt());
        add(vanillaPool, Biomes.WINDSWEPT_FOREST, Config.ISLAND_BIOME_WEIGHT_WINDSWEPT_FOREST.getAsInt());
        add(vanillaPool, Biomes.SWAMP, Config.ISLAND_BIOME_WEIGHT_SWAMP.getAsInt());
        add(vanillaPool, Biomes.DARK_FOREST, Config.ISLAND_BIOME_WEIGHT_DARK_FOREST.getAsInt());
        add(vanillaPool, Biomes.SNOWY_TAIGA, Config.ISLAND_BIOME_WEIGHT_SNOWY_TAIGA.getAsInt());

        List<Weighted> modPool = new ArrayList<>();
        if (BiomeModIntegration.biomesOPlentyLoaded() && Config.ISLAND_BIOME_MOD_INTEGRATION_ENABLED.getAsBoolean()) {
            fillModBiomePool(modPool, biomePresent, registeredBopBiomeKeys);
        }

        boolean bopActive =
                BiomeModIntegration.biomesOPlentyLoaded() && Config.ISLAND_BIOME_MOD_INTEGRATION_ENABLED.getAsBoolean();
        double preferred = Config.ISLAND_BIOME_MOD_PREFERRED_ROLL_FRACTION.get();

        if (bopActive && !modPool.isEmpty() && preferred > 0.0d) {
            if (preferred >= 1.0d || random.nextDouble() < preferred) {
                return rollWeighted(random, modPool);
            }
            return rollWeighted(random, vanillaPool);
        }

        List<Weighted> combined = new ArrayList<>(vanillaPool);
        combined.addAll(modPool);
        return rollWeighted(random, combined);
    }

    private static void fillModBiomePool(
            List<Weighted> pool, Predicate<ResourceKey<Biome>> biomePresent, List<ResourceKey<Biome>> registeredBopBiomeKeys) {
        Map<ResourceKey<Biome>, Integer> overrides = parseModWeightOverrides();
        boolean discover = Config.ISLAND_BIOME_MOD_DISCOVER_ALL_REGISTERED.getAsBoolean();

        if (discover && !registeredBopBiomeKeys.isEmpty()) {
            int defaultW = Config.ISLAND_BIOME_MOD_DISCOVERED_DEFAULT_WEIGHT.getAsInt();
            Map<ResourceKey<Biome>, Integer> weights = new LinkedHashMap<>();
            for (ResourceKey<Biome> key : registeredBopBiomeKeys) {
                if (!biomePresent.test(key)) {
                    continue;
                }
                weights.put(key, overrides.getOrDefault(key, defaultW));
            }
            String ns = BiomeModIntegration.BIOMES_O_PLENTY_MOD_ID;
            for (Map.Entry<ResourceKey<Biome>, Integer> e : overrides.entrySet()) {
                ResourceKey<Biome> key = e.getKey();
                int w = e.getValue();
                if (w <= 0 || !ns.equals(key.location().getNamespace()) || !biomePresent.test(key)) {
                    continue;
                }
                weights.put(key, w);
            }
            for (Map.Entry<ResourceKey<Biome>, Integer> e : weights.entrySet()) {
                if (e.getValue() > 0) {
                    pool.add(new Weighted(e.getKey(), e.getValue()));
                }
            }
        } else {
            addModWeightedEntriesExplicitListOnly(pool, biomePresent);
        }
    }

    private static Map<ResourceKey<Biome>, Integer> parseModWeightOverrides() {
        Map<ResourceKey<Biome>, Integer> map = new HashMap<>();
        for (String entry : Config.ISLAND_BIOME_MOD_WEIGHTED_ENTRIES.get()) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                continue;
            }
            ResourceLocation id = ResourceLocation.parse(entry.substring(0, eq));
            int weight;
            try {
                weight = Integer.parseInt(entry.substring(eq + 1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (weight <= 0) {
                continue;
            }
            map.put(ResourceKey.create(Registries.BIOME, id), weight);
        }
        return map;
    }

    private static ResourceKey<Biome> rollWeighted(RandomSource random, List<Weighted> pool) {
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

    /**
     * Curated list only ({@link Config#ISLAND_BIOME_MOD_DISCOVER_ALL_REGISTERED} {@code false}), or fallback when
     * discovery is on but {@code registeredBopBiomeKeys} was empty.
     */
    private static void addModWeightedEntriesExplicitListOnly(List<Weighted> pool, Predicate<ResourceKey<Biome>> biomePresent) {
        for (Map.Entry<ResourceKey<Biome>, Integer> e : parseModWeightOverrides().entrySet()) {
            if (e.getValue() <= 0 || !biomePresent.test(e.getKey())) {
                continue;
            }
            pool.add(new Weighted(e.getKey(), e.getValue()));
        }
    }

    private static void add(List<Weighted> pool, ResourceKey<Biome> key, int weight) {
        if (weight > 0) {
            pool.add(new Weighted(key, weight));
        }
    }
}
