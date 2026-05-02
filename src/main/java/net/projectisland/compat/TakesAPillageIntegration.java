package net.projectisland.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.fml.ModList;

/**
 * Optional compat for **It Takes a Pillage** / **It Takes a Pillage Continuation** ({@code takesapillage}) — same mod id
 * and {@code takesapillage:*} datapack paths (Faboslav continuation). Custom pillager structures use controlled settlement
 * placement (see {@code IslandRegionControlledSettlementPlacement}).
 */
public final class TakesAPillageIntegration {
    public static final String MOD_ID = "takesapillage";

    /** Matches datapack ids under {@code data/takesapillage/worldgen/structure/}. */
    public static final ResourceKey<Structure> BASTILLE =
            ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "bastille"));

    public static final ResourceKey<Structure> PILLAGER_CAMP =
            ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "pillager_camp"));

    /**
     * Start pools and jigsaw depth / span match {@code worldgen/structure/bastille.json} and {@code pillager_camp.json}
     * in the mod jar (Continuation uses the same ids).
     */
    public static final ResourceKey<StructureTemplatePool> BASTILLE_START_POOL = ResourceKey.create(
            Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath(MOD_ID, "bastille/start_pool"));

    public static final ResourceKey<StructureTemplatePool> PILLAGER_CAMP_START_POOL = ResourceKey.create(
            Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath(MOD_ID, "pillager_camp/start_pool"));

    public static final int BASTILLE_JIGSAW_DEPTH = 3;

    public static final int PILLAGER_CAMP_JIGSAW_DEPTH = 6;

    /** {@code max_distance_from_center} in mod JSON (both structures). */
    public static final int PILLAGER_STRUCTURE_MAX_DISTANCE_FROM_CENTER = 128;

    private TakesAPillageIntegration() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Bastille / pillager camp — treated like settlements for strip / trim / land-contact passes when compat runs. */
    public static boolean isPillagerStructure(ResourceLocation structureId) {
        if (structureId == null || !MOD_ID.equals(structureId.getNamespace())) {
            return false;
        }
        String p = structureId.getPath();
        return "bastille".equals(p) || "pillager_camp".equals(p);
    }
}
