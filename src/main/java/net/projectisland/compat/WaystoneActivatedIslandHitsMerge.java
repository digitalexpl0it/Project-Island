package net.projectisland.compat;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.island.IslandWorld;

/**
 * Waystones persists <strong>activated</strong> waystones per player independently of Project Island’s
 * {@linkplain net.projectisland.island.FloatingIslandSavedData island hit} map (filled on right-click). After reconnect,
 * our packed visit list could be empty or incomplete while Waystones still knows every stone you’ve used — Xaero gold /
 * persistent pins then mis-sync. This reconciles by merging {@code WaystonesAPI.getActivatedWaystones} into
 * {@linkplain IslandWorld#markWaystoneHitsForHudSync the same keys} we use for HUD + Xaero. Reflection only.
 */
public final class WaystoneActivatedIslandHitsMerge {
    private WaystoneActivatedIslandHitsMerge() {}

    private static final AtomicReference<Optional<Bindings>> BINDINGS = new AtomicReference<>(Optional.empty());
    private static volatile boolean probeFailed;

    private record Bindings(Method getActivatedWaystones, Method waystoneGetDimension, Method waystoneGetPos) {}

    private static Optional<Bindings> bindings() {
        Optional<Bindings> cached = BINDINGS.get();
        if (cached.isPresent() || probeFailed) {
            return cached;
        }
        synchronized (WaystoneActivatedIslandHitsMerge.class) {
            if (BINDINGS.get().isPresent() || probeFailed) {
                return BINDINGS.get();
            }
            try {
                Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
                Method getActivated = api.getMethod("getActivatedWaystones", Player.class);
                Class<?> waystoneCl = Class.forName("net.blay09.mods.waystones.api.Waystone");
                Method getDim = waystoneCl.getMethod("getDimension");
                Method getPos = waystoneCl.getMethod("getPos");
                Optional<Bindings> created = Optional.of(new Bindings(getActivated, getDim, getPos));
                BINDINGS.set(created);
                return created;
            } catch (ReflectiveOperationException | LinkageError e) {
                probeFailed = true;
                ProjectIsland.LOGGER.debug("Project Island: WaystonesAPI merge binding failed ({})", e.toString());
                BINDINGS.set(Optional.empty());
                return Optional.empty();
            }
        }
    }

    /**
     * For each waystone the player has activated in this level’s dimension, apply the same region keys as a physical
     * waystone use (centers + merged-surface variants).
     */
    /**
     * @return {@code true} if any island-hit key was newly written reconciling Waystones’ activated list with ours
     */
    public static boolean tryMergeActivatedWaystones(ServerPlayer player, ServerLevel level) {
        if (!ModList.get().isLoaded("waystones")) {
            return false;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return false;
        }
        Optional<Bindings> refOpt = bindings();
        if (refOpt.isEmpty()) {
            return false;
        }
        Bindings ref = refOpt.get();
        try {
            Collection<?> activated = (Collection<?>) ref.getActivatedWaystones.invoke(null, player);
            if (activated == null || activated.isEmpty()) {
                return false;
            }
            boolean anyNew = false;
            for (Object w : activated) {
                if (w == null) {
                    continue;
                }
                Object dimKey = ref.waystoneGetDimension.invoke(w);
                if (!dimensionMatches(level, dimKey)) {
                    continue;
                }
                BlockPos pos = (BlockPos) ref.waystoneGetPos.invoke(w);
                if (pos == null) {
                    continue;
                }
                if (IslandWorld.markWaystoneHitsForHudSync(level, player.getUUID(), pos, pos)) {
                    anyNew = true;
                }
            }
            return anyNew;
        } catch (ReflectiveOperationException | ClassCastException e) {
            if (!probeFailed) {
                ProjectIsland.LOGGER.debug("Project Island: Waystones activated merge failed", e);
            }
            return false;
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

    /** For unit tests / dev — allow re-probing after classpath changes. */
    public static void resetForTests() {
        synchronized (WaystoneActivatedIslandHitsMerge.class) {
            BINDINGS.set(Optional.empty());
            probeFailed = false;
        }
    }
}
