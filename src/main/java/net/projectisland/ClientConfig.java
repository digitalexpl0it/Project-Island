package net.projectisland;

import java.util.Locale;
import java.util.Set;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Xaero {@code WaypointColor} enum constant names (case-insensitive in {@link #isXaeroWaypointColorName}). */
    static final Set<String> XAERO_WAYPOINT_COLOR_NAMES = Set.of(
            "BLACK",
            "DARK_BLUE",
            "DARK_GREEN",
            "DARK_AQUA",
            "DARK_RED",
            "DARK_PURPLE",
            "GOLD",
            "GRAY",
            "DARK_GRAY",
            "BLUE",
            "GREEN",
            "AQUA",
            "RED",
            "PURPLE",
            "YELLOW",
            "WHITE");

    static boolean isXaeroWaypointColorName(Object o) {
        return o instanceof String s && XAERO_WAYPOINT_COLOR_NAMES.contains(s.trim().toUpperCase(Locale.ROOT));
    }

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_SHOW = BUILDER
            .comment("Draw synced island HUD labels when the world uses Project Island floating-islands generation (server can still send data when disabled).")
            .define("islandHudShow", true);

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_WORLD_BILLBOARD_VOID_NAVIGATION = BUILDER
            .comment(
                    "When **false** (default): **world-space** HUD labels draw only when the server sends **exactly one** beacon (you are on an island surface).",
                    "In open void the server sends **many** nearby-island beacons for navigation — skipping labels avoids a cluttered horizon.",
                    "**Xaero** waypoint mirroring (**`islandHudXaeroWaypointSync`**) uses the same rule: multi-beacon sync does not replace **`[Island] `** markers (last on-island waypoint stays) unless this is **true**.",
                    "Set **true** to restore **all** floating labels and **full** Xaero mirrors while between islands.")
            .define("islandHudWorldBillboardVoidNavigation", false);

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
                    "Alpha for the **dark (black)** translucent panel behind the island name (0 = text only, 1 = opaque).",
                    "Tune for readability vs showing the world through the panel.")
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

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_XAERO_WAYPOINT_SYNC = BUILDER
            .comment(
                    "When **Xaero's Minimap** is installed, mirror each server-synced island HUD beacon as a **global waypoint** (name prefix **`[Island] `**).",
                    "Xaero's **World Map** shows the same waypoints when both mods are present. Reflection-based — disable if you prefer not to touch Xaero's waypoint list.")
            .define("islandHudXaeroWaypointSync", true);

    public static final ModConfigSpec.BooleanValue ISLAND_HUD_XAERO_WAYPOINT_TEMPORARY = BUILDER
            .comment(
                    "When **`islandHudXaeroWaypointSync`** is **true**: **true** (default) — **`[Island]`** pins for regions where you have **not** used a **Waystones** block yet (dark gray) are Xaero **temporary** (not saved on world exit).",
                    "After you **use** a waystone on an island, that pin turns **GOLD** and is **always persistent** (saved).",
                    "**false** — unvisited pins are saved too (legacy: waypoint list grows with every island you pass).")
            .define("islandHudXaeroWaypointTemporary", true);

    public static final ModConfigSpec.ConfigValue<String> ISLAND_HUD_XAERO_WAYPOINT_COLOR_DEFAULT = BUILDER
            .comment(
                    "**`[Island] `** Xaero **`WaypointColor`** preset when it is **not** your current island (e.g. **DARK_GRAY**).",
                    "Must match an enum name from Xaero’s minimap — **not** raw RGB (**`Waypoint#setColor(int)`** uses palette indices, not hex).")
            .define("islandHudXaeroWaypointColorDefault", "DARK_GRAY", ClientConfig::isXaeroWaypointColorName);

    public static final ModConfigSpec.ConfigValue<String> ISLAND_HUD_XAERO_WAYPOINT_COLOR_HIT = BUILDER
            .comment(
                    "**`[Island] `** Xaero **`WaypointColor`** when the server lists that island in your **waystone-hit** set (you **used** a Waystones block there), e.g. **GOLD**.")
            .define("islandHudXaeroWaypointColorHit", "GOLD", ClientConfig::isXaeroWaypointColorName);

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
