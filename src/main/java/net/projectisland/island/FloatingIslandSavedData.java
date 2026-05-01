package net.projectisland.island;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
 * Saved island claim rows for the overworld when it uses {@link net.projectisland.worldgen.FloatingIslandsChunkGenerator}.
 */
public final class FloatingIslandSavedData extends SavedData {
    public static final String FILE_ID = ProjectIsland.MOD_ID + "_floating_islands";
    public static final SavedData.Factory<FloatingIslandSavedData> FACTORY =
            new SavedData.Factory<>(FloatingIslandSavedData::new, FloatingIslandSavedData::load, DataFixTypes.LEVEL);

    private static final String TAG_VERSION = "Version";
    private static final String TAG_ISLANDS = "Islands";
    private static final String TAG_STARTER_HOMES = "StarterHomes";
    private static final String TAG_ROPE_LINKS = "RopeLinks";
    private static final int CURRENT_VERSION = 1;

    private final Map<FloatingIslandKey, IslandRecord> islands = new HashMap<>();
    /** Players who received the one-time starter island grant (UUID → key). */
    private final Map<UUID, FloatingIslandKey> starterHomes = new HashMap<>();
    private final Map<UUID, RopeLink> ropeLinks = new HashMap<>();

    public FloatingIslandSavedData() {}

    public static FloatingIslandSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FloatingIslandSavedData d = new FloatingIslandSavedData();
        d.read(tag, registries);
        return d;
    }

    private void read(CompoundTag root, HolderLookup.Provider registries) {
        islands.clear();
        starterHomes.clear();
        ropeLinks.clear();
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
     * Undo a starter-home row when placement failed (e.g. could not find a supported feet column). Caller must only use
     * this right after {@link #tryClaimStarterIsland} returned {@code key} for the same {@code owner}.
     */
    public synchronized void revertStarterIslandClaim(UUID owner, FloatingIslandKey key) {
        FloatingIslandKey assigned = starterHomes.get(owner);
        if (assigned == null || !assigned.equals(key)) {
            return;
        }
        starterHomes.remove(owner);
        IslandRecord rec = islands.get(key);
        if (rec != null && rec.state() == IslandState.CLAIMED && owner.equals(rec.owner())) {
            rec.clearClaim();
        }
        setDirty();
    }

    /**
     * Atomically claim {@code key} for {@code owner} if missing or {@link IslandState#AVAILABLE}. Does not touch
     * {@linkplain #starterHomes starter homes} — for secondary claims (e.g. OP command until dock/link gameplay exists).
     */
    private synchronized boolean islandClaimedBy(FloatingIslandKey key, UUID owner) {
        return peek(key).map(r -> r.state() == IslandState.CLAIMED && owner.equals(r.owner())).orElse(false);
    }

    /** True if this region is {@link IslandState#CLAIMED} by {@code owner}. */
    public synchronized boolean isClaimedByPlayer(FloatingIslandKey key, UUID owner) {
        return islandClaimedBy(key, owner);
    }

    /**
     * True if {@code claimer} owns a {@link RopeLink} whose endpoints are {@code targetToClaim} and another island
     * they already have {@link IslandState#CLAIMED}.
     */
    public synchronized boolean hasRopeLinkFromClaimedIsland(UUID claimer, FloatingIslandKey targetToClaim) {
        Optional<FloatingIslandKey> starter = getStarterHome(claimer);
        for (RopeLink link : copyRopeLinks()) {
            if (!claimer.equals(link.owner())) {
                continue;
            }
            if (!link.fromKey().equals(targetToClaim) && !link.toKey().equals(targetToClaim)) {
                continue;
            }
            FloatingIslandKey other = link.fromKey().equals(targetToClaim) ? link.toKey() : link.fromKey();
            if (islandClaimedBy(other, claimer)) {
                return true;
            }
            // Rope to your starter-home region always counts, even if the island row is missing CLAIMED (data edge case).
            if (starter.isPresent() && starter.get().equals(other)) {
                return true;
            }
        }
        return false;
    }

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

    /**
     * Call only after the new {@link RopeLink} is already in {@link #ropeLinks}. If one endpoint is this player's
     * starter or a region they already claim, and the other is {@link IslandState#AVAILABLE}, claims the available
     * region (same outcome as {@link #trySecondaryClaim} when used from the harpoon).
     */
    public synchronized boolean tryAutoClaimIslandAfterRopePlaced(UUID owner, FloatingIslandKey a, FloatingIslandKey b, long gameTime) {
        if (!Config.AUTO_CLAIM_ON_ROPE_LINK.getAsBoolean()) {
            return false;
        }
        Optional<FloatingIslandKey> starter = getStarterHome(owner);
        boolean hubA = isClaimedByPlayer(a, owner) || starter.filter(a::equals).isPresent();
        boolean hubB = isClaimedByPlayer(b, owner) || starter.filter(b::equals).isPresent();
        boolean availA = peek(a).map(r -> r.state() == IslandState.AVAILABLE).orElse(true);
        boolean availB = peek(b).map(r -> r.state() == IslandState.AVAILABLE).orElse(true);
        if (hubA && availB) {
            return trySecondaryClaim(b, owner, gameTime);
        }
        if (hubB && availA) {
            return trySecondaryClaim(a, owner, gameTime);
        }
        return false;
    }

    public synchronized void putRopeLink(RopeLink link) {
        ropeLinks.put(link.id(), link);
        setDirty();
    }

    public synchronized Optional<RopeLink> getRopeLink(UUID id) {
        return Optional.ofNullable(ropeLinks.get(id));
    }

    public synchronized void removeRopeLink(UUID id) {
        RopeLink removed = ropeLinks.remove(id);
        if (removed != null) {
            setDirty();
            if (Config.SECONDARY_CLAIM_REQUIRES_ROPE_LINK.getAsBoolean()) {
                revalidateRopeBackedClaimsForOwner(removed.owner());
            }
        }
    }

    /**
     * Secondary islands (not this player's starter home) must keep a direct owned {@linkplain RopeLink} to another
     * island they {@linkplain #islandClaimedBy claim}; otherwise they return to {@link IslandState#AVAILABLE}.
     */
    public synchronized void revalidateRopeBackedClaimsForOwner(UUID owner) {
        Optional<FloatingIslandKey> starter = getStarterHome(owner);
        boolean changed = false;
        for (Map.Entry<FloatingIslandKey, IslandRecord> e : new ArrayList<>(islands.entrySet())) {
            IslandRecord rec = e.getValue();
            if (rec.state() != IslandState.CLAIMED || !owner.equals(rec.owner())) {
                continue;
            }
            FloatingIslandKey key = e.getKey();
            if (starter.filter(key::equals).isPresent()) {
                continue;
            }
            if (!hasRopeLinkFromClaimedIsland(owner, key)) {
                rec.clearClaim();
                changed = true;
            }
        }
        if (changed) {
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
        return root;
    }
}
