package net.projectisland.island;

import java.util.UUID;

import net.minecraft.core.BlockPos;

/** Server-authored rope link between two island regions. */
public record RopeLink(
        UUID id,
        UUID owner,
        FloatingIslandKey fromKey,
        FloatingIslandKey toKey,
        BlockPos fromAnchorPos,
        BlockPos toAnchorPos,
        double maxLengthBlocks) {

    /** The opposite anchor position for one end of the link, or null if {@code end} is not an endpoint. */
    public BlockPos otherAnchor(BlockPos end) {
        if (end.equals(fromAnchorPos)) {
            return toAnchorPos;
        }
        if (end.equals(toAnchorPos)) {
            return fromAnchorPos;
        }
        return null;
    }
}

