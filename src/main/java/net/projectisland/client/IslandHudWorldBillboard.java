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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.projectisland.ClientConfig;
import net.projectisland.ProjectIsland;
import net.projectisland.network.IslandHudSyncPayload.IslandHudBeacon;

/**
 * Billboards a small panel (translucent fill + border + island icon + text) in world space, matching
 * {@link net.minecraft.client.renderer.debug.DebugRenderer#renderFloatingText} orientation.
 */
public final class IslandHudWorldBillboard {
    private static final ResourceLocation TEX_ISLAND =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "textures/gui/island_hud/floating-island.png");
    private static final ResourceLocation TEX_ISLAND_EX =
            ResourceLocation.fromNamespaceAndPath(ProjectIsland.MOD_ID, "textures/gui/island_hud/floating-island_ex.png");

    /**
     * How long each texture is shown before swapping (exclamation ↔ plain) for available islands.
     * Lower values feel like flicker; 25 ticks ≈ 1.25 s per image at 20 TPS (~2.5 s for a full A→B→A cycle).
     */
    private static final int AVAILABLE_ICON_HOLD_TICKS = 25;

    /** Source PNGs are 64×64; full UV 0–1 is used. Slot size in local billboard units before {@code textScale}. */
    private static final float ICON = 18f;

    private static final float BILLBOARD_Y_BIAS = 0.07f;
    private static final float PANEL_PAD = 4f;
    private static final float TEXT_GAP = 3f;
    private static final float BORDER = 1.25f;
    /** RGB for panel fill when combined with {@link ClientConfig#ISLAND_HUD_PANEL_FILL_OPACITY}. */
    private static final int PANEL_FILL_RGB = 0x00101018;

    private IslandHudWorldBillboard() {}

    public static void render(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource.BufferSource buffers,
            IslandHudBeacon b,
            float textScale,
            boolean seeThroughText,
            int titleArgb,
            int statusArgb,
            int idArgb) {
        Font font = mc.font;
        Level level = mc.level;
        if (level == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }

        String title = ellipsize(font, b.title(), 200);
        String status = b.status();
        String idKey = b.idKey();

        int tw = Mth.ceil(Math.max(Math.max(font.width(title), font.width(status)), font.width(idKey)));
        int lineCount = 2 + (idKey.isEmpty() ? 0 : 1);
        float textBlockH = font.lineHeight * lineCount + TEXT_GAP * (lineCount - 1);
        float innerW = PANEL_PAD + ICON + TEXT_GAP + tw + PANEL_PAD;
        float innerH = PANEL_PAD + Math.max(ICON, textBlockH) + PANEL_PAD;
        float halfW = innerW * 0.5f;
        float halfH = innerH * 0.5f;
        float tx = -halfW + PANEL_PAD + ICON + TEXT_GAP;

        int fillA = alphaByte(ClientConfig.ISLAND_HUD_PANEL_FILL_OPACITY.getAsDouble());
        int panelFillArgb = (fillA << 24) | (PANEL_FILL_RGB & 0x00FFFFFF);
        int borderArgb = withAlpha(borderColor(titleArgb), ClientConfig.ISLAND_HUD_PANEL_BORDER_OPACITY.getAsDouble());

        Vec3 cam = camera.getPosition();
        pose.pushPose();
        pose.translate(b.x() - cam.x, b.y() - cam.y + BILLBOARD_Y_BIAS, b.z() - cam.z);
        Quaternionf rot = camera.rotation();
        pose.mulPose(rot);
        pose.scale(textScale, -textScale, textScale);

        Pose poseEntry = pose.last();
        Matrix4f mat = poseEntry.pose();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        // Separate Z in local billboard units (before uniform scale) so layers don't z-fight when the camera moves.
        final float zBorder = 0f;
        final float zFillIconCol = 1.0f;
        final float zFillTextCol = 1.25f;
        final float zIcon = 2.0f;
        final float zText = 2.6f;

        fillQuad(quads, mat, -halfW - BORDER, -halfH - BORDER, halfW + BORDER, halfH + BORDER, zBorder, borderArgb);
        float innerTop = -halfH + 1f;
        float innerBottom = halfH - 1f;
        float iconColRight = tx - 2f;
        fillQuad(quads, mat, -halfW + 1f, innerTop, iconColRight, innerBottom, zFillIconCol, panelFillArgb);
        fillQuad(quads, mat, iconColRight, innerTop, halfW - 1f, innerBottom, zFillTextCol, panelFillArgb);

        float ix0 = -halfW + PANEL_PAD;
        float iy0 = -halfH + PANEL_PAD;
        float ix1 = ix0 + ICON;
        float iy1 = iy0 + ICON;

        if (b.stateKind() == 2) {
            pose.pushPose();
            pose.translate(ix0, iy0, zIcon);
            float iconScale = ICON / 16f;
            pose.scale(iconScale, iconScale, iconScale);
            mc.getItemRenderer().renderStatic(
                    new ItemStack(Items.REDSTONE_TORCH),
                    ItemDisplayContext.GUI,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    pose,
                    buffers,
                    level,
                    0);
            pose.popPose();
        } else {
            ResourceLocation tex = islandHudTexture(b.stateKind(), level.getGameTime());
            VertexConsumer texConsumer = buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(tex));
            texturedIconQuad(texConsumer, poseEntry, ix0, iy0, ix1, iy1, zIcon);
        }

        Font.DisplayMode mode = seeThroughText ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        pose.pushPose();
        pose.translate(0f, 0f, zText);
        Matrix4f matText = pose.last().pose();
        float ty = -halfH + PANEL_PAD;
        font.drawInBatch(
                title, tx, ty, titleArgb, false, matText, buffers, mode, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        ty += font.lineHeight + TEXT_GAP;
        font.drawInBatch(
                status, tx, ty, statusArgb, false, matText, buffers, mode, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        if (!idKey.isEmpty()) {
            ty += font.lineHeight + TEXT_GAP;
            font.drawInBatch(
                    idKey, tx, ty, idArgb, false, matText, buffers, mode, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();

        pose.popPose();
    }

    private static ResourceLocation islandHudTexture(int stateKind, long gameTime) {
        if (stateKind == 1) {
            return TEX_ISLAND;
        }
        long phase = gameTime / AVAILABLE_ICON_HOLD_TICKS;
        boolean showExclamation = (phase & 1L) == 0L;
        return showExclamation ? TEX_ISLAND_EX : TEX_ISLAND;
    }

    /** Full texture quad (UV 0–1); normals use {@link PoseStack.Pose} so entity shading stays consistent while moving. */
    private static void texturedIconQuad(
            VertexConsumer c, Pose pose, float x0, float y0, float x1, float y1, float z) {
        float u0 = 0f;
        float u1 = 1f;
        float v0 = 0f;
        float v1 = 1f;
        c.addVertex(pose, x0, y1, z)
                .setColor(255, 255, 255, 255)
                .setUv(u0, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0f, 0f, 1f);
        c.addVertex(pose, x1, y1, z)
                .setColor(255, 255, 255, 255)
                .setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0f, 0f, 1f);
        c.addVertex(pose, x1, y0, z)
                .setColor(255, 255, 255, 255)
                .setUv(u1, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0f, 0f, 1f);
        c.addVertex(pose, x0, y0, z)
                .setColor(255, 255, 255, 255)
                .setUv(u0, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0f, 0f, 1f);
    }

    private static int borderColor(int titleRgb) {
        int r = (titleRgb >> 16) & 0xFF;
        int g = (titleRgb >> 8) & 0xFF;
        int b = titleRgb & 0xFF;
        float t = 0.45f;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int argb8888, double opacity01) {
        int a = alphaByte(opacity01);
        return (a << 24) | (argb8888 & 0x00FFFFFF);
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
