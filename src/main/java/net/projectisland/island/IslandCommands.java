package net.projectisland.island;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.projectisland.Config;
import net.projectisland.ProjectIsland;

public final class IslandCommands {
    private IslandCommands() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(IslandCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("projectisland")
                .then(Commands.literal("island")
                        .then(Commands.literal("here")
                                .requires(IslandCommands::isServerPlayerSource)
                                .executes(ctx -> islandHere(ctx.getSource())))
                        .then(Commands.literal("claim")
                                .requires(IslandCommands::isServerPlayerSourceWithClaimPermission)
                                .executes(ctx -> islandClaim(ctx.getSource())))));
    }

    private static boolean isServerPlayerSource(CommandSourceStack s) {
        return s.getEntity() instanceof ServerPlayer;
    }

    private static boolean isServerPlayerSourceWithClaimPermission(CommandSourceStack s) {
        return s.getEntity() instanceof ServerPlayer
                && s.hasPermission(Config.SECONDARY_CLAIM_COMMAND_PERMISSION_LEVEL.getAsInt());
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
     * Claim the island region under the player's feet when {@link IslandState#AVAILABLE}. Permission:
     * {@link Config#SECONDARY_CLAIM_COMMAND_PERMISSION_LEVEL}. Rope rules: {@link IslandSecondaryClaim}.
     */
    private static int islandClaim(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        var level = player.serverLevel();
        IslandSecondaryClaim.Outcome outcome = IslandSecondaryClaim.tryAtFeet(player, level);
        if (outcome == IslandSecondaryClaim.Outcome.SUCCESS) {
            source.sendSuccess(() -> IslandSecondaryClaim.message(outcome), true);
            IslandWorld.keyAt(level, player.blockPosition())
                    .ifPresent(
                            key -> ProjectIsland.LOGGER.info(
                                    "projectisland island claim: {} by {}",
                                    key,
                                    player.getGameProfile().getName()));
            return 1;
        }
        source.sendFailure(IslandSecondaryClaim.message(outcome));
        return 0;
    }
}
