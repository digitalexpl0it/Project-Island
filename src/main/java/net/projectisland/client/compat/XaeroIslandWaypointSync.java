package net.projectisland.client.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.projectisland.ClientConfig;
import net.projectisland.ProjectIsland;
import net.projectisland.network.IslandHudSyncPayload.IslandHudBeacon;
import net.projectisland.worldgen.FloatingIslandLayout;

/**
 * When <strong>Xaero's Minimap</strong> is installed, mirror synced island HUD beacons as waypoints (same set is
 * shown on <strong>Xaero's World Map</strong> when that mod is present — it shares minimap waypoint data).
 * Uses the same “void navigation” rule as {@link net.projectisland.client.IslandHudRenderer}: when the server sends
 * multiple beacons (open void), world billboard labels stay disabled unless
 * {@link ClientConfig#ISLAND_HUD_WORLD_BILLBOARD_VOID_NAVIGATION} is enabled. With billboards off, multi-beacon payloads
 * still include the procedural scan ring on the server, but Xaero only <strong>upserts</strong> islands in the Waystone
 * visit set (merged in by {@linkplain net.projectisland.island.IslandHudServerSync}) — not every scanned region — so
 * reconnect restores distant gold pins without filling the map with gray pins when you leave an island.
 * <p><strong>Single-beacon</strong> (on island): <strong>upserts</strong> one **`[Island] `** pin per procedural region
 * (deduped with {@link FloatingIslandLayout}'s grid) so visiting several islands <strong>accumulates</strong> waypoints
 * instead of replacing the list. {@link ClientConfig#ISLAND_HUD_XAERO_WAYPOINT_TEMPORARY}: unvisited pins are Xaero
 * **temporary** (default); {@linkplain ClientConfig#ISLAND_HUD_XAERO_WAYPOINT_COLOR_HIT GOLD} waystone-hit pins are always
 * **persistent**.
 * Pin colors: {@link ClientConfig#ISLAND_HUD_XAERO_WAYPOINT_COLOR_HIT} (**GOLD** preset) only for procedural regions the
 * server lists in {@linkplain net.projectisland.client.IslandHudClientCache#waystoneVisitedRegionKeys() waystone visits}
 * (player **used** a Waystones block there); otherwise {@link ClientConfig#ISLAND_HUD_XAERO_WAYPOINT_COLOR_DEFAULT}.
 * Single-beacon upsert also drops same-title pins within {@link #STALE_DUPLICATE_TITLE_CHEBYSHEV_BLOCKS} blocks
 * (Chebyshev) when their coords-derived region differs from the server's — avoids duplicate rows after crossing
 * merged-island boundaries.
 * <p>While Xaero’s waypoint list screen ({@code xaero.common.gui.GuiWaypoints}) is open, mutations are
 * <strong>deferred</strong> (flushed on the next client tick after it closes) so the backing list is not changed
 * mid-render (Xaero can otherwise crash with an empty sub-list).
 * <p><strong>Empty beacon list:</strong> the server can send no HUD beacons while you still have
 * {@code waystoneVisitedRegionKeys} (void / scan gap). We only strip managed pins when <strong>both</strong> lists are
 * empty (matches sync-off); otherwise we refresh colors only so gold pins survive reconnect.
 * Reflection only.
 */
@EventBusSubscriber(modid = ProjectIsland.MOD_ID, value = Dist.CLIENT)
public final class XaeroIslandWaypointSync {
    private XaeroIslandWaypointSync() {}

    /** Xaero waypoint editor — mutating {@link Refs#getList the live set} while this screen renders can crash (stale list rows). */
    private static final String XAERO_GUI_WAYPOINTS = "xaero.common.gui.GuiWaypoints";

    private static final Object DEFER_LOCK = new Object();
    private static List<IslandHudBeacon> deferredBeacons;
    private static List<Long> deferredVisited;

    /** Visible prefix for HUD-mirrored pins (sync appends an invisible ownership suffix — {@link #isSyncManagedIslandWaypointName}). */
    public static final String WAYPOINT_NAME_PREFIX = "[Island] ";

