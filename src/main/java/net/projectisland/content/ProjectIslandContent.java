package net.projectisland.content;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.projectisland.ProjectIsland;

public final class ProjectIslandContent {
    public static final ResourceLocation ROPE_ANCHOR_ID = ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "rope_anchor");
    public static final ResourceLocation HARPOON_GUN_ID = ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "harpoon_gun");
    public static final ResourceLocation ROPE_ANCHOR_BE_ID =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "rope_anchor");

    public static Block ROPE_ANCHOR;
    public static Item ROPE_ANCHOR_ITEM;
    public static Item HARPOON_GUN;
    public static BlockEntityType<RopeAnchorBlockEntity> ROPE_ANCHOR_BE;

    private ProjectIslandContent() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ProjectIslandContent::registerContent);
        modEventBus.addListener(ProjectIslandContent::addCreativeTabEntries);
    }

    private static void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> tabKey = event.getTabKey();
        if (tabKey.equals(CreativeModeTabs.BUILDING_BLOCKS)) {
            event.accept(new ItemStack(ROPE_ANCHOR_ITEM), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        if (tabKey.equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(new ItemStack(HARPOON_GUN), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private static void registerContent(RegisterEvent event) {
        if (Objects.equals(event.getRegistryKey(), Registries.BLOCK)) {
            event.register(Registries.BLOCK, ROPE_ANCHOR_ID, () -> {
                ROPE_ANCHOR =
                        new RopeAnchorBlock(
                                BlockBehaviour.Properties.of()
                                        .mapColor(MapColor.METAL)
                                        .strength(2.0f, 6.0f)
                                        .noOcclusion());
                return ROPE_ANCHOR;
            });
        } else if (Objects.equals(event.getRegistryKey(), Registries.ITEM)) {
            event.register(Registries.ITEM, ROPE_ANCHOR_ID, () -> {
                ROPE_ANCHOR_ITEM = new BlockItem(ROPE_ANCHOR, new Item.Properties());
                return ROPE_ANCHOR_ITEM;
            });
            event.register(Registries.ITEM, HARPOON_GUN_ID, () -> {
                HARPOON_GUN = new HarpoonGunItem(new Item.Properties().stacksTo(1).durability(384));
                return HARPOON_GUN;
            });
        } else if (Objects.equals(event.getRegistryKey(), Registries.BLOCK_ENTITY_TYPE)) {
            event.register(Registries.BLOCK_ENTITY_TYPE, ROPE_ANCHOR_BE_ID, () -> {
                ROPE_ANCHOR_BE = BlockEntityType.Builder.of(RopeAnchorBlockEntity::new, ROPE_ANCHOR).build(null);
                return ROPE_ANCHOR_BE;
            });
        }
    }
}

