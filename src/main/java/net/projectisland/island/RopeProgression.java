package net.projectisland.island;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.projectisland.ProjectIsland;

/**
 * Server-authoritative rope “tier” derived from advancements (datapack-visible).
 *
 * <p>This is intentionally light-weight: the server reads advancements at runtime and applies tiered caps when a new
 * rope link is created. Existing links keep their stored caps until a future “upgrade” mechanic is added.</p>
 */
public final class RopeProgression {
    private static final ResourceLocation ADV_REINFORCED =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "progression/rope_reinforced");
    private static final ResourceLocation ADV_STEEL =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "progression/rope_steel");

    private RopeProgression() {}

    public enum RopeTier {
        BASIC(1.00d, 1.00d),
        REINFORCED(1.25d, 1.50d),
        STEEL(1.50d, 2.25d);

        public final double maxLengthMultiplier;
        public final double maxHealthMultiplier;

        RopeTier(double maxLengthMultiplier, double maxHealthMultiplier) {
            this.maxLengthMultiplier = maxLengthMultiplier;
            this.maxHealthMultiplier = maxHealthMultiplier;
        }
    }

    public static RopeTier tierFor(ServerPlayer player) {
        if (has(player, ADV_STEEL)) {
            return RopeTier.STEEL;
        }
        if (has(player, ADV_REINFORCED)) {
            return RopeTier.REINFORCED;
        }
        return RopeTier.BASIC;
    }

    private static boolean has(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder h = player.server.getAdvancements().get(id);
        if (h == null) {
            return false;
        }
        return player.getAdvancements().getOrStartProgress(h).isDone();
    }
}

