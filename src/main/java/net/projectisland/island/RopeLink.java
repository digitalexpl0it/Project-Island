package net.projectisland.island;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/** Server-authored rope link between two island regions. */
public record RopeLink(
        UUID id,
        UUID owner,
        FloatingIslandKey fromKey,
        FloatingIslandKey toKey,
        BlockPos fromAnchorPos,
        BlockPos toAnchorPos,
        double maxLengthBlocks,
        float health,
        float maxHealth) {

    public RopeLink withHealth(float newHealth) {
        return new RopeLink(
                id, owner, fromKey, toKey, fromAnchorPos, toAnchorPos, maxLengthBlocks, newHealth, maxHealth);
    }

    public float healthFraction() {
        if (maxHealth <= 1e-6f) {
            return 0f;
        }
        return Mth.clamp(health / maxHealth, 0f, 1f);
    }

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

