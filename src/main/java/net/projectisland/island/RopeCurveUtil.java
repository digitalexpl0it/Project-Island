package net.projectisland.island;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Single source of truth for rope sag + attachment points: {@link net.projectisland.client.RopeLinkSegmentRenderer} and
 * {@linkplain net.projectisland.client.RopeLinkHealthBarRenderer} delegate here so visuals match
 * {@linkplain net.projectisland.island.RopeSurfingState server surf}.
 */
public final class RopeCurveUtil {
    /**
     * Top of outer loop element (block 0–16 space): same as {@code rope_anchor.json} loop center in X/Z and top Y.
     */
    private static final double ATTACH_X = (4.0 + 12.0) * 0.5 / 16.0;

    private static final double ATTACH_Y = (16.0 - 0.5) / 16.0;
    private static final double ATTACH_Z = (3.0 + 13.0) * 0.5 / 16.0;

    private static final double SAG_REL_TO_SPAN = 0.078;
    private static final double MAX_SAG_BLOCKS = 5.0;

    /** Samples for arc-length integration (does not need to match renderer tube tessellation). */
    private static final int ARC_LENGTH_SAMPLES = 48;

    private RopeCurveUtil() {}

    public static Vec3 attachmentWorld(BlockPos pos) {
        return new Vec3(pos.getX() + ATTACH_X, pos.getY() + ATTACH_Y, pos.getZ() + ATTACH_Z);
    }

    /** Lerp with vertical parabolic slack: endpoints fixed, midpoint drops along -Y. */
    public static Vec3 sagPoint(Vec3 a, Vec3 b, double t) {
        double u = 1.0 - t;
        double chord = a.distanceTo(b);
        double sagAmp = Math.min(MAX_SAG_BLOCKS, chord * SAG_REL_TO_SPAN);
        double sagShape = 4.0 * t * (1.0 - t);
        return new Vec3(u * a.x + t * b.x, u * a.y + t * b.y - sagAmp * sagShape, u * a.z + t * b.z);
    }

    public static Vec3 sagPoint(BlockPos from, BlockPos to, double t) {
        return sagPoint(attachmentWorld(from), attachmentWorld(to), Mth.clamp(t, 0.0, 1.0));
    }

    /** Polyline length along the sag curve from {@code t=0} to {@code t=1}. */
    public static double arcLengthBlocks(BlockPos from, BlockPos to) {
        Vec3 a = attachmentWorld(from);
        Vec3 b = attachmentWorld(to);
        int seg = ARC_LENGTH_SAMPLES;
        double len = 0.0;
        Vec3 prev = sagPoint(a, b, 0.0);
        for (int i = 1; i <= seg; i++) {
            Vec3 next = sagPoint(a, b, i / (double) seg);
            len += prev.distanceTo(next);
            prev = next;
        }
        return len;
    }
}
