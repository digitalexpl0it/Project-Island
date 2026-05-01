package net.projectisland.compat;

import java.util.Comparator;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;

/**
 * Optional worldgen companions detected at runtime (no compile dependency).
 */
public final class BiomeModIntegration {
    /** Biomes O' Plenty NeoForge mod id (see its {@code META-INF/neoforge.mods.toml}). */
    public static final String BIOMES_O_PLENTY_MOD_ID = "biomesoplenty";

    private BiomeModIntegration() {}

    public static boolean biomesOPlentyLoaded() {
        return ModList.get().isLoaded(BIOMES_O_PLENTY_MOD_ID);
    }

    /** Every {@code biomesoplenty:*} biome id registered in this lookup, deterministically sorted. */
    public static List<ResourceKey<Biome>> listRegisteredBiomeKeys(HolderLookup<Biome> lookup) {
        return lookup.listElementIds()
                .filter(k -> BIOMES_O_PLENTY_MOD_ID.equals(k.location().getNamespace()))
                .sorted(Comparator.comparing(k -> k.location().toString()))
                .toList();
    }
}