    /**
     * Zero-width non-joiner ({@code U+200C}) appended only to Project Island–created names so we never remove, recolor,
     * or flip temporary/persistent on player-made waypoints — including manual {@link #WAYPOINT_NAME_PREFIX} pins.
     */
    private static final String MANAGED_WAYPOINT_NAME_SUFFIX = "\u200C";

    /**
     * Merged islands can win different {@linkplain FloatingIslandLayout#islandOwningSurface owner regions} as you move.
     * Pins anchored at the previous region center keep a different coords-derived grid key than the server's
     * {@linkplain IslandHudBeacon#regionX() beacon} key — normal dedup misses and Xaero shows duplicate rows (often same
     * Waystones title). Remove same-title stale pins within this horizontal Chebyshev distance of the new beacon.
     */
    private static final int STALE_DUPLICATE_TITLE_CHEBYSHEV_BLOCKS = 256;

    private static final AtomicReference<Optional<Refs>> REFS = new AtomicReference<>(Optional.empty());
    private static volatile boolean probeFailed;

    private static final class Refs {
        final Method getCurrentSession;
        final Method getWaypointsManager;
        final Method getWaypoints;
        final Method getList;
        final Method removeAll;
        final Method addWaypoint;
        final Method updateWaypoints;
        final Constructor<?> waypointCtorWaypointColor;
        final Method setTemporary;
        final Method setWaypointColor;
        final Class<?> waypointColorClass;
        final Method setVisibility;
        final Object visibilityGlobal;

        private Refs(
                Method getCurrentSession,
                Method getWaypointsManager,
                Method getWaypoints,
                Method getList,
                Method removeAll,
                Method addWaypoint,
                Method updateWaypoints,
                Constructor<?> waypointCtorWaypointColor,
                Method setTemporary,
                Method setWaypointColor,
                Class<?> waypointColorClass,
                Method setVisibility,
                Object visibilityGlobal) {
            this.getCurrentSession = getCurrentSession;
            this.getWaypointsManager = getWaypointsManager;
            this.getWaypoints = getWaypoints;
            this.getList = getList;
            this.removeAll = removeAll;
            this.addWaypoint = addWaypoint;
            this.updateWaypoints = updateWaypoints;
            this.waypointCtorWaypointColor = waypointCtorWaypointColor;
            this.setTemporary = setTemporary;
            this.setWaypointColor = setWaypointColor;
            this.waypointColorClass = waypointColorClass;
            this.setVisibility = setVisibility;
            this.visibilityGlobal = visibilityGlobal;
        }

