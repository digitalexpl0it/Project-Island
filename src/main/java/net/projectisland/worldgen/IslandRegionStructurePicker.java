package net.projectisland.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.projectisland.Config;

/**
 * Per-island-region weighted rolls for which vanilla structures to **keep**, mirroring {@link IslandRegionBiomePicker}.
 * Settlement structures ({@code village_*}, pillager outpost) use a separate allow roll plus biome compatibility.
 */
public final class IslandRegionStructurePicker {
    private IslandRegionStructurePicker() {}

    /** Structures handled by {@link #shouldRemoveStructure}; everything else is left to other generator passes. */
    public static boolean isGatedStructureType(ResourceLocation id) {
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String p = id.getPath();
        return "monster_room".equals(p)
                || "trial_chambers".equals(p)
                || "desert_pyramid".equals(p)
                || "jungle_pyramid".equals(p)
                || p.startsWith("village_")
                || "pillager_outpost".equals(p);
    }

    public static IslandRegionRareStructureSlot rollRare(RandomSource rnd) {
        int none = Config.ISLAND_REGION_RARE_STRUCTURE_WEIGHT_NONE.getAsInt();
        int mr = Config.ISLAND_REGION_RARE_STRUCTURE_WEIGHT_MONSTER_ROOM.getAsInt();
        int trial = Config.ISLAND_REGION_RARE_STRUCTURE_WEIGHT_TRIAL_CHAMBERS.getAsInt();
        int desert = Config.ISLAND_REGION_RARE_STRUCTURE_WEIGHT_DESERT_PYRAMID.getAsInt();
        int jungle = Config.ISLAND_REGION_RARE_STRUCTURE_WEIGHT_JUNGLE_PYRAMID.getAsInt();
        int total = none + mr + trial + desert + jungle;
        if (total <= 0) {
            return IslandRegionRareStructureSlot.NONE;
        }
        int[] w = {none, mr, trial, desert, jungle};
        IslandRegionRareStructureSlot[] slots = {
            IslandRegionRareStructureSlot.NONE,
            IslandRegionRareStructureSlot.MONSTER_ROOM,
            IslandRegionRareStructureSlot.TRIAL_CHAMBERS,
            IslandRegionRareStructureSlot.DESERT_PYRAMID,
            IslandRegionRareStructureSlot.JUNGLE_PYRAMID
        };
        int roll = rnd.nextInt(total);
        int acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += w[i];
            if (roll < acc) {
                return slots[i];
            }
        }
        return IslandRegionRareStructureSlot.JUNGLE_PYRAMID;
    }

    /** @return {@code true} if this region allows villages / pillager outposts at all (second roll). */
    public static boolean rollSettlementAllowed(RandomSource rnd) {
        int yes = Config.ISLAND_REGION_SETTLEMENT_STRUCTURE_WEIGHT_ALLOW.getAsInt();
        int no = Config.ISLAND_REGION_SETTLEMENT_STRUCTURE_WEIGHT_DENY.getAsInt();
        int total = yes + no;
        if (total <= 0) {
            return yes > 0;
        }
        return rnd.nextInt(total) < yes;
    }

    /**
     * @return {@code true} if this structure start should be removed from the chunk (invalidate + air wipe elsewhere).
     */
    public static boolean shouldRemoveStructure(
            ResourceLocation id,
            IslandRegionRareStructureSlot rare,
            boolean settlementAllowed,
            Holder<Biome> biome) {
        if (id == null) {
            return false;
        }
        if (ResourceLocation.withDefaultNamespace("monster_room").equals(id)) {
            return rare != IslandRegionRareStructureSlot.MONSTER_ROOM;
        }
        if (ResourceLocation.withDefaultNamespace("trial_chambers").equals(id)) {
            return rare != IslandRegionRareStructureSlot.TRIAL_CHAMBERS;
        }
        if (ResourceLocation.withDefaultNamespace("desert_pyramid").equals(id)) {
            if (rare != IslandRegionRareStructureSlot.DESERT_PYRAMID) {
                return true;
            }
            return !biome.is(Biomes.DESERT) && !biome.is(Biomes.BADLANDS);
        }
        if (ResourceLocation.withDefaultNamespace("jungle_pyramid").equals(id)) {
            if (rare != IslandRegionRareStructureSlot.JUNGLE_PYRAMID) {
                return true;
            }
            return !biome.is(Biomes.JUNGLE)
                    && !biome.is(Biomes.BAMBOO_JUNGLE)
                    && !biome.is(Biomes.SPARSE_JUNGLE);
        }
        if (isSettlementLike(id)) {
            if (!settlementAllowed) {
                return true;
            }
            if (ResourceLocation.withDefaultNamespace("pillager_outpost").equals(id)) {
                return !biomeOkForOutpost(biome);
            }
            if (!Config.ISLAND_REGION_VILLAGE_REQUIRE_BIOME_MATCH.getAsBoolean()) {
                return false;
            }
            return !villagePathMatchesIslandBiome(id, biome);
        }
        return false;
    }

    private static boolean isSettlementLike(ResourceLocation id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String p = id.getPath();
        return p.startsWith("village_") || "pillager_outpost".equals(p);
    }

    private static boolean biomeOkForOutpost(Holder<Biome> biome) {
        return biome.is(Biomes.PLAINS)
                || biome.is(Biomes.SUNFLOWER_PLAINS)
                || biome.is(Biomes.MEADOW)
                || biome.is(Biomes.DESERT)
                || biome.is(Biomes.SNOWY_PLAINS)
                || biome.is(Biomes.SNOWY_TAIGA)
                || biome.is(Biomes.TAIGA)
                || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)
                || biome.is(Biomes.SAVANNA)
                || biome.is(Biomes.SAVANNA_PLATEAU)
                || biome.is(Biomes.WINDSWEPT_SAVANNA)
                || biome.is(Biomes.FOREST)
                || biome.is(Biomes.FLOWER_FOREST)
                || biome.is(Biomes.BIRCH_FOREST)
                || biome.is(Biomes.DARK_FOREST);
    }

    /**
     * Island biomes come from {@link IslandRegionBiomePicker} only — no savanna etc. — so some vanilla village ids rarely
     * match; those rolls still gate placement when vanilla tries an incompatible id.
     */
    private static boolean villagePathMatchesIslandBiome(ResourceLocation villageId, Holder<Biome> biome) {
        String p = villageId.getPath();
        return switch (p) {
            case "village_plains" -> biome.is(Biomes.PLAINS)
                    || biome.is(Biomes.MEADOW)
                    || biome.is(Biomes.FOREST)
                    || biome.is(Biomes.FLOWER_FOREST)
                    || biome.is(Biomes.BIRCH_FOREST)
                    || biome.is(Biomes.DARK_FOREST);
            case "village_desert" -> biome.is(Biomes.DESERT);
            case "village_snowy" -> biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.SNOWY_TAIGA);
            case "village_taiga" -> biome.is(Biomes.TAIGA) || biome.is(Biomes.SNOWY_TAIGA);
            case "village_savanna" -> biome.is(Biomes.SAVANNA);
            default -> true;
        };
    }
}
