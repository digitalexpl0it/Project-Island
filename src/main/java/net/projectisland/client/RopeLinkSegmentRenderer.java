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
import net.minecraft.world.phys.Vec3;
import net.projectisland.network.RopeLinkSyncPayload.RopeLinkSegment;

/**
 * Island links: attaches at the anchor loop, samples a smooth **parabolic sag** (vertical slack), tessellates the span,
 * and draws a **square tube** (four textured faces) using vanilla {@code minecraft:textures/block/chain.png}.
 * Each face uses an **explicit outward normal** (not a quad cross product) and {@link LightTexture#FULL_BRIGHT} so no
 * side reads as a black “missing texture” strip under diffuse lighting.
 */
public final class RopeLinkSegmentRenderer {
    private static final ResourceLocation CHAIN_TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/chain.png");

    /**
     * Top of outer loop element (block 0–16 space): just inside the top voxel so the chain leaves the hook visibly
     * above the cyan face center — see {@code rope_anchor.json} second element {@code to: [12, 16, 13]}.
     */
    private static final double ATTACH_X = (4.0 + 12.0) * 0.5 / 16.0;

    private static final double ATTACH_Y = (16.0 - 0.5) / 16.0;
    private static final double ATTACH_Z = (3.0 + 13.0) * 0.5 / 16.0;

    /** Half-size of the square cross-section (smaller = slimmer chain, less “plank” face area). */
    private static final float TUBE_HALF = 0.095f;

    /** Polyline samples along the sag curve (more = smoother curve + smaller UV steps per segment). */
    private static final int CURVE_SEGMENTS = 32;

    /** Max sag at mid-span as a fraction of chord length (then clamped). */
    private static final double SAG_REL_TO_SPAN = 0.078;

    /** Hard cap so very long spans do not dip into absurd depths. */
    private static final double MAX_SAG_BLOCKS = 5.0;

    /** How many vertical chain repeats in UV space per block of arc length (higher = smaller links). */
    private static final float CHAIN_V_REPEATS_PER_BLOCK = 10.0f;

    /** Horizontal slice of the chain texture (narrower = less wide dark bands from the atlas crop). */
    private static final float U_CENTER_HALF_WIDTH = 0.19f;

    private RopeLinkSegmentRenderer() {}

