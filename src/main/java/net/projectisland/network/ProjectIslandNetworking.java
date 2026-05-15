package net.projectisland.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.projectisland.ProjectIsland;

public final class ProjectIslandNetworking {
    private ProjectIslandNetworking() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ProjectIslandNetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(ProjectIsland.MOD_ID);
        registrar.playToClient(
                IslandHudSyncPayload.TYPE, IslandHudSyncPayload.STREAM_CODEC, IslandHudSyncPayload::handleOnClient);
        registrar.playToClient(
                RopeLinkSyncPayload.TYPE, RopeLinkSyncPayload.STREAM_CODEC, RopeLinkSyncPayload::handleOnClient);
        registrar.playToClient(
                ActionBarToastPayload.TYPE, ActionBarToastPayload.STREAM_CODEC, ActionBarToastPayload::handleClientbound);
        registrar.playToClient(
                DragonCountdownSyncPayload.TYPE,
                DragonCountdownSyncPayload.STREAM_CODEC,
                DragonCountdownSyncPayload::handleClientbound);
    }
}
