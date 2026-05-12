package net.projectisland.island;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.projectisland.ProjectIslandDimensions;

/**
 * Keeps the one-time starter supply chest from being removed by explosions (e.g. creepers) or survival block breaks.
 * Creative players with {@linkplain Player#getAbilities() instabuild} may still remove it for admin cleanup.
 */
public final class StarterSupplyChestProtection {
    private StarterSupplyChestProtection() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, StarterSupplyChestProtection::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, StarterSupplyChestProtection::onBreakBlock);
    }

    private static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> StarterIslandSupplyChest.isSupplyChestProtectedFromRemoval(level, pos));
    }

    private static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && player.getAbilities().instabuild) {
            return;
        }
        if (StarterIslandSupplyChest.isSupplyChestProtectedFromRemoval(level, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
