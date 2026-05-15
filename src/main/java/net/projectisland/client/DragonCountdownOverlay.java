package net.projectisland.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.projectisland.ProjectIsland;

@EventBusSubscriber(modid = ProjectIsland.MOD_ID, value = Dist.CLIENT)
public final class DragonCountdownOverlay {
    private static final int BACKDROP_ARGB = 0x60101010;
    private static final int BORDER_ARGB = 0x40808080;

    private static boolean countdownActive;
    private static long respawnTargetGameTime;

    private DragonCountdownOverlay() {}

    public static void updateFromNetwork(boolean active, long targetGameTime) {
        countdownActive = active;
        respawnTargetGameTime = targetGameTime;
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!countdownActive) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.font == null || mc.getWindow() == null) {
            return;
        }
        long now = mc.level.getGameTime();
        long remainingTicks = respawnTargetGameTime - now;
        if (remainingTicks <= 0L) {
            return;
        }
        int sec = (int) Mth.ceil(remainingTicks / 20.0f);
        sec = Math.max(1, sec);
        int m = sec / 60;
        int s = sec % 60;
        String timeStr = String.format("%d:%02d", m, s);
        Component line = Component.translatable("projectisland.dragon.countdown", timeStr);

        int sw = mc.getWindow().getGuiScaledWidth();
        int maxW = Math.max(40, (int) (sw * 0.9f));
        List<FormattedCharSequence> lines = mc.font.split(line, maxW);
        if (lines.isEmpty()) {
            return;
        }
        int lineH = mc.font.lineHeight;
        int padH = 4;
        int padW = 10;
        int maxLineW = 0;
        for (FormattedCharSequence fr : lines) {
            maxLineW = Math.max(maxLineW, mc.font.width(fr));
        }
        int boxW = maxLineW + padW * 2;
        int totalH = lines.size() * lineH + padH * 2;
        int cx = sw / 2;
        int top = 6;
        int left = cx - boxW / 2;

        GuiGraphics g = event.getGuiGraphics();
        g.fill(left, top, left + boxW, top + totalH, BACKDROP_ARGB);
        g.fill(left, top, left + boxW, top + 1, BORDER_ARGB);

        int y = top + padH;
        for (FormattedCharSequence fr : lines) {
            int x = cx - mc.font.width(fr) / 2;
            g.drawString(mc.font, fr, x, y, 0xFFE0E0, true);
            y += lineH;
        }
    }
}
