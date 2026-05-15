package net.projectisland.end;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.projectisland.ProjectIsland;

/**
 * Persists scheduled Ender Dragon respawn: witnesses (players in the End at kill time) and countdown target game time.
 */
public final class DragonResetSavedData extends SavedData {
    public static final String FILE_ID = ProjectIsland.MOD_ID + "_dragon_reset";
    public static final SavedData.Factory<DragonResetSavedData> FACTORY =
            new SavedData.Factory<>(DragonResetSavedData::new, DragonResetSavedData::load, DataFixTypes.LEVEL);

    private static final String TAG_STATE = "State";
    private static final String TAG_WITNESSES = "Witnesses";
    private static final String TAG_TARGET_GAME_TIME = "TargetGameTime";

    /** No schedule. */
    public static final byte STATE_IDLE = 0;
    /** Dragon slain; waiting until no witness remains in the End. */
    public static final byte STATE_AWAIT_LEAVE_END = 1;
    /** All witnesses left; counting down to respawn. */
    public static final byte STATE_COUNTDOWN = 2;

    private byte state = STATE_IDLE;
    private final Set<UUID> witnesses = new HashSet<>();
    /** Inclusive: respawn runs when {@code level.getGameTime() >= targetGameTime}. */
    private long targetGameTime;

    public DragonResetSavedData() {}

    public static DragonResetSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DragonResetSavedData d = new DragonResetSavedData();
        d.read(tag);
        return d;
    }

    private void read(CompoundTag root) {
        state = root.getByte(TAG_STATE);
        witnesses.clear();
        ListTag list = root.getList(TAG_WITNESSES, Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            witnesses.add(NbtUtils.loadUUID(list.get(i)));
        }
        targetGameTime = root.getLong(TAG_TARGET_GAME_TIME);
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putByte(TAG_STATE, state);
        ListTag list = new ListTag();
        for (UUID u : witnesses) {
            list.add(NbtUtils.createUUID(u));
        }
        root.put(TAG_WITNESSES, list);
        root.putLong(TAG_TARGET_GAME_TIME, targetGameTime);
        return root;
    }

    public byte state() {
        return state;
    }

    public void setState(byte state) {
        this.state = state;
        setDirty();
    }

    public Set<UUID> witnessesView() {
        return Set.copyOf(witnesses);
    }

    public void clearWitnesses() {
        witnesses.clear();
        setDirty();
    }

    public void setWitnesses(Set<UUID> copy) {
        witnesses.clear();
        witnesses.addAll(copy);
        setDirty();
    }

    public long targetGameTime() {
        return targetGameTime;
    }

    public void setTargetGameTime(long targetGameTime) {
        this.targetGameTime = targetGameTime;
        setDirty();
    }

    public void resetToIdle() {
        state = STATE_IDLE;
        witnesses.clear();
        targetGameTime = 0L;
        setDirty();
    }
}