        static Optional<Refs> tryCreate() {
            try {
                Class<?> sessionCl = Class.forName("xaero.common.XaeroMinimapSession");
                Method getCurrentSession = sessionCl.getMethod("getCurrentSession");
                Method getWaypointsManager = sessionCl.getMethod("getWaypointsManager");

                Class<?> mgrCl = Class.forName("xaero.common.minimap.waypoints.WaypointsManager");
                Method getWaypoints = mgrCl.getMethod("getWaypoints");
                Method updateWaypoints = mgrCl.getMethod("updateWaypoints");

                Class<?> setCl = Class.forName("xaero.common.minimap.waypoints.WaypointSet");
                Method getList = setCl.getMethod("getList");
                Method removeAll = setCl.getMethod("removeAll", java.util.Collection.class);
                Method addWaypoint = setCl.getMethod("add", Class.forName("xaero.common.minimap.waypoints.Waypoint"));

                Class<?> wpCl = Class.forName("xaero.common.minimap.waypoints.Waypoint");
                Class<?> wcCl = Class.forName("xaero.hud.minimap.waypoint.WaypointColor");
                Constructor<?> ctor =
                        wpCl.getConstructor(int.class, int.class, int.class, String.class, String.class, wcCl);
                Method setTemporary = wpCl.getMethod("setTemporary", boolean.class);
                Method setWaypointColor = wpCl.getMethod("setWaypointColor", wcCl);

                Class<?> visCl = Class.forName("xaero.common.minimap.waypoints.WaypointVisibilityType");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object global = Enum.valueOf((Class) visCl, "GLOBAL");
                Method setVisibility = wpCl.getMethod("setVisibility", visCl);

                return Optional.of(
                        new Refs(
                                getCurrentSession,
                                getWaypointsManager,
                                getWaypoints,
                                getList,
                                removeAll,
                                addWaypoint,
                                updateWaypoints,
                                ctor,
                                setTemporary,
                                setWaypointColor,
                                wcCl,
                                setVisibility,
                                global));
            } catch (ReflectiveOperationException | LinkageError e) {
                ProjectIsland.LOGGER.debug("Project Island: Xaero minimap waypoint API not available ({})", e.toString());
                return Optional.empty();
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object waypointColor(Refs r, String configured, String fallbackName) {
        try {
            String n = configured.trim().toUpperCase(Locale.ROOT);
            return Enum.valueOf((Class) r.waypointColorClass, n);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            ProjectIsland.LOGGER.debug(
                    "Project Island: invalid Xaero WaypointColor '{}', using {}", configured, fallbackName);
            return Enum.valueOf((Class) r.waypointColorClass, fallbackName);
        }
    }

    private static Optional<Refs> refs() {
        Optional<Refs> cached = REFS.get();
        if (cached.isPresent() || probeFailed) {
            return cached;
        }
        synchronized (XaeroIslandWaypointSync.class) {
            if (REFS.get().isPresent() || probeFailed) {
                return REFS.get();
            }
            Optional<Refs> created = Refs.tryCreate();
            if (created.isEmpty()) {
                probeFailed = true;
            }
            REFS.set(created);
            return created;
        }
    }

    /**
     * Called from the client network thread / enqueue work after {@link net.projectisland.client.IslandHudClientCache}
     * updates.
     */
    public static void onHudBeacons(List<IslandHudBeacon> beacons, List<Long> waystoneVisitedRegionKeys) {
        if (!ClientConfig.ISLAND_HUD_XAERO_WAYPOINT_SYNC.getAsBoolean()) {
            return;
        }
        if (!ModList.get().isLoaded("xaerominimap")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<IslandHudBeacon> bCopy = List.copyOf(beacons);
        List<Long> vCopy = List.copyOf(waystoneVisitedRegionKeys);
        if (isXaeroWaypointsScreen(mc.screen)) {
            synchronized (DEFER_LOCK) {
                deferredBeacons = bCopy;
                deferredVisited = vCopy;
            }
            return;
        }
        applyHudBeaconsNow(bCopy, vCopy);
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            synchronized (DEFER_LOCK) {
                deferredBeacons = null;
                deferredVisited = null;
            }
            return;
        }
        if (isXaeroWaypointsScreen(mc.screen)) {
            return;
        }
        List<IslandHudBeacon> b;
        List<Long> v;
        synchronized (DEFER_LOCK) {
            if (deferredBeacons == null) {
                return;
            }
            b = deferredBeacons;
            v = deferredVisited;
            deferredBeacons = null;
            deferredVisited = null;
        }
        applyHudBeaconsNow(b, v);
    }

    private static boolean isXaeroWaypointsScreen(Screen screen) {
        return screen != null && XAERO_GUI_WAYPOINTS.equals(screen.getClass().getName());
    }

    static String managedIslandWaypointDisplayName(String islandTitle) {
        return WAYPOINT_NAME_PREFIX + islandTitle + MANAGED_WAYPOINT_NAME_SUFFIX;
    }

    /** {@code true} only for waypoint rows this mod creates — safe to sync color/temporary/remove. */
    static boolean isSyncManagedIslandWaypointName(String waypointName) {
        return waypointName != null
                && waypointName.startsWith(WAYPOINT_NAME_PREFIX)
                && waypointName.endsWith(MANAGED_WAYPOINT_NAME_SUFFIX);
    }

    private static void applyHudBeaconsNow(List<IslandHudBeacon> beacons, List<Long> waystoneVisitedRegionKeys) {
        Optional<Refs> refOpt = refs();
        if (refOpt.isEmpty()) {
            return;
        }
        Refs r = refOpt.get();
        try {
            Object session = r.getCurrentSession.invoke(null);
            if (session == null) {
                return;
            }
            Object mgr = r.getWaypointsManager.invoke(session);
            if (mgr == null) {
                return;
            }
            Object set = r.getWaypoints.invoke(mgr);
            if (set == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) r.getList.invoke(set);
            Set<Long> visited = new HashSet<>(waystoneVisitedRegionKeys);

            /*
             * Empty beacon list happens in open void when no procedural islands fall inside the server scan radius, or
             * briefly before columns resolve — the server still sends waystoneVisitedRegionKeys. Clearing all [Island]
             * pins on every empty beacon payload wiped persistent (gold) waypoints after reconnect.
             * Match sync-disabled: both lists empty → remove managed pins.
             */
            if (beacons.isEmpty()) {
                if (visited.isEmpty()) {
                    removeManagedIslandWaypoints(r, set, list);
                } else {
                    applyManagedIslandWaypointColors(r, list, visited);
                }
                r.updateWaypoints.invoke(mgr);
                return;
            }

            /*
             * Void navigation billboards off + multiple beacons: the payload includes the full procedural scan ring — upserting
             * every row would mirror hundreds of gray pins to Xaero. Only upsert regions in {@code visited} (Waystone hits);
             * the server merges those into the payload so distant gold pins still resolve after reconnect. Scan-only islands
             * stay navigation-only for the world HUD when enabled; landing still uses single-beacon upsert for local pins.
             */
            if (!ClientConfig.ISLAND_HUD_WORLD_BILLBOARD_VOID_NAVIGATION.getAsBoolean() && beacons.size() != 1) {
                List<Object> cur = list;
                for (IslandHudBeacon b : beacons) {
                    if (visited.contains(islandRegionGridKey(b.regionX(), b.regionZ()))) {
                        upsertSingleBeacon(r, set, cur, b, visited);
                        cur = (List<Object>) r.getList.invoke(set);
                    }
                }
                applyManagedIslandWaypointColors(r, cur, visited);
                r.updateWaypoints.invoke(mgr);
                return;
            }

            if (beacons.size() == 1) {
                IslandHudBeacon b = beacons.getFirst();
                upsertSingleBeacon(r, set, list, b, visited);
                List<Object> fresh = (List<Object>) r.getList.invoke(set);
                applyManagedIslandWaypointColors(r, fresh, visited);
            } else {
                /*
                 * Full void-navigation refresh: keep [Island] pins for regions in the payload OR in waystone visits.
                 * Replacing with only the scan radius used to delete gold pins for islands behind you.
                 */
                Set<Long> payloadKeys = new HashSet<>();
                for (IslandHudBeacon b : beacons) {
                    payloadKeys.add(islandRegionGridKey(b.regionX(), b.regionZ()));
                }
                Set<Long> keepKeys = new HashSet<>(visited);
                keepKeys.addAll(payloadKeys);

                List<Object> toDrop = new ArrayList<>();
                for (Object wp : list) {
                    if (wp == null) {
                        continue;
                    }
                    String name = (String) wp.getClass().getMethod("getName").invoke(wp);
                    if (!isSyncManagedIslandWaypointName(name)) {
                        continue;
                    }
                    Optional<Long> rk = regionKeyOfWaypoint(wp);
                    if (rk.isEmpty()) {
                        continue;
                    }
                    if (!keepKeys.contains(rk.get())) {
                        toDrop.add(wp);
                    }
                }
                if (!toDrop.isEmpty()) {
                    r.removeAll.invoke(set, toDrop);
                }
                @SuppressWarnings("unchecked")
                List<Object> cur = (List<Object>) r.getList.invoke(set);
                for (IslandHudBeacon b : beacons) {
                    upsertSingleBeacon(r, set, cur, b, visited);
                    cur = (List<Object>) r.getList.invoke(set);
                }
                applyManagedIslandWaypointColors(r, cur, visited);
            }
            r.updateWaypoints.invoke(mgr);
        } catch (ReflectiveOperationException | LinkageError e) {
            if (!probeFailed) {
                ProjectIsland.LOGGER.debug("Project Island: failed to sync island HUD to Xaero waypoints", e);
            }
        }
    }

    /**
     * Authoritative packed key from server {@linkplain net.projectisland.island.FloatingIslandKey island region}
     * indices — use for highlight / upsert target so merged islands and float rounding cannot desync from waypoint coords.
     */
    static long islandRegionGridKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xffffffffL);
    }

