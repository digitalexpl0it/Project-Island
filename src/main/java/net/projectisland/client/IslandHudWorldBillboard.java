package net.projectisland.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.projectisland.ClientConfig;
import net.projectisland.network.IslandHudSyncPayload.IslandHudBeacon;

/**
 * World-space billboard: light translucent backing + island name (no outline or drop shadow).
 */
public final class IslandHudWorldBillboard {
    private static final float BILLBOARD_Y_BIAS = 0.07f;
    private static final float PANEL_PAD_BASE = 5f;
    /** Near-white panel tint (readable on snow / bright sky). */
    private static final int PANEL_FILL_RGB = 0xF6F6F8;

    private IslandHudWorldBillboard() {}

    public static void render(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource.BufferSource buffers,
            IslandHudBeacon b,
            float textScale,
            boolean seeThroughText,
            int titleArgb) {
        Font font = mc.font;
        if (mc.level == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }

        float ps = Mth.clamp((float) ClientConfig.ISLAND_HUD_PANEL_SCALE.getAsDouble(), 0.35f, 2.5f);
        float pad = PANEL_PAD_BASE * ps;
        int titleMaxPx = Mth.ceil(220 * ps);

        String title = ellipsize(font, b.title(), titleMaxPx);
        int tw = font.width(title);
        float innerW = pad + tw + pad;
        float innerH = pad + font.lineHeight + pad;
        float halfW = innerW * 0.5f;
        float halfH = innerH * 0.5f;
        float tx = -halfW + pad;
        float ty = -halfH + pad;

        int fillA = alphaByte(ClientConfig.ISLAND_HUD_PANEL_FILL_OPACITY.getAsDouble());
        int panelFillArgb = (fillA << 24) | (PANEL_FILL_RGB & 0x00FFFFFF);

        Vec3 cam = camera.getPosition();
        pose.pushPose();
        pose.translate(b.x() - cam.x, b.y() - cam.y + BILLBOARD_Y_BIAS, b.z() - cam.z);
        Quaternionf rot = camera.rotation();
        pose.mulPose(rot);
        pose.scale(textScale, -textScale, textScale);

        PoseStack.Pose poseEntry = pose.last();
        Matrix4f mat = poseEntry.pose();
        final float zFill = 0.5f;
        final float zText = 2.0f;

        if (fillA > 0) {
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            fillQuad(quads, mat, -halfW, -halfH, halfW, halfH, zFill, panelFillArgb);
        }

        Font.DisplayMode mode = seeThroughText ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        pose.pushPose();
        pose.translate(0f, 0f, zText);
        Matrix4f matText = pose.last().pose();
        font.drawInBatch(
                title,
                tx,
                ty,
                titleArgb,
                false,
                matText,
                buffers,
                mode,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();

        pose.popPose();
    }

    private static int alphaByte(double opacity01) {
        return Mth.clamp(Mth.floor(opacity01 * 255.0 + 0.5), 0, 255);
    }

    private static void fillQuad(VertexConsumer buffer, Matrix4f mat, float x0, float y0, float x1, float y1, float z, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        buffer.addVertex(mat, x0, y1, z).setColor(r, g, b, a);
        buffer.addVertex(mat, x1, y1, z).setColor(r, g, b, a);
        buffer.addVertex(mat, x1, y0, z).setColor(r, g, b, a);
        buffer.addVertex(mat, x0, y0, z).setColor(r, g, b, a);
    }

    private static String ellipsize(Font font, String s, int maxPx) {
        if (font.width(s) <= maxPx) {
            return s;
        }
        String ell = "…";
        String cut = s;
        while (cut.length() > 1 && font.width(cut + ell) > maxPx) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + ell;
    }
}
