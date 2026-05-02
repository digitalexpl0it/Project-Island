package net.projectisland;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_SHOW = BUILDER
            .comment("Draw synced island HUD labels when the world uses Project Island floating-islands generation (server can still send data when disabled).")
            .define("islandHudShow", true);

    public static final ModConfigSpec.DoubleValue ISLAND_HUD_TEXT_SCALE = BUILDER
            .comment("World-space scale for island name labels (vanilla debug labels use ~0.02).")
            .defineInRange("islandHudTextScale", 0.062d, 0.024d, 0.11d);

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_SEE_THROUGH_TEXT = BUILDER
            .comment("Use see-through text so labels stay readable when blocks or terrain sit in front of them.")
            .define("islandHudSeeThroughText", true);

    public static final ModConfigSpec.DoubleValue ISLAND_HUD_NIGHT_COLOR_BOOST = BUILDER
            .comment(
                    "Blend label colors toward white when the sky is dark and local light is low (0 = off, 1 = strongest).",
                    "Improves readability at night without changing daytime appearance much.")
            .defineInRange("islandHudNightColorBoost", 0.72d, 0.0d, 1.0d);

    public static final ModConfigSpec.DoubleValue ISLAND_HUD_PANEL_FILL_OPACITY = BUILDER
            .comment(
                    "Alpha for the **light** translucent panel behind the island name (0 = text only, 1 = opaque).",
                    "Tune on bright snow/sky; default is a soft wash.")
            .defineInRange("islandHudPanelFillOpacity", 100.0d / 255.0d, 0.0d, 1.0d);

    public static final ModConfigSpec.ConfigValue<String> ISLAND_HUD_TITLE_COLOR_MODE = BUILDER
            .comment(
                    "**white** — always white title.",
                    "**island_hue** — stable pastel per island name (hash); easier to spot names at a glance.")
            .define(
                    "islandHudTitleColorMode",
                    "island_hue",
                    o -> o instanceof String s && ("white".equals(s) || "island_hue".equals(s)));

    public static final ModConfigSpec.DoubleValue ISLAND_HUD_PANEL_SCALE = BUILDER
            .comment(
                    "Scales max title width before ellipsis (**220 × this**, GUI px); text size is **`islandHudTextScale`**.")
            .defineInRange("islandHudPanelScale", 1.0d, 0.35d, 2.5d);

    public static final ModConfigSpec.BooleanValue ROPE_LINKS_SHOW = BUILDER
            .comment("Draw synced rope segments between linked anchors in the floating-islands overworld (server still sends data when disabled).")
            .define("ropeLinksShow", true);

    public static final ModConfigSpec.BooleanValue ROPE_LINK_HEALTH_BARS_SHOW = BUILDER
            .comment(
                    "Draw small billboard health bars at each rope anchor when rope rendering is enabled (uses synced health fraction).")
            .define("ropeLinkHealthBarsShow", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}
}