    public static void render(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        if (RopeLinkClientCache.segments().isEmpty()) {
            return;
        }
        if (!mc.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        VertexConsumer quads = buffers.getBuffer(RenderType.entityCutoutNoCullZOffset(CHAIN_TEXTURE));

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Pose pose = poseStack.last();

        for (RopeLinkSegment seg : RopeLinkClientCache.segments()) {
            Vec3 a = attachmentWorld(BlockPos.of(seg.fromPacked()));
            Vec3 b = attachmentWorld(BlockPos.of(seg.toPacked()));
            renderSquareTubeWithSag(quads, pose, a, b);
        }

        poseStack.popPose();
    }

    public static Vec3 attachmentWorld(BlockPos pos) {
        return new Vec3(pos.getX() + ATTACH_X, pos.getY() + ATTACH_Y, pos.getZ() + ATTACH_Z);
    }

    /** Lerp with vertical parabolic slack: endpoints fixed, midpoint drops along -Y. */
    private static Vec3 sagPoint(Vec3 a, Vec3 b, double t) {
        double u = 1.0 - t;
        double chord = a.distanceTo(b);
        double sagAmp = Math.min(MAX_SAG_BLOCKS, chord * SAG_REL_TO_SPAN);
        double sagShape = 4.0 * t * (1.0 - t);
        return new Vec3(u * a.x + t * b.x, u * a.y + t * b.y - sagAmp * sagShape, u * a.z + t * b.z);
    }

    private static void renderSquareTubeWithSag(VertexConsumer quads, Pose pose, Vec3 aWorld, Vec3 bWorld) {
        int seg = CURVE_SEGMENTS;
        Vec3[] p = new Vec3[seg + 1];
        for (int i = 0; i <= seg; i++) {
            p[i] = sagPoint(aWorld, bWorld, i / (double) seg);
        }
        double[] cum = new double[seg + 1];
        cum[0] = 0.0;
        for (int i = 0; i < seg; i++) {
            cum[i + 1] = cum[i] + p[i].distanceTo(p[i + 1]);
        }
        float uMin = 0.5f - U_CENTER_HALF_WIDTH;
        float uMax = 0.5f + U_CENTER_HALF_WIDTH;
        double w = TUBE_HALF;
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);

        for (int i = 0; i < seg; i++) {
            Vec3 p0 = p[i];
            Vec3 p1 = p[i + 1];
            Vec3 forward = p1.subtract(p0);
            double segLen = forward.length();
            if (segLen < 1e-8) {
                continue;
            }
            forward = forward.scale(1.0 / segLen);
            Vec3 side1 = forward.cross(worldUp);
            if (side1.lengthSqr() < 1e-10) {
                side1 = forward.cross(new Vec3(1.0, 0.0, 0.0));
            }
            side1 = side1.normalize();
            Vec3 side2 = forward.cross(side1);
            if (side2.lengthSqr() < 1e-10) {
                continue;
            }
            side2 = side2.normalize();

            Vec3 c0 = side1.scale(w).add(side2.scale(w));
            Vec3 c1 = side1.scale(w).subtract(side2.scale(w));
            Vec3 c2 = side1.scale(-w).subtract(side2.scale(w));
            Vec3 c3 = side1.scale(-w).add(side2.scale(w));

            float v0 = (float) (cum[i] * CHAIN_V_REPEATS_PER_BLOCK);
            float v1 = (float) (cum[i + 1] * CHAIN_V_REPEATS_PER_BLOCK);

            // Each face must keep a consistent (A→B) vertex mapping, otherwise the chain UVs “tear” and look detached.
            // Corners are in side1/side2 space: c0(+,+), c1(+,-), c2(-,-), c3(-,+).
            // +side1 face (normal +side1)
            texturedQuad(quads, pose, p0.add(c0), p0.add(c1), p1.add(c1), p1.add(c0), uMin, uMax, v0, v1, side1);
            // -side1 face (normal -side1)
            texturedQuad(
                    quads,
                    pose,
                    p0.add(c3),
                    p0.add(c2),
                    p1.add(c2),
                    p1.add(c3),
                    uMin,
                    uMax,
                    v0,
                    v1,
                    side1.scale(-1.0));
            // +side2 face (normal +side2)
            texturedQuad(quads, pose, p0.add(c0), p0.add(c3), p1.add(c3), p1.add(c0), uMin, uMax, v0, v1, side2);
            // -side2 face (normal -side2)
            texturedQuad(
                    quads,
                    pose,
                    p0.add(c2),
                    p0.add(c1),
                    p1.add(c1),
                    p1.add(c2),
                    uMin,
                    uMax,
                    v0,
                    v1,
                    side2.scale(-1.0));
        }
    }

    /** Quad aL → aR → bR → bL; u across width, v along chain; {@code outward} is the face normal (tube exterior). */
    private static void texturedQuad(
            VertexConsumer c,
            Pose pose,
            Vec3 aL,
            Vec3 aR,
            Vec3 bR,
            Vec3 bL,
            float uMin,
            float uMax,
            float v0,
            float v1,
            Vec3 outward) {
        double len = outward.length();
        if (len < 1e-10) {
            return;
        }
        float nx = (float) (outward.x / len);
        float ny = (float) (outward.y / len);
        float nz = (float) (outward.z / len);
        vertex(c, pose, aL, uMin, v0, nx, ny, nz);
        vertex(c, pose, aR, uMax, v0, nx, ny, nz);
        vertex(c, pose, bR, uMax, v1, nx, ny, nz);
        vertex(c, pose, bL, uMin, v1, nx, ny, nz);
    }

    private static void vertex(VertexConsumer c, Pose pose, Vec3 p, float u, float v, float nx, float ny, float nz) {
        c.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
