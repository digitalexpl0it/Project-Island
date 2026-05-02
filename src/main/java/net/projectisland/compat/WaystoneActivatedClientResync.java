package net.projectisland.compat;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.projectisland.ProjectIsland;

/**
 * Pushes Waystones’ {@code KnownWaystonesMessage} again after server work queues drain so the client teleport list
 * matches server-side activated data (ordering vs our deferred HUD sync / merge).
 * Reflection only — no compile dependency on Waystones.
 */
public final class WaystoneActivatedClientResync {
    private WaystoneActivatedClientResync() {}

    private static final AtomicReference<Optional<Method>> SEND_ACTIVATED = new AtomicReference<>(Optional.empty());
    private static volatile boolean probeFailed;

    private static Optional<Method> sendActivatedMethod() {
        Optional<Method> cached = SEND_ACTIVATED.get();
        if (cached.isPresent() || probeFailed) {
            return cached;
        }
        synchronized (WaystoneActivatedClientResync.class) {
            if (SEND_ACTIVATED.get().isPresent() || probeFailed) {
                return SEND_ACTIVATED.get();
            }
            try {
                Class<?> cl = Class.forName("net.blay09.mods.waystones.core.WaystoneSyncManager");
                Method m = cl.getMethod("sendActivatedWaystones", Player.class);
                Optional<Method> created = Optional.of(m);
                SEND_ACTIVATED.set(created);
                return created;
            } catch (ReflectiveOperationException | LinkageError e) {
                probeFailed = true;
                ProjectIsland.LOGGER.debug("Project Island: WaystoneSyncManager.sendActivatedWaystones binding failed ({})", e.toString());
                SEND_ACTIVATED.set(Optional.empty());
                return Optional.empty();
            }
        }
    }

    /**
     * Runs {@code WaystoneSyncManager.sendActivatedWaystones} twice via the server task queue so it executes after
     * same-tick Waystones activation and our deferred {@linkplain net.projectisland.island.IslandHudServerSync} work.
     */
    public static void scheduleDeferred(ServerPlayer player) {
        if (!ModList.get().isLoaded("waystones")) {
            return;
        }
        Optional<Method> m = sendActivatedMethod();
        if (m.isEmpty()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID id = player.getUUID();
        server.execute(() -> server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                return;
            }
            try {
                m.get().invoke(null, p);
            } catch (ReflectiveOperationException | LinkageError e) {
                if (!probeFailed) {
                    ProjectIsland.LOGGER.debug("Project Island: sendActivatedWaystones invoke failed", e);
                }
            }
        }));
    }

    /** For unit tests / dev — allow re-probing after classpath changes. */
    public static void resetForTests() {
        synchronized (WaystoneActivatedClientResync.class) {
            SEND_ACTIVATED.set(Optional.empty());
            probeFailed = false;
        }
    }
}
