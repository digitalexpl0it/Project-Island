package net.projectisland.island;

import java.util.Optional;
import java.util.UUID;

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
 *
 * <p>Design reference: {@code docs/phase4-dock-link-spec.md} in the repository (physical harpoon rules, logical rope gate,
 * command vs anchor surfaces, anti-exploit notes).
 */
public final class IslandSecondaryClaim {
    private IslandSecondaryClaim() {}

    public enum Outcome {
        SUCCESS,
        NOT_FLOATING_WORLD,
        NO_ISLAND_COLUMN,
        NOT_AVAILABLE,
        ROPE_REQUIRED,
        TOO_FAR_FROM_DOCK,
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
        if (ropeAnchorPos == null) {
            int maxDist = Config.SECONDARY_CLAIM_COMMAND_MAX_DISTANCE_BLOCKS.getAsInt();
            if (maxDist > 0 && Config.SECONDARY_CLAIM_REQUIRES_ROPE_LINK.getAsBoolean()) {
                if (!isNearValidDockForCommand(player, data, islandKey, maxDist)) {
                    return Outcome.TOO_FAR_FROM_DOCK;
                }
            }
        }
        if (data.trySecondaryClaim(islandKey, player.getUUID(), level.getGameTime())) {
            return Outcome.SUCCESS;
        }
        return Outcome.RACE_LOST;
    }

    private static boolean isNearValidDockForCommand(
            ServerPlayer player, FloatingIslandSavedData data, FloatingIslandKey targetKey, int maxDistBlocks) {
        UUID owner = player.getUUID();
        Optional<FloatingIslandKey> starter = data.getStarterHome(owner);
        BlockPos feet = player.blockPosition();
        int fx = feet.getX();
        int fz = feet.getZ();

        int best = Integer.MAX_VALUE;
        for (RopeLink link : data.copyRopeLinks()) {
            if (!owner.equals(link.owner())) {
                continue;
            }
            boolean targetIsFrom = link.fromKey().equals(targetKey);
            boolean targetIsTo = link.toKey().equals(targetKey);
            if (!targetIsFrom && !targetIsTo) {
                continue;
            }
            FloatingIslandKey other = targetIsFrom ? link.toKey() : link.fromKey();
            boolean otherCounts = data.isClaimedByPlayer(other, owner) || starter.filter(other::equals).isPresent();
            if (!otherCounts) {
                continue;
            }
            BlockPos dock = targetIsFrom ? link.fromAnchorPos() : link.toAnchorPos();
            int d = Math.max(Math.abs(dock.getX() - fx), Math.abs(dock.getZ() - fz));
            if (d < best) {
                best = d;
            }
        }
        return best <= maxDistBlocks;
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
        return Component.translatable(translationKey(outcome));
    }

    /** Same keys as {@link #message(Outcome)}; used for wrapped action-bar toasts from the server. */
    public static String translationKey(Outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> "projectisland.claim.success";
            case NOT_FLOATING_WORLD -> "projectisland.claim.not_floating_world";
            case NO_ISLAND_COLUMN -> "projectisland.claim.no_island_column";
            case NOT_AVAILABLE -> "projectisland.claim.not_available";
            case ROPE_REQUIRED -> "projectisland.claim.no_rope_link";
            case TOO_FAR_FROM_DOCK -> "projectisland.claim.too_far_from_dock";
            case NOT_LINKED_ANCHOR -> "projectisland.claim.not_linked_anchor";
            case RACE_LOST -> "projectisland.claim.race_lost";
        };
    }
}
