package net.projectisland.island;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.projectisland.Config;
import net.projectisland.ProjectIslandDimensions;

/**
 * Server: upgrade existing rope links when the owner's advancement-derived tier increases.
 *
 * <p>We preserve health fraction: if a link is at 40%, upgrading max health keeps it at 40% (rounded).</p>
 */
public final class RopeLinkProgressionUpgrade {
    private RopeLinkProgressionUpgrade() {}

    public static void tick(MinecraftServer server) {
        if (!Config.ROPE_PROGRESSION_UPGRADE_EXISTING_LINKS.getAsBoolean()) {
            return;
        }
        int interval = Math.max(1, Config.ROPE_PROGRESSION_UPGRADE_INTERVAL_TICKS.getAsInt());
        if (server.getTickCount() % interval != 0) {
            return;
        }

        Map<UUID, RopeProgression.RopeTier> tierByPlayer = new HashMap<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            tierByPlayer.put(sp.getUUID(), RopeProgression.tierFor(sp));
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
                continue;
            }
            FloatingIslandSavedData data = IslandWorld.get(level);
            upgradeLinksForLevel(data, tierByPlayer);
        }
    }

    private static void upgradeLinksForLevel(FloatingIslandSavedData data, Map<UUID, RopeProgression.RopeTier> tierByPlayer) {
        double baseLen = Math.max(1.0d, (double) Config.ROPE_LINK_MAX_LENGTH_BLOCKS.getAsInt());
        float baseHp = (float) Math.max(1.0d, Config.ROPE_LINK_MAX_HEALTH.getAsDouble());

        List<RopeLink> links = data.copyRopeLinks();
        for (RopeLink link : links) {
            RopeProgression.RopeTier tier = tierByPlayer.get(link.owner());
            if (tier == null) {
                continue; // offline owner; skip
            }
            double wantLen = baseLen * tier.maxLengthMultiplier;
            float wantMaxHp = (float) (baseHp * tier.maxHealthMultiplier);

            boolean lenUpgrade = wantLen > link.maxLengthBlocks() + 1e-6;
            boolean hpUpgrade = wantMaxHp > link.maxHealth() + 1e-6f;
            if (!lenUpgrade && !hpUpgrade) {
                continue;
            }

            float frac = link.healthFraction();
            float newHp = Math.max(0f, Math.min(wantMaxHp, wantMaxHp * frac));
            double newLen = Math.max(link.maxLengthBlocks(), wantLen);
            float newMax = Math.max(link.maxHealth(), wantMaxHp);

            data.putRopeLink(link.withCaps(newLen, newHp, newMax));
        }
    }
}

