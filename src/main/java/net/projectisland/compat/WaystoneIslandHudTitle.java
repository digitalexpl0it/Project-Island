package net.projectisland.compat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;
import net.projectisland.worldgen.FloatingIslandLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When the <a href="https://modrinth.com/mod/waystones">Waystones</a> mod is installed, resolve island HUD titles from
 * the nearest waystone with a display name on the procedural island (via Waystones' saved manager list — not a chunk
 * scan). Falls back to {@link net.projectisland.island.FloatingIslandDisplayName}. Reflection only.
 */
public final class WaystoneIslandHudTitle {
    private WaystoneIslandHudTitle() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(WaystoneIslandHudTitle.class);

    private static volatile Throwable reflectionInitFailure;
    private static final AtomicBoolean REFLECTION_FAILURE_LOGGED = new AtomicBoolean();

    private static final class Reflection {
        final Class<?> waystoneBlockEntityBase;
        final Class<?> waystoneApi;
        final Method managerGet;
        final Method managerGetWaystones;
        final Method waystoneGetDimension;
        final Method waystoneGetPos;
        final Method waystoneGetName;
        final Method blockEntityGetWaystone;

        private Reflection(
                Class<?> waystoneBlockEntityBase,
                Class<?> waystoneApi,
                Method managerGet,
                Method managerGetWaystones,
                Method waystoneGetDimension,
                Method waystoneGetPos,
                Method waystoneGetName,
                Method blockEntityGetWaystone) {
            this.waystoneBlockEntityBase = waystoneBlockEntityBase;
            this.waystoneApi = waystoneApi;
            this.managerGet = managerGet;
            this.managerGetWaystones = managerGetWaystones;
            this.waystoneGetDimension = waystoneGetDimension;
            this.waystoneGetPos = waystoneGetPos;
            this.waystoneGetName = waystoneGetName;
            this.blockEntityGetWaystone = blockEntityGetWaystone;
        }

        static Optional<Reflection> tryCreate() {
            try {
                Class<?> base = Class.forName("net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase");
                Class<?> api = Class.forName("net.blay09.mods.waystones.api.Waystone");
                Class<?> mgr = Class.forName("net.blay09.mods.waystones.core.WaystoneManagerImpl");
                Method managerGet = mgr.getMethod("get", net.minecraft.server.MinecraftServer.class);
                Method managerGetWaystones = mgr.getMethod("getWaystones");
                Method getDim = api.getMethod("getDimension");
                Method getPos = api.getMethod("getPos");
                Method getName = api.getMethod("getName");
                Method beGetWaystone = base.getMethod("getWaystone");
                return Optional.of(
                        new Reflection(base, api, managerGet, managerGetWaystones, getDim, getPos, getName, beGetWaystone));
            } catch (ReflectiveOperationException | LinkageError e) {
                reflectionInitFailure = e;
                return Optional.empty();
            }
        }

        boolean isWaystoneBlockEntity(BlockEntity be) {
            return waystoneBlockEntityBase.isInstance(be);
        }

        Optional<String> nameFromWaystoneObject(Object waystone) {
            try {
                if (waystone == null || !waystoneApi.isInstance(waystone)) {
                    return Optional.empty();
                }
                Object comp = waystoneGetName.invoke(waystone);
                if (!(comp instanceof Component component)) {
                    return Optional.empty();
                }
                String s = waystoneNameToPlain(component);
                if (s.isEmpty() || s.equalsIgnoreCase("invalid")) {
                    return Optional.empty();
                }
                return Optional.of(WaystoneIslandHudTitle.truncate(s, 192));
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                return Optional.empty();
            }
        }

