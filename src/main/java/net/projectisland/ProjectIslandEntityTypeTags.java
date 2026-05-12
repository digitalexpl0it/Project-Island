package net.projectisland;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class ProjectIslandEntityTypeTags {
    public static final TagKey<EntityType<?>> ROPE_SURFING_MOBS =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "rope_surfing_mobs"));

    private ProjectIslandEntityTypeTags() {}
}