    /** Same region grid as {@link FloatingIslandLayout#islandOwningSurface} — derived from block coords (waypoint pins). */
    static long islandRegionKeyFromBlock(int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        int rcx = Mth.floorDiv(chunkX, FloatingIslandLayout.REGION_CHUNKS);
        int rcz = Mth.floorDiv(chunkZ, FloatingIslandLayout.REGION_CHUNKS);
        return islandRegionGridKey(rcx, rcz);
    }

    private static Optional<Long> regionKeyOfWaypoint(Object wp) {
        try {
            Class<?> cl = wp.getClass();
            Object xObj = cl.getMethod("getX").invoke(wp);
            Object zObj = cl.getMethod("getZ").invoke(wp);
            int x = ((Number) xObj).intValue();
            int z = ((Number) zObj).intValue();
            return Optional.of(islandRegionKeyFromBlock(x, z));
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException ignored) {
            return Optional.empty();
        }
    }

    private static boolean waypointMatchesBeacon(Object wp, int x, int y, int z, String display) {
        try {
            Class<?> cl = wp.getClass();
            int wx = ((Number) cl.getMethod("getX").invoke(wp)).intValue();
            int wy = ((Number) cl.getMethod("getY").invoke(wp)).intValue();
            int wz = ((Number) cl.getMethod("getZ").invoke(wp)).intValue();
            String name = (String) cl.getMethod("getName").invoke(wp);
            return wx == x && wy == y && wz == z && display.equals(name);
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException ignored) {
            return false;
        }
    }

