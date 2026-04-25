package net.projectisland.island;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.projectisland.Config;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.content.ProjectIslandContent;
import net.projectisland.content.RopeAnchorBlockEntity;

/**
 * Server-only secondary claim (non-starter): feet column or shift-use on a linked rope anchor on the target island.
 */
public final class IslandSecondaryClaim {
    private IslandSecondaryClaim() {}

    public enum Outcome {
        SUCCESS,
        NOT_FLOATING_WORLD,
        NO_ISLAND_COLUMN,
        NOT_AVAILABLE,
        ROPE_REQUIRED,
        NOT_LINKED_ANCHOR,
        RACE_LOST
    }

    public static Outcome tryAtFeet(ServerPlayer player, ServerLevel level) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return Outcome.NOT_FLOATING_WORLD;
        }
        return IslandWorld.keyAt(level, player.blockPosition())
                .map(key -> tryAtIsland(player, level, key, null))
                .orElse(Outcome.NO_ISLAND_COLUMN);
    }

    /**
     * @param ropeAnchorPos when non-null, must be a {@link ProjectIslandContent#ROPE_ANCHOR} with a saved link owned by
     *     the player on the same island region as {@code islandKey}.
     */
    public static Outcome tryAtIsland(ServerPlayer player, ServerLevel level, FloatingIslandKey islandKey, @Nullable BlockPos ropeAnchorPos) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return Outcome.NOT_FLOATING_WORLD;
        }
        FloatingIslandSavedData data = IslandWorld.get(level);
        if (ropeAnchorPos != null) {
            if (!validatePlayerAnchorOnIsland(player, level, data, islandKey, ropeAnchorPos)) {
                return Outcome.NOT_LINKED_ANCHOR;
            }
        }
        IslandState state = data.peek(islandKey).map(IslandRecord::state).orElse(IslandState.AVAILABLE);
        if (state != IslandState.AVAILABLE) {
            return Outcome.NOT_AVAILABLE;
        }
        if (Config.SECONDARY_CLAIM_REQUIRES_ROPE_LINK.getAsBoolean()
                && !data.hasRopeLinkFromClaimedIsland(player.getUUID(), islandKey)) {
            return Outcome.ROPE_REQUIRED;
        }
        if (data.trySecondaryClaim(islandKey, player.getUUID(), level.getGameTime())) {
            return Outcome.SUCCESS;
        }
        return Outcome.RACE_LOST;
    }

    private static boolean validatePlayerAnchorOnIsland(
            ServerPlayer player,
            ServerLevel level,
            FloatingIslandSavedData data,
            FloatingIslandKey islandKey,
            BlockPos ropeAnchorPos) {
        if (!level.getBlockState(ropeAnchorPos).is(ProjectIslandContent.ROPE_ANCHOR)) {
            return false;
        }
        if (!IslandWorld.keyAt(level, ropeAnchorPos).filter(islandKey::equals).isPresent()) {
            return false;
        }
        if (!(level.getBlockEntity(ropeAnchorPos) instanceof RopeAnchorBlockEntity be) || !be.hasLink()) {
            return false;
        }
        return be.linkId()
                .flatMap(data::getRopeLink)
                .filter(link -> player.getUUID().equals(link.owner()))
                .isPresent();
    }

    public static Component message(Outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> Component.translatable("projectisland.claim.success");
            case NOT_FLOATING_WORLD -> Component.translatable("projectisland.claim.not_floating_world");
            case NO_ISLAND_COLUMN -> Component.translatable("projectisland.claim.no_island_column");
            case NOT_AVAILABLE -> Component.translatable("projectisland.claim.not_available");
            case ROPE_REQUIRED -> Component.translatable("projectisland.claim.no_rope_link");
            case NOT_LINKED_ANCHOR -> Component.translatable("projectisland.claim.not_linked_anchor");
            case RACE_LOST -> Component.translatable("projectisland.claim.race_lost");
        };
    }
}
