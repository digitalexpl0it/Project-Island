package net.projectisland.client;

import java.awt.Color;

import net.minecraft.util.Mth;

/**
 * Stable ARGB per island display name (same name ⇒ same color; no flicker).
 */
public final class IslandHudTitleColors {
    private IslandHudTitleColors() {}

    /** Readable pastel on dark panels: varied hue, constrained saturation / brightness. */
    public static int argbForName(String name) {
        if (name == null || name.isEmpty()) {
            return 0xFFFFFFFF;
        }
        int h = name.hashCode();
        int spread = h ^ (h >>> 16) ^ (h << 7);
        float hue = (spread & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
        float sat = Mth.clamp(0.40f + (spread & 0x1F) / 160f, 0.36f, 0.58f);
        float bri = Mth.clamp(0.94f + ((spread >>> 8) & 7) / 200f, 0.91f, 0.99f);
        int rgb = Color.HSBtoRGB(hue, sat, bri);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Dark outline that stays on-hue for colored titles; near-black for white. */
    public static int outlineArgbForTitle(int titleArgb) {
        int r = (titleArgb >> 16) & 0xFF;
        int g = (titleArgb >> 8) & 0xFF;
        int b = titleArgb & 0xFF;
        float f = 0.18f;
        int rr = Mth.clamp(Mth.floor(r * f), 0, 255);
        int gg = Mth.clamp(Mth.floor(g * f), 0, 255);
        int bb = Mth.clamp(Mth.floor(b * f), 0, 255);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }
}
