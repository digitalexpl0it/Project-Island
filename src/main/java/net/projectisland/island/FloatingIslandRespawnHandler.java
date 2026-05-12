package net.projectisland.island;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.projectisland.ProjectIslandDimensions;

/**
 * If vanilla respawn (world spawn, obstructed bed, etc.) would place the player in the void over the floating-islands
 * overworld, move them to their starter island center or the nearest procedural surface instead. Valid bed positions
 * in the overworld are left unchanged.
 */
public final class FloatingIslandRespawnHandler {
    private FloatingIslandRespawnHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FloatingIslandRespawnHandler::onPlayerRespawnPosition);
    }

    private static void onPlayerRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DimensionTransition dt = event.getDimensionTransition();
        ServerLevel target = dt.newLevel();
        if (!target.dimension().equals(Level.OVERWORLD) || !ProjectIslandDimensions.isFloatingIslandsGameplay(target)) {
            return;
        }
        Vec3 pos = dt.pos();
        if (!isUnsafeFloatingSpawn(target, pos)) {
            return;
        }
        FloatingIslandSavedData data = IslandWorld.get(target);
        Optional<Vec3> replacement = resolveSafeRespawnFeet(player, target, data, pos);
        if (replacement.isEmpty()) {
            return;
        }
        Vec3 feet = replacement.get();
        IslandChunkLoader.ensureChunksAroundWorldBlock(target, Mth.floor(feet.x), Mth.floor(feet.z), 3);
        event.setDimensionTransition(
                new DimensionTransition(target, feet, Vec3.ZERO, dt.yRot(), dt.xRot(), dt.postDimensionTransition()));
        FloatingIslandVoidRescue.showVoidRescueActionBar(player);
    }

    /** True if no column in a small neighborhood has standable collision within a few dozen blocks below the feet. */
    static boolean isUnsafeFloatingSpawn(ServerLevel level, Vec3 pos) {
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return false;
        }
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int bx = Mth.floor(pos.x);
        int bz = Mth.floor(pos.z);
        double ey = pos.y;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (FloatingIslandSurfaceSupport.columnSupportsFeet(level, bx + dx, bz + dz, ey, minY, maxY)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Optional<Vec3> resolveSafeRespawnFeet(
            ServerPlayer player, ServerLevel level, FloatingIslandSavedData data, Vec3 originalPos) {
        Optional<FloatingIslandKey> starter = data.getStarterHome(player.getUUID());
        if (starter.isPresent()) {
            Optional<Vec3> atStarter = FloatingIslandStarterPlacement.optionalFeetAtIslandCenter(level, starter.get());
            if (atStarter.isPresent()) {
                return atStarter;
            }
        }
        return FloatingIslandVoidRescue.findNearestIslandFeet(level, originalPos.x, originalPos.z);
    }
}
