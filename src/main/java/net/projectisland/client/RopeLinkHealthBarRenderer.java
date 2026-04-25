package net.projectisland.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.projectisland.network.RopeLinkSyncPayload.RopeLinkSegment;

/**
 * Small billboard health bars at each rope anchor attachment (same anchor point as {@link RopeLinkSegmentRenderer}).
 */
public final class RopeLinkHealthBarRenderer {
    private static final ResourceLocation WHITE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final float BAR_WIDTH = 0.52f;
    private static final float BAR_HEIGHT = 0.085f;
    private static final float LIFT_ABOVE_ATTACH = 0.28f;

    private RopeLinkHealthBarRenderer() {}

    public static void render(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        if (RopeLinkClientCache.segments().isEmpty()) {
            return;
        }
        if (!mc.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        VertexConsumer quads = buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(WHITE));

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Pose pose = poseStack.last();

        for (RopeLinkSegment seg : RopeLinkClientCache.segments()) {
            float frac = Mth.clamp(seg.healthFraction(), 0f, 1f);
            Vec3 a = RopeLinkSegmentRenderer.attachmentWorld(BlockPos.of(seg.fromPacked()))
                    .add(0.0, LIFT_ABOVE_ATTACH, 0.0);
            Vec3 b = RopeLinkSegmentRenderer.attachmentWorld(BlockPos.of(seg.toPacked()))
                    .add(0.0, LIFT_ABOVE_ATTACH, 0.0);
            drawBar(quads, pose, a, cam, frac);
            drawBar(quads, pose, b, cam, frac);
        }

        poseStack.popPose();
    }

    private static void drawBar(VertexConsumer c, Pose pose, Vec3 center, Vec3 cam, float frac) {
        Vec3 toCam = cam.subtract(center);
        double len = toCam.length();
        if (len < 1e-6) {
            return;
        }
        toCam = toCam.scale(1.0 / len);
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
        Vec3 right = toCam.cross(worldUp);
        if (right.lengthSqr() < 1e-10) {
            right = toCam.cross(new Vec3(1.0, 0.0, 0.0));
        }
        right = right.normalize();
        Vec3 up = right.cross(toCam).normalize();

        float hw = BAR_WIDTH * 0.5f;
        float hh = BAR_HEIGHT * 0.5f;
        float nx = (float) toCam.x;
        float ny = (float) toCam.y;
        float nz = (float) toCam.z;

        // Background (dark)
        quad(
                c,
                pose,
                center.subtract(right.scale(hw)).subtract(up.scale(hh)),
                center.add(right.scale(hw)).subtract(up.scale(hh)),
                center.add(right.scale(hw)).add(up.scale(hh)),
                center.subtract(right.scale(hw)).add(up.scale(hh)),
                32,
                32,
                32,
                220,
                nx,
                ny,
                nz);

        float inner = Mth.clamp(frac, 0.04f, 1f);
        float halfInner = hw * inner;
        int g = (int) (255 * frac);
        int r = (int) (255 * (1f - frac));
        Vec3 leftM = center.subtract(right.scale(hw));
        Vec3 rightM = center.add(right.scale(-hw + BAR_WIDTH * inner));
        quad(
                c,
                pose,
                leftM.subtract(up.scale(hh)),
                rightM.subtract(up.scale(hh)),
                rightM.add(up.scale(hh)),
                leftM.add(up.scale(hh)),
                r,
                g,
                24,
                250,
                nx,
                ny,
                nz);
    }

    private static void quad(
            VertexConsumer c,
            Pose pose,
            Vec3 v0,
            Vec3 v1,
            Vec3 v2,
            Vec3 v3,
            int cr,
            int cg,
            int cb,
            int ca,
            float nx,
            float ny,
            float nz) {
        vertex(c, pose, v0, 0f, 0f, cr, cg, cb, ca, nx, ny, nz);
        vertex(c, pose, v1, 1f, 0f, cr, cg, cb, ca, nx, ny, nz);
        vertex(c, pose, v2, 1f, 1f, cr, cg, cb, ca, nx, ny, nz);
        vertex(c, pose, v3, 0f, 1f, cr, cg, cb, ca, nx, ny, nz);
    }

    private static void vertex(
            VertexConsumer c,
            Pose pose,
            Vec3 p,
            float u,
            float v,
            int cr,
            int cg,
            int cb,
            int ca,
            float nx,
            float ny,
            float nz) {
        c.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(cr, cg, cb, ca)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
