package net.projectisland.island;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Serialized claim row for one {@link FloatingIslandKey}. Versioning is handled at the {@link FloatingIslandSavedData} file level.
 */
public final class IslandRecord {
    private IslandState state = IslandState.AVAILABLE;
    @Nullable
    private UUID owner;
    private long claimedAtGameTime;

    public IslandState state() {
        return state;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public long claimedAtGameTime() {
        return claimedAtGameTime;
    }

    public void setState(IslandState next) {
        this.state = next;
    }

    public void setClaimed(UUID newOwner, long gameTime) {
        this.state = IslandState.CLAIMED;
        this.owner = newOwner;
        this.claimedAtGameTime = gameTime;
    }

    public void clearClaim() {
        this.state = IslandState.AVAILABLE;
        this.owner = null;
        this.claimedAtGameTime = 0L;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("State", state.name());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putLong("ClaimedAt", claimedAtGameTime);
        return tag;
    }

    public static IslandRecord read(CompoundTag tag) {
        IslandRecord r = new IslandRecord();
        if (tag.contains("State")) {
            try {
                r.state = IslandState.valueOf(tag.getString("State"));
            } catch (IllegalArgumentException e) {
                r.state = IslandState.AVAILABLE;
            }
        }
        if (tag.hasUUID("Owner")) {
            r.owner = tag.getUUID("Owner");
        }
        r.claimedAtGameTime = tag.getLong("ClaimedAt");
        return r;
    }
}
