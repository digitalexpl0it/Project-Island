package net.projectisland.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.projectisland.ClientConfig;
import net.projectisland.network.IslandHudSyncPayload.IslandHudBeacon;

/** World-space billboard: optional faint backing + single-pass title (defaults: white text, no outline, low panel alpha). */
public final class IslandHudWorldBillboard {
    private static final float BILLBOARD_Y_BIAS = 0.07f;
    private static final float PANEL_PAD_BASE = 5f;
    /** Black outline offsets in font pixels (billboard-local, before final pose scale). */
    private static final int[][] OUTLINE_OFFSETS = {
        {-1, 0},
        {1, 0},
        {0, -1},
        {0, 1},
        {-1, -1},
        {1, -1},
        {-1, 1},
        {1, 1}
    };

    private static final int OUTLINE_ARGB = 0xFF000000;
    private static final ResourceLocation WHITE_TEX =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

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

        Vec3 cam = camera.getPosition();
        pose.pushPose();
        pose.translate(b.x() - cam.x, b.y() - cam.y + BILLBOARD_Y_BIAS, b.z() - cam.z);
        Quaternionf rot = camera.rotation();
        pose.mulPose(rot);
        pose.scale(textScale, -textScale, textScale);

        PoseStack.Pose poseEntry = pose.last();
        final float zFill = 0.5f;
        final float zText = 2.0f;

        if (fillA > 0) {
            VertexConsumer quads = buffers.getBuffer(RenderType.entityTranslucent(WHITE_TEX, false));
            fillTranslucentTexturedQuad(quads, poseEntry, -halfW, -halfH, halfW, halfH, zFill, 0, 0, 0, fillA);
            /*
             * Flush the entity-translucent batch before Font draws. Shader packs / Embeddium often leave the entity
             * pipeline bound; interleaving translucent quads with {@link Font#drawInBatch} without flushing can bind the
             * wrong texture for glyphs → solid black “block” letters and a fog-tinted panel (e.g. purple).
             */
            buffers.endBatch(RenderType.entityTranslucent(WHITE_TEX, false));
        }

        Font.DisplayMode mode = seeThroughText ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        pose.pushPose();
        pose.translate(0f, 0f, zText);
        Matrix4f matText = pose.last().pose();
        if (ClientConfig.ISLAND_HUD_WORLD_TEXT_OUTLINE.getAsBoolean()) {
            for (int[] d : OUTLINE_OFFSETS) {
                font.drawInBatch(
                        title,
                        tx + d[0],
                        ty + d[1],
                        OUTLINE_ARGB,
                        false,
                        matText,
                        buffers,
                        mode,
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY);
            }
        }
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

    private static void fillTranslucentTexturedQuad(
            VertexConsumer buffer,
            Pose pose,
            float x0,
            float y0,
            float x1,
            float y1,
            float z,
            int r,
            int g,
            int b,
            int a) {
        float nx = 0f;
        float ny = 0f;
        float nz = 1f;
        vertex(buffer, pose, x0, y1, z, r, g, b, a, 0f, 0f, nx, ny, nz);
        vertex(buffer, pose, x1, y1, z, r, g, b, a, 1f, 0f, nx, ny, nz);
        vertex(buffer, pose, x1, y0, z, r, g, b, a, 1f, 1f, nx, ny, nz);
        vertex(buffer, pose, x0, y0, z, r, g, b, a, 0f, 1f, nx, ny, nz);
    }

    private static void vertex(
            VertexConsumer buffer,
            Pose pose,
            float x,
            float y,
            float z,
            int r,
            int g,
            int b,
            int a,
            float u,
            float v,
            float nx,
            float ny,
            float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
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