        /**
         * Prefer the live tile's {@code getWaystone()} when the chunk is loaded — the manager copy can lag or differ
         * from what Waystones shows after activation.
         */
        Optional<String> resolveDisplayName(ServerLevel level, BlockPos pos, Object managerWaystone) {
            try {
                if (level.isLoaded(pos)) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null && waystoneBlockEntityBase.isInstance(be)) {
                        Object linked = blockEntityGetWaystone.invoke(be);
                        Optional<String> fromTile = nameFromWaystoneObject(linked);
                        if (fromTile.isPresent()) {
                            return fromTile;
                        }
                    }
                }
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                // fall through to manager copy
            }
            return nameFromWaystoneObject(managerWaystone);
        }
    }

    private static boolean dimensionMatches(ServerLevel level, Object dimKey) {
        if (dimKey == null) {
            return false;
        }
        if (level.dimension().equals(dimKey)) {
            return true;
        }
        if (dimKey instanceof ResourceKey<?> rk) {
            return level.dimension().location().equals(rk.location());
        }
        return false;
    }

    /**
     * Strict: same dimension, non-empty name (tile when loaded else manager), inside {@link
     * FloatingIslandLayout#columnContains}. Nearest horizontally to procedural center.
     */
    private static Optional<String> scanStrict(
            ServerLevel level,
            FloatingIslandLayout.IslandParams params,
            int minY,
            int maxY,
            Reflection ref,
            List<?> waystones,
            int regionX,
            int regionZ) {
        double cx = params.centerX + 0.5d;
        double cz = params.centerZ + 0.5d;

        int inDim = 0;
        int namedInDim = 0;
        int passedColumn = 0;

        double bestDist = Double.POSITIVE_INFINITY;
        String bestName = null;
        for (Object w : waystones) {
            try {
                Object dimKey = ref.waystoneGetDimension.invoke(w);
                if (!dimensionMatches(level, dimKey)) {
                    continue;
                }
                inDim++;
                BlockPos pos = (BlockPos) ref.waystoneGetPos.invoke(w);
                if (pos == null) {
                    continue;
                }
                int wx = pos.getX();
                int wz = pos.getZ();
                int y = pos.getY();
                Optional<String> name = ref.resolveDisplayName(level, pos, w);
                if (name.isEmpty()) {
                    continue;
                }
                namedInDim++;
                if (!FloatingIslandLayout.columnContains(wx, wz, y, minY, maxY)) {
                    continue;
                }
                passedColumn++;
                double dx = wx + 0.5d - cx;
                double dz = wz + 0.5d - cz;
                double d2 = dx * dx + dz * dz;
                if (d2 < bestDist) {
                    bestDist = d2;
                    bestName = name.get();
                }
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                // skip entry
            }
        }

        if (bestName != null) {
            return Optional.of(bestName);
        }

        if (Config.DEBUG_LOGGING.getAsBoolean() && ModList.get().isLoaded("waystones")) {
            ProjectIsland.LOGGER.debug(
                    "Waystone HUD title scan (strict) region {},{}: totalWaystones={} inDimension={} namedInDimension={} passedColumnContains={} (no title picked)",
                    regionX,
                    regionZ,
                    waystones.size(),
                    inDim,
                    namedInDim,
                    passedColumn);
        }
        return Optional.empty();
    }

    /**
     * When strict {@code columnContains} matches nothing (e.g. village footing / structure trim), still pick the nearest
     * <strong>named</strong> waystone in a generous horizontal disc around the procedural center. Same dimension only.
     */
    private static Optional<String> scanRelaxedHorizontal(
            ServerLevel level,
            FloatingIslandLayout.IslandParams params,
            Reflection ref,
            List<?> waystones,
            int regionX,
            int regionZ) {
        double cx = params.centerX + 0.5d;
        double cz = params.centerZ + 0.5d;
        /*
         * Keep the search within this island's horizontal scale — the old cap (~400 blocks) let the same village waystone
         * win as "nearest" for many nearby procedural islands after activation, duplicating one name on every pin.
         */
        int maxHoriz = Mth.clamp(params.hr + 72, 72, 160);
        double maxD2 = (double) maxHoriz * (double) maxHoriz;

        double bestDist = Double.POSITIVE_INFINITY;
        String bestName = null;
        int relaxedCandidates = 0;
        for (Object w : waystones) {
            try {
                Object dimKey = ref.waystoneGetDimension.invoke(w);
                if (!dimensionMatches(level, dimKey)) {
                    continue;
                }
                BlockPos pos = (BlockPos) ref.waystoneGetPos.invoke(w);
                if (pos == null) {
                    continue;
                }
                int wx = pos.getX();
                int wz = pos.getZ();
                Optional<String> name = ref.resolveDisplayName(level, pos, w);
                if (name.isEmpty()) {
                    continue;
                }
                double dx = wx + 0.5d - cx;
                double dz = wz + 0.5d - cz;
                double d2 = dx * dx + dz * dz;
                if (d2 > maxD2) {
                    continue;
                }
                relaxedCandidates++;
                if (d2 < bestDist) {
                    bestDist = d2;
                    bestName = name.get();
                }
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                // skip
            }
        }

        if (bestName != null) {
            if (Config.DEBUG_LOGGING.getAsBoolean()) {
                ProjectIsland.LOGGER.debug(
                        "Waystone HUD title region {},{}: used relaxed horizontal fallback (maxHoriz={} candidates={})",
                        regionX,
                        regionZ,
                        maxHoriz,
                        relaxedCandidates);
            }
            return Optional.of(bestName);
        }

        if (Config.DEBUG_LOGGING.getAsBoolean() && ModList.get().isLoaded("waystones")) {
            ProjectIsland.LOGGER.debug(
                    "Waystone HUD title scan (relaxed) region {},{}: maxHoriz={} no named waystone in disc",
                    regionX,
                    regionZ,
                    maxHoriz);
        }
        return Optional.empty();
    }

    private static Optional<String> scanFromManager(
            ServerLevel level,
            FloatingIslandLayout.IslandParams params,
            int minY,
            int maxY,
            Reflection ref,
            int regionX,
            int regionZ) {
        try {
            Object mgr = ref.managerGet.invoke(null, level.getServer());
            if (mgr == null) {
                return Optional.empty();
            }
            Stream<?> stream = (Stream<?>) ref.managerGetWaystones.invoke(mgr);
            if (stream == null) {
                return Optional.empty();
            }
            List<?> waystones;
            try (Stream<?> s = stream) {
                waystones = s.toList();
            }
            Optional<String> strict = scanStrict(level, params, minY, maxY, ref, waystones, regionX, regionZ);
            if (strict.isPresent()) {
                return strict;
            }
            return scanRelaxedHorizontal(level, params, ref, waystones, regionX, regionZ);
        } catch (ReflectiveOperationException | ClassCastException e) {
            if (Config.DEBUG_LOGGING.getAsBoolean()) {
                ProjectIsland.LOGGER.debug("WaystoneIslandHudTitle scan failed", e);
            }
            return Optional.empty();
        }
    }

    private static volatile Optional<Reflection> cachedReflection = Optional.empty();
    private static volatile boolean reflectionProbeDone;

    private static Optional<Reflection> reflection() {
        if (!reflectionProbeDone) {
            synchronized (WaystoneIslandHudTitle.class) {
                if (!reflectionProbeDone) {
                    if (ModList.get().isLoaded("waystones")) {
                        Optional<Reflection> created = Reflection.tryCreate();
                        cachedReflection = created;
                        if (created.isEmpty()
                                && reflectionInitFailure != null
                                && REFLECTION_FAILURE_LOGGED.compareAndSet(false, true)) {
                            LOGGER.warn(
                                    "Waystones is loaded but Project Island could not bind reflection for island HUD titles (procedural names only). Cause: {}",
                                    reflectionInitFailure.toString());
                        }
                    } else {
                        cachedReflection = Optional.empty();
                    }
                    reflectionProbeDone = true;
                }
            }
        }
        return cachedReflection;
    }

    private record CacheKey(int regionX, int regionZ) {}

    /** Only successful waystone titles are cached — never cache a miss (avoids locking procedural HUD after activation). */
    private record CacheEntry(String title, long validUntilTick) {}

    private static final Map<CacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();

    /**
     * {@link Component#getString()} is sometimes blank on the dedicated server for nested / translatable shapes while
     * chat still renders the same component; mirror Waystones' chat line by flattening literals, siblings, and
     * translatable args, then fall back to {@link Language}.
     */
    private static String waystoneNameToPlain(Component c) {
        if (c == null) {
            return "";
        }
        String direct = c.getString().trim();
        if (!direct.isEmpty()) {
            return direct;
        }
        StringBuilder fromSiblings = new StringBuilder();
        for (Component sib : c.getSiblings()) {
            String p = waystoneNameToPlain(sib);
            if (!p.isEmpty()) {
                if (!fromSiblings.isEmpty()) {
                    fromSiblings.append(' ');
                }
                fromSiblings.append(p);
            }
        }
        if (!fromSiblings.isEmpty()) {
            return fromSiblings.toString().trim();
        }
        if (c.getContents() instanceof PlainTextContents lit) {
            return lit.text().trim();
        }
        if (c.getContents() instanceof TranslatableContents tr) {
            StringBuilder args = new StringBuilder();
            for (Object o : tr.getArgs()) {
                if (o instanceof Component ac) {
                    String p = waystoneNameToPlain(ac);
                    if (!p.isEmpty()) {
                        if (!args.isEmpty()) {
                            args.append(' ');
                        }
                        args.append(p);
                    }
                } else if (o != null) {
                    String p = o.toString().trim();
                    if (!p.isEmpty()) {
                        if (!args.isEmpty()) {
                            args.append(' ');
                        }
                        args.append(p);
                    }
                }
            }
            if (!args.isEmpty()) {
                return args.toString().trim();
            }
            return Language.getInstance().getOrDefault(tr.getKey());
        }
        return "";
    }

    /**
     * @return custom waystone name if Waystones is present and a named waystone exists inside the island volume near
     *     the procedural center; otherwise empty (caller uses {@link net.projectisland.island.FloatingIslandDisplayName}).
     */
    public static Optional<String> resolve(
            ServerLevel level, int regionX, int regionZ, FloatingIslandLayout.IslandParams params, int minY, int maxY) {
        if (!Config.ISLAND_HUD_WAYSTONE_TITLE_WHEN_LOADED.getAsBoolean()) {
            return Optional.empty();
        }
        Optional<Reflection> ref = reflection();
        if (ref.isEmpty()) {
            return Optional.empty();
        }

        long now = level.getGameTime();
        int refresh = Math.max(20, Config.ISLAND_HUD_WAYSTONE_TITLE_CACHE_TICKS.getAsInt());
        CacheKey key = new CacheKey(regionX, regionZ);
        CacheEntry hit = CACHE.get(key);
        if (hit != null && now < hit.validUntilTick()) {
            return Optional.of(hit.title());
        }

        Optional<String> found = scanFromManager(level, params, minY, maxY, ref.get(), regionX, regionZ);
        if (found.isPresent()) {
            CACHE.put(key, new CacheEntry(found.get(), now + refresh));
        } else {
            CACHE.remove(key);
        }
        return found;
    }

    /** Clears cached titles (e.g. after waystone rename / activation). */
    public static void invalidateCache() {
        CACHE.clear();
    }

    /** True if this block entity is a Waystones tile (for interaction-based cache bust). */
    public static boolean isWaystoneBlockEntity(BlockEntity be) {
        Optional<Reflection> ref = reflection();
        return ref.isPresent() && ref.get().isWaystoneBlockEntity(be);
    }

    /** Call after {@link net.minecraft.server.MinecraftServer#stopServer()} or for tests — clears reflection + name cache. */
    public static void resetForTests() {
        synchronized (WaystoneIslandHudTitle.class) {
            reflectionProbeDone = false;
            cachedReflection = Optional.empty();
            reflectionInitFailure = null;
            REFLECTION_FAILURE_LOGGED.set(false);
        }
        CACHE.clear();
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }
}
