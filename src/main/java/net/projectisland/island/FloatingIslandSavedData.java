package net.projectisland.island;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;

/**
 * Persisted island-region rows (legacy {@link IslandState} for older worlds), starter-home mappings, and rope links for
 * the floating-islands overworld. Ropes are **not** gated on claims; starters only use {@linkplain #starterHomes}.
 */
public final class FloatingIslandSavedData extends SavedData {
    public static final String FILE_ID = ProjectIsland.MOD_ID + "_floating_islands";
    public static final SavedData.Factory<FloatingIslandSavedData> FACTORY =
            new SavedData.Factory<>(FloatingIslandSavedData::new, FloatingIslandSavedData::load, DataFixTypes.LEVEL);

    private static final String TAG_VERSION = "Version";
    private static final String TAG_ISLANDS = "Islands";
    private static final String TAG_STARTER_HOMES = "StarterHomes";
    private static final String TAG_ROPE_LINKS = "RopeLinks";
    private static final String TAG_STARTER_SPAWN_BASELINE = "StarterSpawnBaseline";
    private static final String TAG_SHARED_STARTER_HUB = "SharedStarterHub";
    private static final String TAG_WAYSTONE_ISLAND_HITS = "WaystoneIslandHits";
    private static final int CURRENT_VERSION = 2;

    private final Map<FloatingIslandKey, IslandRecord> islands = new HashMap<>();
    /** Players who received the one-time starter island grant (UUID → key). */
    private final Map<UUID, FloatingIslandKey> starterHomes = new HashMap<>();
    private final Map<UUID, RopeLink> ropeLinks = new HashMap<>();
    /** Overworld shared spawn XZ captured on first starter assignment; {@link Integer#MIN_VALUE} = unset. */
    private int starterSpawnBaselineX = Integer.MIN_VALUE;
    private int starterSpawnBaselineZ = Integer.MIN_VALUE;
    /** First island claimed as the shared starter hub when {@link Config#STARTER_ISLAND_SHARED_HUB} is used. */
    private FloatingIslandKey sharedStarterHubKey;

    /**
     * Packed procedural island region keys ({@link #packIslandRegionKey}) where this player has used a Waystones block
     * on that island — synced to the client for Xaero “hit” (gold) tint.
     */
    private final Map<UUID, HashSet<Long>> playerWaystoneIslandHits = new HashMap<>();

    public FloatingIslandSavedData() {}

