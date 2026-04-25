package net.projectisland.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.projectisland.ProjectIsland;
import net.projectisland.network.ActionBarToastPayload;

@EventBusSubscriber(modid = ProjectIsland.MOD_ID, value = Dist.CLIENT)
public final class ActionBarToastOverlay {
    /** Semi-transparent panel (~31% opacity) so the world stays visible behind the text. */
    private static final int BACKDROP_ARGB = 0x50101010;
    private static final int BORDER_TOP_ARGB = 0x30808080;

    private static int ticksLeft;
    private static String translationKey = "";
    private static List<String> stringArgs = List.of();

    private ActionBarToastOverlay() {}

    public static void show(String key, List<String> args, int visibleTicks) {
        translationKey = key;
        stringArgs = List.copyOf(args);
        ticksLeft = visibleTicks;
    }

    public static void handlePayload(ActionBarToastPayload payload, IPayloadContext context) {
        context.enqueueWork(
                () -> show(payload.translationKey(), payload.stringArgs(), payload.visibleTicks()));
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (ticksLeft <= 0 || translationKey.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null || mc.getWindow() == null) {
            return;
        }
        Object[] args = stringArgs.toArray();
        Component message = Component.translatable(translationKey, args);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int maxW = Math.max(40, (int) (sw * 0.88f));
        List<FormattedCharSequence> lines = mc.font.split(message, maxW);
        if (lines.isEmpty()) {
            return;
        }
        int lineH = mc.font.lineHeight;
        int padH = 5;
        int padW = 8;
        int maxLineW = 0;
        for (FormattedCharSequence line : lines) {
            maxLineW = Math.max(maxLineW, mc.font.width(line));
        }
        int boxW = maxLineW + padW * 2;
        int totalH = lines.size() * lineH + padH * 2;
        int cx = sw / 2;
        int yBase = sh - 72;
        int left = cx - boxW / 2;
        int top = yBase - totalH;

        GuiGraphics g = event.getGuiGraphics();
        g.fill(left, top, left + boxW, top + totalH, BACKDROP_ARGB);
        g.fill(left, top, left + boxW, top + 1, BORDER_TOP_ARGB);

        int y = top + padH;
        for (FormattedCharSequence line : lines) {
            int x = cx - mc.font.width(line) / 2;
            g.drawString(mc.font, line, x, y, 0xFFFFFF, true);
            y += lineH;
        }
    }
}
