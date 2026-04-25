package net.projectisland.island;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.projectisland.Config;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.content.RopeAnchorBlockEntity;

/**
 * Server: overstretched ropes lose {@linkplain RopeLink#health()}; at 0 the link is removed and anchors restore.
 */
public final class RopeLinkStress {
    private RopeLinkStress() {}

    public static void tick(MinecraftServer server) {
        float dmgBase = (float) Config.ROPE_LINK_STRAIN_DAMAGE_PER_TICK.getAsDouble();
        if (dmgBase <= 0f) {
            return;
        }
        int interval = Math.max(1, Config.ROPE_LINK_STRESS_TICK_INTERVAL.getAsInt());
        if (server.getTickCount() % interval != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
                tickLevel(level, dmgBase);
            }
        }
    }

    private static void tickLevel(ServerLevel level, float dmgBase) {
        FloatingIslandSavedData data = IslandWorld.get(level);
        double threshold = Config.ROPE_LINK_STRAIN_RATIO_THRESHOLD.get();
        double span = Math.max(1e-6, 1.0 - threshold);
        List<RopeLink> links = data.copyRopeLinks();
        for (RopeLink link : links) {
            RopeLink current = data.getRopeLink(link.id()).orElse(null);
            if (current == null) {
                continue;
            }
            double chord = Math.sqrt(current.fromAnchorPos().distSqr(current.toAnchorPos()));
            double maxLen = Math.max(1e-6, current.maxLengthBlocks());
            double ratio = chord / maxLen;
            if (ratio <= threshold) {
                continue;
            }
            float factor = (float) ((ratio - threshold) / span);
            float dmg = dmgBase * factor;
            float nh = current.health() - dmg;
            if (nh <= 0f) {
                RopeAnchorBlockEntity.severLinkFromSavedData(level, current);
            } else {
                data.putRopeLink(current.withHealth(nh));
            }
        }
    }
}
