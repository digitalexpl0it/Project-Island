package net.projectisland.island;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private static final String TAG_STARTER_HOMES = "StarterHomes";
    private static final int CURRENT_VERSION = 1;

    private final Map<FloatingIslandKey, IslandRecord> islands = new HashMap<>();
    /** Players who received the one-time starter island grant (UUID → key). */
    private final Map<UUID, FloatingIslandKey> starterHomes = new HashMap<>();

    public FloatingIslandSavedData() {}

    public static FloatingIslandSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FloatingIslandSavedData d = new FloatingIslandSavedData();
        d.read(tag, registries);
        return d;
    }

    private void read(CompoundTag root, HolderLookup.Provider registries) {
        islands.clear();
        starterHomes.clear();
        if (root.contains(TAG_ISLANDS)) {
            CompoundTag sec = root.getCompound(TAG_ISLANDS);
            for (String key : sec.getAllKeys()) {
                FloatingIslandKey.parseStorageKey(key).ifPresent(k -> islands.put(k, IslandRecord.read(sec.getCompound(key))));
            }
        }
        if (root.contains(TAG_STARTER_HOMES)) {
            CompoundTag st = root.getCompound(TAG_STARTER_HOMES);
            for (String uuidStr : st.getAllKeys()) {
                try {
                    UUID owner = UUID.fromString(uuidStr);
                    String sk = st.getString(uuidStr);
                    FloatingIslandKey.parseStorageKey(sk).ifPresent(k -> starterHomes.put(owner, k));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed uuid or key
                }
            }
        }
    }

    /** Starter island granted on first join, if any. */
    public Optional<FloatingIslandKey> getStarterHome(UUID player) {
        return Optional.ofNullable(starterHomes.get(player));
    }

    /** All island keys currently used as someone's starter home (for spacing new starters). */
    public Collection<FloatingIslandKey> listStarterIslandKeys() {
        return List.copyOf(starterHomes.values());
    }

    /**
     * Atomically claim {@code key} for {@code owner} as starter home if the row is missing or {@link IslandState#AVAILABLE}.
     * Does nothing if {@code owner} already has a starter home entry. Caller must run on the server thread.
     */
    public synchronized Optional<FloatingIslandKey> tryClaimStarterIsland(FloatingIslandKey key, UUID owner, long gameTime) {
        if (starterHomes.containsKey(owner)) {
            return Optional.empty();
        }
        IslandRecord rec = islands.get(key);
        if (rec != null && rec.state() != IslandState.AVAILABLE) {
            return Optional.empty();
        }
        if (rec == null) {
            rec = new IslandRecord();
            islands.put(key, rec);
        }
        if (rec.state() != IslandState.AVAILABLE) {
            return Optional.empty();
        }
        rec.setClaimed(owner, gameTime);
        starterHomes.put(owner, key);
        setDirty();
        return Optional.of(key);
    }

    /**
     * Atomically claim {@code key} for {@code owner} if missing or {@link IslandState#AVAILABLE}. Does not touch
     * {@linkplain #starterHomes starter homes} — for secondary claims (e.g. OP command until dock/link gameplay exists).
     */
    public synchronized boolean trySecondaryClaim(FloatingIslandKey key, UUID owner, long gameTime) {
        IslandRecord rec = islands.get(key);
        if (rec != null && rec.state() != IslandState.AVAILABLE) {
            return false;
        }
        if (rec == null) {
            rec = new IslandRecord();
            islands.put(key, rec);
        }
        if (rec.state() != IslandState.AVAILABLE) {
            return false;
        }
        rec.setClaimed(owner, gameTime);
        setDirty();
        return true;
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
        CompoundTag st = new CompoundTag();
        for (Map.Entry<UUID, FloatingIslandKey> e : starterHomes.entrySet()) {
            st.putString(e.getKey().toString(), e.getValue().toStorageKey());
        }
        root.put(TAG_STARTER_HOMES, st);
        return root;
    }
}
