package net.projectisland;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_SHOW = BUILDER
            .comment("Draw synced island HUD labels when the world uses Project Island floating-islands generation (server can still send data when disabled).")
            .define("islandHudShow", true);

    public static final ModConfigSpec.DoubleValue ISLAND_HUD_TEXT_SCALE = BUILDER
            .comment("World-space scale for debug-style floating text (vanilla debug labels use 0.02).")
            .defineInRange("islandHudTextScale", 0.056d, 0.024d, 0.11d);

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_SEE_THROUGH_TEXT = BUILDER
            .comment("Use see-through text so labels stay readable when blocks or terrain sit in front of them.")
            .define("islandHudSeeThroughText", true);

    public static final ModConfigSpec.DoubleValue ISLAND_HUD_NIGHT_COLOR_BOOST = BUILDER
            .comment(
                    "Blend label colors toward white when the sky is dark and local light is low (0 = off, 1 = strongest).",
                    "Improves readability at night without changing daytime appearance much.")
            .defineInRange("islandHudNightColorBoost", 0.72d, 0.0d, 1.0d);

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}
}
