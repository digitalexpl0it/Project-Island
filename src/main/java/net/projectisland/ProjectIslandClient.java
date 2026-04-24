package net.projectisland;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack.Position;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@Mod(value = ProjectIsland.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ProjectIsland.MOD_ID, value = Dist.CLIENT)
public final class ProjectIslandClient {
    public ProjectIslandClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
            ProjectIsland.LOGGER.info("Project Island client setup (user={})", Minecraft.getInstance().getUser().getName());
        }
    }

    /**
     * Ships a CC0 resource pack inside the mod JAR (see {@code licenses/UNSHADED_BLOCKS_ATTRIBUTION.txt}).
     * NeoForge {@code addPackFinders} uses {@link net.minecraft.server.packs.PathPackResources}: the pack must be a
     * <strong>directory</strong> under {@code resources/} with {@code pack.mcmeta} at its root (a lone {@code .zip} fails with “Missing metadata”).
     */
    @SubscribeEvent
    static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "resourcepacks/bundled_unshaded_blocks"),
                PackType.CLIENT_RESOURCES,
                Component.translatable("resourcePack.projectisland.unshaded_blocks.title"),
                PackSource.BUILT_IN,
                false,
                Position.TOP);
    }
}
