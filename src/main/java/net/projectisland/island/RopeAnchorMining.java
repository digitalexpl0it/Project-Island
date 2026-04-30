package net.projectisland.island;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.projectisland.Config;
import net.projectisland.ProjectIslandDimensions;
import net.projectisland.content.ProjectIslandContent;
import net.projectisland.content.RopeAnchorBlockEntity;

/**
 * Survival mining on a <strong>linked</strong> rope anchor damages {@linkplain RopeLink} health on each
 * <strong>blocked</strong> break (when vanilla would have removed the block) instead of removing the anchor in one hit.
 */
public final class RopeAnchorMining {
    private RopeAnchorMining() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RopeAnchorMining::onBreakBlock);
    }

    private static void onBreakBlock(BlockEvent.BreakEvent event) {
        double scale = Config.ROPE_ANCHOR_LINK_DAMAGE_PER_DIG_TICK.getAsDouble();
        if (scale <= 0d) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer sp) || sp.getAbilities().instabuild) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(ProjectIslandContent.ROPE_ANCHOR)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RopeAnchorBlockEntity anchor) || !anchor.hasLink()) {
            return;
        }
        Optional<RopeLink> linkOpt = IslandWorld.get(level).getRopeLink(anchor.getLinkId());
        if (linkOpt.isEmpty()) {
            return;
        }
        RopeLink link = linkOpt.get();
        if (link.health() <= 1e-6f) {
            return;
        }

        float dmg = linkDamageForOneBlockedBreak(sp, level, pos, state, link, scale);
        if (dmg <= 0f) {
            dmg = 0.5f;
        }
        applyDamage(level, anchor, dmg);

        event.setCanceled(true);
        sp.gameMode.handleBlockBreakAction(
                pos,
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                Direction.UP,
                level.getMaxBuildHeight(),
                -1);
    }

    private static float linkDamageForOneBlockedBreak(
            ServerPlayer sp, ServerLevel level, BlockPos pos, BlockState state, RopeLink link, double scale) {
        float perTick = state.getDestroyProgress(sp, level, pos);
        if (perTick <= 0f) {
            perTick = 0.06f;
        }
        float cd = Mth.clamp(sp.getAttackStrengthScale(0.5f), 0.2f, 1f);
        double t = (double) (perTick / 0.1f) * (scale / 0.35d);
        double fracOfMax = Mth.clamp(0.055d * t, 0.035d, 0.14d);
        return (float) (link.maxHealth() * fracOfMax * (0.3d + 0.7d * (double) cd));
    }

    private static void applyDamage(ServerLevel level, RopeAnchorBlockEntity anchor, float damage) {
        UUID lid = anchor.getLinkId();
        if (lid == null) {
            return;
        }
        FloatingIslandSavedData data = IslandWorld.get(level);
        RopeLink link = data.getRopeLink(lid).orElse(null);
        if (link == null) {
            return;
        }
        float nh = link.health() - damage;
        if (nh <= 0f) {
            anchor.handleServerBreak(level, null);
        } else {
            data.putRopeLink(link.withHealth(nh));
        }
        RopeLinkServerSync.sendRopeLinkSyncForLevel(level);
    }
}
