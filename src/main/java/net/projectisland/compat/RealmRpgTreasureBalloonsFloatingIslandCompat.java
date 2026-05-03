package net.projectisland.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.projectisland.Config;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.worldgen.FloatingIslandsChunkGenerator;

/**
 * <a href="https://modrinth.com/mod/realm-rpg-treasure-balloons">Realm RPG: Treasure Balloons</a> spawns sky
 * balloons using vanilla-style heights; on void floating islands many columns have no ground, so balloons can
 * appear too low or in empty space. Clamp new spawns to sit above the procedural island surface at their X/Z.
 * If the spawn column is void (common when the mod picks overworld-like coordinates), search nearby columns for land
 * before discarding the join.
 */
public final class RealmRpgTreasureBalloonsFloatingIslandCompat {
    private static final String REALM_RPG_BALLOONS_NAMESPACE = "realmrpg_balloons";
    /** How far to search horizontally for an island top when the balloon’s spawn column is void. */
    private static final int VOID_RELOCATE_RADIUS_BLOCKS = 112;
    /** Random samples within the square around the spawn point before giving up. */
    private static final int VOID_RELOCATE_MAX_TRIES = 40;

    private RealmRpgTreasureBalloonsFloatingIslandCompat() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RealmRpgTreasureBalloonsFloatingIslandCompat::onEntityJoin);
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) {
            return;
        }
        if (!Config.FLOATING_ISLANDS_REALM_RPG_BALLOONS_SPAWN_FIX_ENABLED.getAsBoolean()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        Entity entity = event.getEntity();
        if (!BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals(REALM_RPG_BALLOONS_NAMESPACE)) {
            return;
        }
        FloatingIslandsChunkGenerator gen = ProjectIslandDimensions.floatingIslandsChunkGenerator(level).orElse(null);
        if (gen == null) {
            return;
        }
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int wx = Mth.floor(entity.getX());
        int wz = Mth.floor(entity.getZ());
        int surface = FloatingIslandsChunkGenerator.islandSurfaceBlockY(gen, wx, wz, minY, maxY);
        if (surface == Integer.MIN_VALUE) {
            int[] found = findNearbyIslandColumn(level, gen, minY, maxY, wx, wz, level.getRandom());
            if (found == null) {
                event.setCanceled(true);
                return;
            }
            wx = found[0];
            wz = found[1];
            surface = found[2];
            entity.setPos(wx + 0.5d, entity.getY(), wz + 0.5d);
        }
        int clearance = Config.FLOATING_ISLANDS_REALM_RPG_BALLOONS_MIN_BLOCKS_ABOVE_SURFACE.getAsInt();
        double minOkY = surface + clearance + 0.01d;
        if (entity.getY() < minOkY) {
            entity.setPos(entity.getX(), minOkY, entity.getZ());
        }
    }

    /**
     * @return {@code [wx, wz, surfaceY]} for a column with an island, or {@code null}
     */
    private static int[] findNearbyIslandColumn(
            ServerLevel level,
            FloatingIslandsChunkGenerator gen,
            int minY,
            int maxY,
            int centerX,
            int centerZ,
            RandomSource rnd) {
        int r = VOID_RELOCATE_RADIUS_BLOCKS;
        for (int t = 0; t < VOID_RELOCATE_MAX_TRIES; t++) {
            int rx = centerX + rnd.nextInt(r * 2 + 1) - r;
            int rz = centerZ + rnd.nextInt(r * 2 + 1) - r;
            if (!level.hasChunk(rx >> 4, rz >> 4)) {
                continue;
            }
            int top = FloatingIslandsChunkGenerator.islandSurfaceBlockY(gen, rx, rz, minY, maxY);
            if (top != Integer.MIN_VALUE) {
                return new int[] {rx, rz, top};
            }
        }
        return null;
    }
}