    private static long packIslandRegionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xffffffffL);
    }

    public static FloatingIslandSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FloatingIslandSavedData d = new FloatingIslandSavedData();
        d.read(tag, registries);
        return d;
    }

    private void read(CompoundTag root, HolderLookup.Provider registries) {
        islands.clear();
        starterHomes.clear();
        ropeLinks.clear();
        starterSpawnBaselineX = Integer.MIN_VALUE;
        starterSpawnBaselineZ = Integer.MIN_VALUE;
        sharedStarterHubKey = null;
        playerWaystoneIslandHits.clear();
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
        if (root.contains(TAG_STARTER_SPAWN_BASELINE)) {
            CompoundTag b = root.getCompound(TAG_STARTER_SPAWN_BASELINE);
            starterSpawnBaselineX = b.getInt("X");
            starterSpawnBaselineZ = b.getInt("Z");
        }
        if (root.contains(TAG_SHARED_STARTER_HUB)) {
            FloatingIslandKey.parseStorageKey(root.getString(TAG_SHARED_STARTER_HUB)).ifPresent(k -> sharedStarterHubKey = k);
        }
        if (sharedStarterHubKey == null && !starterHomes.isEmpty()) {
            FloatingIslandKey only = null;
            boolean conflict = false;
            for (FloatingIslandKey k : starterHomes.values()) {
                if (only == null) {
                    only = k;
                } else if (!only.equals(k)) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict && only != null) {
                sharedStarterHubKey = only;
                setDirty();
            }
        }
        if (root.contains(TAG_ROPE_LINKS)) {
            CompoundTag rl = root.getCompound(TAG_ROPE_LINKS);
            for (String idStr : rl.getAllKeys()) {
                try {
                    UUID id = UUID.fromString(idStr);
                    CompoundTag t = rl.getCompound(idStr);
                    UUID owner = t.hasUUID("Owner") ? t.getUUID("Owner") : new UUID(0L, 0L);
                    Optional<FloatingIslandKey> fromKey = FloatingIslandKey.parseStorageKey(t.getString("FromKey"));
                    Optional<FloatingIslandKey> toKey = FloatingIslandKey.parseStorageKey(t.getString("ToKey"));
                    if (fromKey.isEmpty() || toKey.isEmpty()) {
                        continue;
                    }
                    var fromPos = BlockPos.of(t.getLong("FromPos"));
                    var toPos = BlockPos.of(t.getLong("ToPos"));
                    double maxLen = t.contains("MaxLen") ? t.getDouble("MaxLen") : 96.0d;
                    float maxHp = t.contains("MaxHealth") ? t.getFloat("MaxHealth") : (float) Config.ROPE_LINK_MAX_HEALTH.getAsDouble();
                    float hp = t.contains("Health") ? t.getFloat("Health") : maxHp;
                    hp = Math.min(Math.max(0f, hp), maxHp);
                    ropeLinks.put(id, new RopeLink(id, owner, fromKey.get(), toKey.get(), fromPos, toPos, maxLen, hp, maxHp));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed uuid
                }
            }
        }
        if (root.contains(TAG_WAYSTONE_ISLAND_HITS)) {
            CompoundTag hitsRoot = root.getCompound(TAG_WAYSTONE_ISLAND_HITS);
            for (String uuidStr : hitsRoot.getAllKeys()) {
                try {
                    UUID id = UUID.fromString(uuidStr);
                    long[] arr = hitsRoot.getLongArray(uuidStr);
                    HashSet<Long> set = new HashSet<>(arr.length);
                    for (long v : arr) {
                        set.add(v);
                    }
                    playerWaystoneIslandHits.put(id, set);
                } catch (IllegalArgumentException ignored) {
                    // skip malformed uuid
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
     * Records overworld shared spawn XZ once (first starter-assignment attempt). Used with
     * {@link #hasWorldSpawnMovedFromStarterBaseline} when {@link Config#STARTER_ISLAND_SPLIT_WHEN_WORLD_SPAWN_MOVES} is on.
     */
    public synchronized void captureStarterSpawnBaselineIfUnset(BlockPos spawn) {
        if (starterSpawnBaselineX != Integer.MIN_VALUE) {
            return;
        }
        starterSpawnBaselineX = spawn.getX();
        starterSpawnBaselineZ = spawn.getZ();
        setDirty();
    }

    public synchronized boolean hasWorldSpawnMovedFromStarterBaseline(BlockPos spawn) {
        if (starterSpawnBaselineX == Integer.MIN_VALUE) {
            return false;
        }
        return spawn.getX() != starterSpawnBaselineX || spawn.getZ() != starterSpawnBaselineZ;
    }

    /** True when two or more players have starter homes on different island regions (legacy per-player starters). */
    public synchronized boolean hasMultipleDistinctStarterHomes() {
        if (starterHomes.size() < 2) {
            return false;
        }
        FloatingIslandKey first = null;
        for (FloatingIslandKey k : starterHomes.values()) {
            if (first == null) {
                first = k;
            } else if (!first.equals(k)) {
                return true;
            }
        }
        return false;
    }

    public synchronized Optional<FloatingIslandKey> getSharedStarterHubKey() {
        return Optional.ofNullable(sharedStarterHubKey);
    }

    public synchronized void setSharedStarterHubKeyIfUnset(FloatingIslandKey key) {
        if (sharedStarterHubKey != null) {
            return;
        }
        sharedStarterHubKey = key;
        setDirty();
    }

    /**
     * Adds {@code owner} → {@code hub} in {@linkplain #starterHomes starter homes} only. Requires {@code hub} to match
     * {@linkplain #sharedStarterHubKey} and at least one existing starter-home entry on that hub (first player already
     * assigned).
     */
    public synchronized Optional<FloatingIslandKey> tryAssignStarterHomeAtSharedHub(UUID owner, FloatingIslandKey hub) {
        if (starterHomes.containsKey(owner)) {
            return Optional.empty();
        }
        if (sharedStarterHubKey == null || !sharedStarterHubKey.equals(hub)) {
            return Optional.empty();
        }
        boolean hubHasStarter =
                starterHomes.values().stream().anyMatch(h -> h.equals(hub));
        if (!hubHasStarter) {
            return Optional.empty();
        }
        starterHomes.put(owner, hub);
        setDirty();
        return Optional.of(hub);
    }

    /** Removes only the starter-home mapping. */
    public synchronized void revertStarterHomeMappingOnly(UUID owner) {
        if (starterHomes.remove(owner) != null) {
            setDirty();
        }
    }

    /**
     * Assign {@code key} as {@code owner}'s starter-home region if they do not already have one and no <em>other</em>
     * player uses {@code key} as their starter (shared hub assigns multiple players to the same key via
     * {@link #tryAssignStarterHomeAtSharedHub}). Does not use {@link IslandState#CLAIMED}.
     */
    public synchronized Optional<FloatingIslandKey> tryClaimStarterIsland(FloatingIslandKey key, UUID owner, long gameTime) {
        if (starterHomes.containsKey(owner)) {
            return Optional.empty();
        }
        for (Map.Entry<UUID, FloatingIslandKey> e : starterHomes.entrySet()) {
            if (!e.getKey().equals(owner) && e.getValue().equals(key)) {
                return Optional.empty();
            }
        }
        islands.computeIfAbsent(key, k -> new IslandRecord());
        starterHomes.put(owner, key);
        setDirty();
        return Optional.of(key);
    }

    /**
     * Undo a starter-home mapping when placement failed (e.g. could not find supported feet). Caller must only use this
     * right after {@link #tryClaimStarterIsland} returned {@code key} for the same {@code owner}.
     */
    public synchronized void revertStarterIslandClaim(UUID owner, FloatingIslandKey key) {
        FloatingIslandKey assigned = starterHomes.get(owner);
        if (assigned == null || !assigned.equals(key)) {
            return;
        }
        starterHomes.remove(owner);
        setDirty();
    }

    public synchronized void putRopeLink(RopeLink link) {
        ropeLinks.put(link.id(), link);
        setDirty();
    }

    public synchronized Optional<RopeLink> getRopeLink(UUID id) {
        return Optional.ofNullable(ropeLinks.get(id));
    }

    public synchronized void removeRopeLink(UUID id) {
        if (ropeLinks.remove(id) != null) {
            setDirty();
        }
    }

    /** Snapshot of current links for sync or iteration (server thread). */
    public synchronized List<RopeLink> copyRopeLinks() {
        return new ArrayList<>(ropeLinks.values());
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

    /** Records that {@code player} used a waystone on {@code island} (for HUD / map highlight sync). */
    public synchronized boolean markPlayerUsedWaystoneOnIsland(UUID player, FloatingIslandKey island) {
        long pk = packIslandRegionKey(island.regionX(), island.regionZ());
        HashSet<Long> set = playerWaystoneIslandHits.computeIfAbsent(player, u -> new HashSet<>());
        if (set.add(pk)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Snapshot of packed region keys for {@linkplain net.projectisland.network.IslandHudSyncPayload island HUD sync}. */
    public synchronized List<Long> copyWaystoneIslandHits(UUID player) {
        HashSet<Long> set = playerWaystoneIslandHits.get(player);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        return List.copyOf(set);
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
        if (starterSpawnBaselineX != Integer.MIN_VALUE) {
            CompoundTag b = new CompoundTag();
            b.putInt("X", starterSpawnBaselineX);
            b.putInt("Z", starterSpawnBaselineZ);
            root.put(TAG_STARTER_SPAWN_BASELINE, b);
        }
        if (sharedStarterHubKey != null) {
            root.putString(TAG_SHARED_STARTER_HUB, sharedStarterHubKey.toStorageKey());
        }
        CompoundTag rl = new CompoundTag();
        for (Map.Entry<UUID, RopeLink> e : ropeLinks.entrySet()) {
            RopeLink link = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("Owner", link.owner());
            t.putString("FromKey", link.fromKey().toStorageKey());
            t.putString("ToKey", link.toKey().toStorageKey());
            t.putLong("FromPos", link.fromAnchorPos().asLong());
            t.putLong("ToPos", link.toAnchorPos().asLong());
            t.putDouble("MaxLen", link.maxLengthBlocks());
            t.putFloat("Health", link.health());
            t.putFloat("MaxHealth", link.maxHealth());
            rl.put(e.getKey().toString(), t);
        }
        root.put(TAG_ROPE_LINKS, rl);
        if (!playerWaystoneIslandHits.isEmpty()) {
            CompoundTag hitsRoot = new CompoundTag();
            for (Map.Entry<UUID, HashSet<Long>> e : playerWaystoneIslandHits.entrySet()) {
                HashSet<Long> set = e.getValue();
                long[] arr = set.stream().mapToLong(Long::longValue).toArray();
                hitsRoot.putLongArray(e.getKey().toString(), arr);
            }
            root.put(TAG_WAYSTONE_ISLAND_HITS, hitsRoot);
        }
        return root;
    }
}
