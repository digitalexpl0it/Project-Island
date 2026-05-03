package net.projectisland;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * One weighted entry for {@linkplain FloatingIslandPackSpawnReloader pack spawn rules} (datapack JSON).
 *
 * @param biomeTag        Biome must be in this tag (e.g. {@code minecraft:is_overworld}).
 * @param placement       {@code ground} = island surface column with air feet; {@code sky} = above surface band.
 * @param minYAboveSurface inclusive offset above procedural island top for {@code sky} (ignored for ground).
 * @param maxYAboveSurface inclusive offset above procedural island top for {@code sky}.
 * @param weight          Relative weight when rolling among matching rules at a candidate column.
 * @param maxNearbySame   Skip if this many entities of the same type already exist within {@code nearbySameRadius}.
 * @param nearbySameRadius horizontal-ish box radius for the same-type cap check.
 */
public record FloatingIslandPackSpawnRule(
        ResourceLocation entityId,
        TagKey<Biome> biomeTag,
        FloatingIslandPackSpawnRule.Placement placement,
        int minYAboveSurface,
        int maxYAboveSurface,
        int weight,
        int maxNearbySame,
        double nearbySameRadius) {

    public enum Placement {
        GROUND,
        SKY
    }
}