    private static boolean nearBeaconChebyshev(Object wp, int bx, int bz, int maxDxz) throws ReflectiveOperationException {
        Class<?> cl = wp.getClass();
        int wx = ((Number) cl.getMethod("getX").invoke(wp)).intValue();
        int wz = ((Number) cl.getMethod("getZ").invoke(wp)).intValue();
        return Math.abs(wx - bx) <= maxDxz && Math.abs(wz - bz) <= maxDxz;
    }

    /**
     * Waystone-visited regions are always persistent; {@link ClientConfig#ISLAND_HUD_XAERO_WAYPOINT_TEMPORARY} applies
     * only when the region is not in the server hit set (default {@code true} → gray pins do not save).
     */
    private static boolean xaeroTemporaryForManagedWaypoint(boolean waystoneHitRegion) {
        if (waystoneHitRegion) {
            return false;
        }
        return ClientConfig.ISLAND_HUD_XAERO_WAYPOINT_TEMPORARY.getAsBoolean();
    }

    /**
     * Remove any existing managed waypoint for this island region (or same display name if coords unreadable), then add
     * the current beacon — keeps pins from other islands.
     */
    private static void upsertSingleBeacon(Refs r, Object set, List<Object> list, IslandHudBeacon b, Set<Long> visited)
            throws ReflectiveOperationException {
        int x = Mth.floor(b.x());
        int y = Mth.floor(b.y());
        int z = Mth.floor(b.z());
        String title = b.title();
        String display = managedIslandWaypointDisplayName(title);
        long targetKey = islandRegionGridKey(b.regionX(), b.regionZ());

        List<Object> toRemove = new ArrayList<>();
        for (Object wp : list) {
            if (wp == null) {
                continue;
            }
            String name = (String) wp.getClass().getMethod("getName").invoke(wp);
            if (!isSyncManagedIslandWaypointName(name)) {
                continue;
            }
            Optional<Long> wk = regionKeyOfWaypoint(wp);
            boolean sameAuthoritativeRegion = wk.isPresent() && wk.get() == targetKey;
            /*
             * Do not use display-name matching when both keys are known and differ — otherwise visiting another island
             * that shares a procedural/Waystones title would delete that island's pin.
             */
            boolean staleMergedDuplicateTitle = false;
            if (wk.isPresent()
                    && wk.get() != targetKey
                    && display.equals(name)
                    && nearBeaconChebyshev(wp, x, z, STALE_DUPLICATE_TITLE_CHEBYSHEV_BLOCKS)) {
                staleMergedDuplicateTitle = true;
            }
            boolean unknownKeyMatchesDisplay = wk.isEmpty() && display.equals(name);
            boolean remove = sameAuthoritativeRegion || staleMergedDuplicateTitle || unknownKeyMatchesDisplay;
            if (remove) {
                toRemove.add(wp);
            }
        }
        boolean noopReplacingSelf =
                toRemove.size() == 1 && waypointMatchesBeacon(toRemove.getFirst(), x, y, z, display);
        if (noopReplacingSelf) {
            return;
        }
        if (!toRemove.isEmpty()) {
            r.removeAll.invoke(set, toRemove);
        }
        addIslandWaypoint(r, set, b, visited);
    }

