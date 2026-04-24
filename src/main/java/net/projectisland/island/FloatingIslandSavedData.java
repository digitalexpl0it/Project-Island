package net.projectisland.island;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.projectisland.ProjectIsland;

/**
 * Saved island claim rows for the overworld when it uses {@link net.projectisland.worldgen.FloatingIslandsChunkGenerator}.
 */
public final class FloatingIslandSavedData extends SavedData {
    public static final String FILE_ID = ProjectIsland.MOD_ID + "_floating_islands";
    public static final SavedData.Factory<FloatingIslandSavedData> FACTORY =
            new SavedData.Factory<>(FloatingIslandSavedData::new, FloatingIslandSavedData::load, DataFixTypes.LEVEL);

    private static final String TAG_VERSION = "Version";
    private static final String TAG_ISLANDS = "Islands";
    private static final int CURRENT_VERSION = 1;

    private final Map<FloatingIslandKey, IslandRecord> islands = new HashMap<>();

    public FloatingIslandSavedData() {}

    public static FloatingIslandSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FloatingIslandSavedData d = new FloatingIslandSavedData();
        d.read(tag, registries);
        return d;
    }

    private void read(CompoundTag root, HolderLookup.Provider registries) {
        islands.clear();
        if (!root.contains(TAG_ISLANDS)) {
            return;
        }
        CompoundTag sec = root.getCompound(TAG_ISLANDS);
        for (String key : sec.getAllKeys()) {
            FloatingIslandKey.parseStorageKey(key).ifPresent(k -> islands.put(k, IslandRecord.read(sec.getCompound(key))));
        }
    }

    public IslandRecord getOrCreate(FloatingIslandKey key) {
        IslandRecord existing = islands.get(key);
        if (existing != null) {
            return existing;
        }
        IslandRecord created = new IslandRecord();
        islands.put(key, created);
        setDirty();
        return created;
    }

    public Optional<IslandRecord> getIfPresent(FloatingIslandKey key) {
        return Optional.ofNullable(islands.get(key));
    }

    /** Read-only lookup without creating a row (used for HUD sync). */
    public Optional<IslandRecord> peek(FloatingIslandKey key) {
        return Optional.ofNullable(islands.get(key));
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt(TAG_VERSION, CURRENT_VERSION);
        CompoundTag sec = new CompoundTag();
        for (Map.Entry<FloatingIslandKey, IslandRecord> e : islands.entrySet()) {
            sec.put(e.getKey().toStorageKey(), e.getValue().write());
        }
        root.put(TAG_ISLANDS, sec);
        return root;
    }
}
