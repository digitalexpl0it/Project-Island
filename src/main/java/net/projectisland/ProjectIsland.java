package net.projectisland;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.projectisland.content.ProjectIslandContent;
import net.projectisland.worldgen.FloatingIslandLayoutSeed;
import net.projectisland.island.FloatingIslandDisplayNameReloader;
import net.projectisland.island.FloatingIslandRespawnHandler;
import net.projectisland.island.IslandCommands;
import net.projectisland.island.IslandHudServerSync;
import net.projectisland.island.RopeAnchorMining;
import net.projectisland.island.RopeLinkServerSync;
import net.projectisland.island.RopeTraversalEvents;
import net.projectisland.island.StarterSupplyChestProtection;
import net.projectisland.network.ProjectIslandNetworking;
import net.projectisland.compat.IslandBiomeModDiagnostics;
import net.projectisland.compat.RealmRpgTreasureBalloonsFloatingIslandCompat;
import net.projectisland.worldgen.FloatingIslandsSpawnPregen;
import net.projectisland.worldgen.ProjectIslandWorldgen;

@Mod(ProjectIsland.MOD_ID)
public final class ProjectIsland {
    public static final String MOD_ID = "projectisland";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProjectIsland(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onCommonSetup);
        ProjectIslandWorldgen.register(modEventBus);
        ProjectIslandNetworking.register(modEventBus);
        ProjectIslandContent.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
        FloatingIslandsSpawnEvents.register();
        FloatingIslandsSpawnTuning.register();
        FloatingIslandsDaytimeCreatureSpawnBoost.register();
        FloatingIslandsPackSpawnBoost.register();
        FloatingIslandsSpawnPregen.register();
        FloatingIslandLayoutSeed.register();
        IslandCommands.register();
        IslandHudServerSync.register();
        RopeLinkServerSync.register();
        RopeAnchorMining.register();
        StarterSupplyChestProtection.register();
        RopeTraversalEvents.register();
        FloatingIslandRespawnHandler.register();
        RealmRpgTreasureBalloonsFloatingIslandCompat.register();
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Project Island common setup");
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
            LOGGER.debug("Project Island debug logging is enabled");
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Project Island dedicated server starting");
        IslandBiomeModDiagnostics.logOnServerStart(event.getServer());
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FloatingIslandDisplayNameReloader());
        event.addListener(new FloatingIslandPackSpawnReloader());
    }
}
