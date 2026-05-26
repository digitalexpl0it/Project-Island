package net.projectisland.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;

/**
 * Optional worldgen companions detected at runtime (no compile dependency).
 */
public final class BiomeModIntegration {
    /** Biomes O' Plenty NeoForge mod id (see its {@code META-INF/neoforge.mods.toml}). */
    public static final String BIOMES_O_PLENTY_MOD_ID = "biomesoplenty";

    /** Create: Levite Fields (Levitite Fields) — {@code levmod:levitite_fields} overworld biome. */
    public static final String LEVITE_FIELDS_MOD_ID = "levmod";

    public static final ResourceKey<Biome> LEVITITE_FIELDS_BIOME =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(LEVITE_FIELDS_MOD_ID, "levitite_fields"));

    private static final List<String> DISCOVER_NAMESPACES =
            List.of(BIOMES_O_PLENTY_MOD_ID, LEVITE_FIELDS_MOD_ID);

    private BiomeModIntegration() {}

    public static boolean biomesOPlentyLoaded() {
        return ModList.get().isLoaded(BIOMES_O_PLENTY_MOD_ID);
    }

    public static boolean leviteFieldsLoaded() {
        return ModList.get().isLoaded(LEVITE_FIELDS_MOD_ID);
    }

    /** Whether any mod that participates in {@link #listDiscoverableBiomeKeys} is present. */
    public static boolean anyDiscoverableBiomeModLoaded() {
        return biomesOPlentyLoaded() || leviteFieldsLoaded();
    }

    public static boolean namespaceIsDiscoverable(String namespace) {
        if (BIOMES_O_PLENTY_MOD_ID.equals(namespace)) {
            return biomesOPlentyLoaded();
        }
        if (LEVITE_FIELDS_MOD_ID.equals(namespace)) {
            return leviteFieldsLoaded();
        }
        return false;
    }

    /**
     * Every {@code namespace:*} biome id registered in this lookup for a discoverable namespace, deterministically
     * sorted. Levite Fields end biomes ({@code end_*}) are omitted so overworld sky islands do not roll them.
     */
    public static List<ResourceKey<Biome>> listRegisteredBiomeKeys(HolderLookup<Biome> lookup, String namespace) {
        if (!namespaceIsDiscoverable(namespace)) {
            return List.of();
        }
        return lookup.listElementIds()
                .filter(k -> namespace.equals(k.location().getNamespace()))
                .filter(k -> includeBiomeForOverworldIslands(namespace, k))
                .sorted(Comparator.comparing(k -> k.location().toString()))
                .toList();
    }

    /** Merged discoverable keys from every loaded namespace in {@link #DISCOVER_NAMESPACES}. */
    public static List<ResourceKey<Biome>> listDiscoverableBiomeKeys(HolderLookup<Biome> lookup) {
        List<ResourceKey<Biome>> merged = new ArrayList<>();
        for (String ns : DISCOVER_NAMESPACES) {
            merged.addAll(listRegisteredBiomeKeys(lookup, ns));
        }
        merged.sort(Comparator.comparing(k -> k.location().toString()));
        return List.copyOf(merged);
    }

    private static boolean includeBiomeForOverworldIslands(String namespace, ResourceKey<Biome> key) {
        if (!LEVITE_FIELDS_MOD_ID.equals(namespace)) {
            return true;
        }
        return !key.location().getPath().startsWith("end_");
    }
}
