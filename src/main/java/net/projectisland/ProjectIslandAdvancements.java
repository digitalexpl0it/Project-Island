package net.projectisland;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Grants hidden {@code minecraft:impossible} advancements for FTB Quests / pack integration. */
public final class ProjectIslandAdvancements {
    public static final ResourceLocation ROPE_LINK_CREATED =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "progression/rope_link_created");
    public static final ResourceLocation ROPE_SURF_COMPLETE =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "progression/rope_surf_complete");

    private ProjectIslandAdvancements() {}

    /** Awards every criterion on {@code advancementId} if the advancement exists and is not already completed. */
    public static void tryGrant(ServerPlayer player, ResourceLocation advancementId) {
        if (player.server == null) {
            return;
        }
        AdvancementHolder holder = player.server.getAdvancements().get(advancementId);
        if (holder == null) {
            return;
        }
        if (player.getAdvancements().getOrStartProgress(holder).isDone()) {
            return;
        }
        for (String criterion : holder.value().criteria().keySet()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
