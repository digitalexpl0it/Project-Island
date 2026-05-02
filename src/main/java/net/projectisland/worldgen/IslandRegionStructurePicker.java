package net.projectisland.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.projectisland.Config;

/**
 * Per-island-region weighted rolls for which vanilla structures to **keep**, mirroring {@link IslandRegionBiomePicker}.
 * Settlement structures ({@code village_*}, pillager outpost) use a separate allow roll plus biome compatibility.
 * Other vanilla structures (e.g. {@code minecraft:mansion}, {@code minecraft:igloo}) are **not** gated here — they still
 * need a suitable island biome and must survive {@link net.projectisland.worldgen.FloatingIslandsChunkGenerator
 * #trimFloatingStructureBlocks} / land-contact rules.
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
                || "mineshaft".equals(p)
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
        int mine = Config.ISLAND_REGION_RARE_STRUCTURE_WEIGHT_MINESHAFT.getAsInt();
        int total = none + mr + trial + desert + jungle + mine;
        if (total <= 0) {
            return IslandRegionRareStructureSlot.NONE;
        }
        int[] w = {none, mr, trial, desert, jungle, mine};
        IslandRegionRareStructureSlot[] slots = {
            IslandRegionRareStructureSlot.NONE,
            IslandRegionRareStructureSlot.MONSTER_ROOM,
            IslandRegionRareStructureSlot.TRIAL_CHAMBERS,
            IslandRegionRareStructureSlot.DESERT_PYRAMID,
            IslandRegionRareStructureSlot.JUNGLE_PYRAMID,
            IslandRegionRareStructureSlot.MINESHAFT
        };
        int roll = rnd.nextInt(total);
        int acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += w[i];
            if (roll < acc) {
                return slots[i];
            }
        }
        return IslandRegionRareStructureSlot.NONE;
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
        if (ResourceLocation.withDefaultNamespace("mineshaft").equals(id)) {
            return rare != IslandRegionRareStructureSlot.MINESHAFT;
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
        return biome.is(BiomeTags.HAS_PILLAGER_OUTPOST);
    }

    /**
     * Island biomes come from {@link IslandRegionBiomePicker} only — no savanna etc. — so some vanilla village ids rarely
     * match; those rolls still gate placement when vanilla tries an incompatible id.
     */
    private static boolean villagePathMatchesIslandBiome(ResourceLocation villageId, Holder<Biome> biome) {
        return switch (villageId.getPath()) {
            case "village_plains" -> biome.is(BiomeTags.HAS_VILLAGE_PLAINS);
            case "village_desert" -> biome.is(BiomeTags.HAS_VILLAGE_DESERT);
            case "village_snowy" -> biome.is(BiomeTags.HAS_VILLAGE_SNOWY);
            case "village_taiga" -> biome.is(BiomeTags.HAS_VILLAGE_TAIGA);
            case "village_savanna" -> biome.is(BiomeTags.HAS_VILLAGE_SAVANNA);
            default -> true;
        };
    }
}