    private static void applyManagedIslandWaypointColors(Refs r, List<Object> list, Set<Long> waystoneVisitedRegions)
            throws ReflectiveOperationException {
        Object gray = waypointColor(r, ClientConfig.ISLAND_HUD_XAERO_WAYPOINT_COLOR_DEFAULT.get(), "DARK_GRAY");
        Object gold = waypointColor(r, ClientConfig.ISLAND_HUD_XAERO_WAYPOINT_COLOR_HIT.get(), "GOLD");
        for (Object wp : list) {
            if (wp == null) {
                continue;
            }
            String name = (String) wp.getClass().getMethod("getName").invoke(wp);
            if (!isSyncManagedIslandWaypointName(name)) {
                continue;
            }
            Optional<Long> rk = regionKeyOfWaypoint(wp);
            boolean hit = rk.isPresent() && waystoneVisitedRegions.contains(rk.get());
            r.setWaypointColor.invoke(wp, hit ? gold : gray);
            r.setTemporary.invoke(wp, xaeroTemporaryForManagedWaypoint(hit));
        }
    }

    private static void addIslandWaypoint(Refs r, Object set, IslandHudBeacon b, Set<Long> visited)
            throws ReflectiveOperationException {
        int x = Mth.floor(b.x());
        int y = Mth.floor(b.y());
        int z = Mth.floor(b.z());
        String title = b.title();
        String display = managedIslandWaypointDisplayName(title);
        String initials = initialsFor(title);
        Object defaultColor = waypointColor(r, ClientConfig.ISLAND_HUD_XAERO_WAYPOINT_COLOR_DEFAULT.get(), "DARK_GRAY");
        Object wp = r.waypointCtorWaypointColor.newInstance(x, y, z, display, initials, defaultColor);
        boolean hit = visited.contains(islandRegionGridKey(b.regionX(), b.regionZ()));
        r.setTemporary.invoke(wp, xaeroTemporaryForManagedWaypoint(hit));
        r.setVisibility.invoke(wp, r.visibilityGlobal);
        r.addWaypoint.invoke(set, wp);
    }

    private static void removeManagedIslandWaypoints(Refs r, Object set, List<Object> list)
            throws ReflectiveOperationException {
        List<Object> toRemove = new ArrayList<>();
        for (Object wp : list) {
            if (wp == null) {
                continue;
            }
            String name = (String) wp.getClass().getMethod("getName").invoke(wp);
            if (isSyncManagedIslandWaypointName(name)) {
                toRemove.add(wp);
            }
        }
        if (!toRemove.isEmpty()) {
            r.removeAll.invoke(set, toRemove);
        }
    }

    private static String initialsFor(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            return "?";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length() && sb.length() < 6; i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "?" : sb.toString();
    }

    /** For unit tests / dev — allow re-probing after classpath changes. */
    public static void resetForTests() {
        synchronized (XaeroIslandWaypointSync.class) {
            REFS.set(Optional.empty());
            probeFailed = false;
        }
        synchronized (DEFER_LOCK) {
            deferredBeacons = null;
            deferredVisited = null;
        }
    }
}
