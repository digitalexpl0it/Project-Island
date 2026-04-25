package net.projectisland.island;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.projectisland.Config;

/**
 * Enforces starter-centric rope limits: max {@linkplain Config#ROPE_TOPOLOGY_MAX_DEPTH_FROM_STARTER depth} from
 * starter, a cap on {@linkplain Config#ROPE_MAIN_DIRECT_SPOKE_CAP direct ropes off the starter}, and a per-island
 * cap on {@linkplain Config#ROPE_SISTER_OUTBOUND_CAP non-starter neighbors} for regions you {@linkplain FloatingIslandSavedData#isClaimedByPlayer claim}.
 */
public final class RopeTopology {
    private RopeTopology() {}

    /**
     * @return empty if the new undirected edge {@code aKey}–{@code bKey} is allowed; otherwise a translation key for the
     *     player (harpoon feedback).
     */
    public static Optional<String> validateNewRopeLink(FloatingIslandSavedData data, UUID owner, FloatingIslandKey aKey, FloatingIslandKey bKey) {
        if (!Config.ROPE_TOPOLOGY_ENABLED.getAsBoolean()) {
            return Optional.empty();
        }
        Optional<FloatingIslandKey> starterOpt = data.getStarterHome(owner);
        if (starterOpt.isEmpty()) {
            return Optional.empty();
        }
        FloatingIslandKey s = starterOpt.get();

        Map<FloatingIslandKey, Set<FloatingIslandKey>> adj = new HashMap<>();
        for (RopeLink link : data.copyRopeLinks()) {
            if (!owner.equals(link.owner())) {
                continue;
            }
            addEdge(adj, link.fromKey(), link.toKey());
        }
        addEdge(adj, aKey, bKey);

        Map<FloatingIslandKey, Integer> depth = bfsDepths(adj, s);
        if (!depth.containsKey(aKey) && !depth.containsKey(bKey)) {
            return Optional.of("projectisland.harpoon.topology_disconnected");
        }
        int configuredMax = Math.max(0, Config.ROPE_TOPOLOGY_MAX_DEPTH_FROM_STARTER.getAsInt());
        int maxDepthCap = configuredMax;
        if (!Config.ROPE_ALLOW_TERTIARY_ISLAND_LINKS.getAsBoolean()) {
            maxDepthCap = Math.min(maxDepthCap, 1);
        }
        int maxFound = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxFound > maxDepthCap) {
            if (!Config.ROPE_ALLOW_TERTIARY_ISLAND_LINKS.getAsBoolean() && maxFound >= 2) {
                return Optional.of("projectisland.harpoon.topology_tertiary_locked");
            }
            return Optional.of("projectisland.harpoon.topology_blocked");
        }

        int mainCap = Math.max(1, Config.ROPE_MAIN_DIRECT_SPOKE_CAP.getAsInt());
        Set<FloatingIslandKey> mainNeigh = new HashSet<>(adj.getOrDefault(s, Set.of()));
        mainNeigh.remove(s);
        if (mainNeigh.size() > mainCap) {
            return Optional.of("projectisland.harpoon.topology_blocked");
        }

        int sisterCap = Math.max(1, Config.ROPE_SISTER_OUTBOUND_CAP.getAsInt());
        for (FloatingIslandKey k : depth.keySet()) {
            if (k.equals(s)) {
                continue;
            }
            if (!data.isClaimedByPlayer(k, owner)) {
                continue;
            }
            Set<FloatingIslandKey> nn = new HashSet<>(adj.getOrDefault(k, Set.of()));
            nn.remove(s);
            if (nn.size() > sisterCap) {
                return Optional.of("projectisland.harpoon.topology_blocked");
            }
        }
        return Optional.empty();
    }

    /**
     * @return {@code false} if the new undirected edge {@code aKey}–{@code bKey} would violate topology (caller should
     *     not place the second anchor).
     */
    public static boolean canAddRopeLink(FloatingIslandSavedData data, UUID owner, FloatingIslandKey aKey, FloatingIslandKey bKey) {
        return validateNewRopeLink(data, owner, aKey, bKey).isEmpty();
    }

    private static void addEdge(Map<FloatingIslandKey, Set<FloatingIslandKey>> adj, FloatingIslandKey x, FloatingIslandKey y) {
        if (x.equals(y)) {
            return;
        }
        adj.computeIfAbsent(x, k -> new HashSet<>()).add(y);
        adj.computeIfAbsent(y, k -> new HashSet<>()).add(x);
    }

    /** Shortest-path depth from {@code start} (start has depth 0). */
    private static Map<FloatingIslandKey, Integer> bfsDepths(
            Map<FloatingIslandKey, Set<FloatingIslandKey>> adj, FloatingIslandKey start) {
        Map<FloatingIslandKey, Integer> depth = new HashMap<>();
        ArrayDeque<FloatingIslandKey> q = new ArrayDeque<>();
        depth.put(start, 0);
        q.add(start);
        while (!q.isEmpty()) {
            FloatingIslandKey u = q.removeFirst();
            int du = depth.get(u);
            for (FloatingIslandKey v : adj.getOrDefault(u, Set.of())) {
                if (!depth.containsKey(v)) {
                    depth.put(v, du + 1);
                    q.add(v);
                }
            }
        }
        return depth;
    }
}
