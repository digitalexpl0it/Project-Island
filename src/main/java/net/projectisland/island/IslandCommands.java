package net.projectisland.island;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.projectisland.ProjectIsland;
import net.projectisland.ProjectIslandDimensions;

public final class IslandCommands {
    private IslandCommands() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(IslandCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("projectisland")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("island")
                        .then(Commands.literal("here").executes(ctx -> islandHere(ctx.getSource())))
                        .then(Commands.literal("claim").executes(ctx -> islandClaim(ctx.getSource())))));
    }

    private static int islandHere(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        var level = player.serverLevel();
        var keyOpt = IslandWorld.keyAt(level, player.blockPosition());
        if (keyOpt.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No island region owns this column (void or outside procedural mass)."), false);
            return 1;
        }
        FloatingIslandKey key = keyOpt.get();
        IslandRecord record = IslandWorld.get(level).getOrCreate(key);
        source.sendSuccess(
                () -> Component.literal("Island "
                        + key.toStorageKey()
                        + " — state="
                        + record.state()
                        + (record.owner() != null ? " owner=" + record.owner() : "")),
                false);
        ProjectIsland.LOGGER.debug("projectisland island here: {} {}", key, record.state());
        return 1;
    }

    /**
     * Interim Phase 4: claim the island region under the player's feet when {@link IslandState#AVAILABLE}. Full
     * dock/link rules come later; this is OP-only for testing and admin.
     */
    private static int islandClaim(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        var level = player.serverLevel();
        if (!ProjectIslandDimensions.isFloatingIslandsGameplay(level)) {
            source.sendFailure(Component.literal("Not in the floating-islands overworld."));
            return 0;
        }
        FloatingIslandSavedData data = IslandWorld.get(level);
        var keyOpt = IslandWorld.keyAt(level, player.blockPosition());
        if (keyOpt.isEmpty()) {
            source.sendFailure(Component.literal("No island region owns this column."));
            return 0;
        }
        FloatingIslandKey key = keyOpt.get();
        IslandState state = data.peek(key).map(IslandRecord::state).orElse(IslandState.AVAILABLE);
        if (state != IslandState.AVAILABLE) {
            source.sendFailure(Component.literal("Island is not AVAILABLE (state=" + state + ")."));
            return 0;
        }
        if (data.trySecondaryClaim(key, player.getUUID(), level.getGameTime())) {
            source.sendSuccess(
                    () -> Component.literal("Claimed island " + key.toStorageKey() + " for " + player.getGameProfile().getName()),
                    true);
            ProjectIsland.LOGGER.info("projectisland island claim: {} by {}", key, player.getGameProfile().getName());
            return 1;
        }
        source.sendFailure(Component.literal("Claim failed (race or state changed)."));
        return 0;
    }
}
