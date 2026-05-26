package net.projectisland.worldgen;

import java.util.Optional;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.projectisland.Config;
import net.projectisland.island.FloatingIslandKey;

/**
 * Procedural island grid shared by {@link FloatingIslandsChunkGenerator} and gameplay systems (island identity).
 */
public final class FloatingIslandLayout {
    public static final int REGION_CHUNKS = 8;
    private static final int REGION_SEED_SALT = 84062247;
    public static final double TOP_HORIZ_POWER = 0.72d;
    /** Upper bound on spike-anchor count per island; matches the fixed-size arrays in {@link IslandParams}. */
    public static final int MAX_BOTTOM_SPIKES = 6;

    private FloatingIslandLayout() {}

    public static boolean regionHasIsland(int regionX, int regionZ) {
        RandomSource rnd = RandomSource.create(Mth.getSeed(regionX, REGION_SEED_SALT, regionZ));
        double chance = Config.FLOATING_ISLAND_REGION_SPAWN_CHANCE.getAsDouble();
        return rnd.nextDouble() < chance;
    }

    /**
     * Highest solid surface Y at the island's horizontal center (matches ellipsoid math used for columns).
     * {@code params} must come from {@link #regionIsland(int, int, IslandParams)} for the same region.
     */
    public static int peakSurfaceYAtIslandCenter(IslandParams params) {
        double cyEff = params.centerY + verticalHill(params.centerX, params.centerZ, params.shapeSalt);
        double hTop = horizForTop(0.0d);
        double cap = Math.sqrt(Math.max(0.0d, 1.0d - hTop));
        return Mth.floor(cyEff + params.vrTop * cap);
    }

    public static void regionIsland(int regionX, int regionZ, IslandParams out) {
        RandomSource rnd = RandomSource.create(Mth.getSeed(regionX, REGION_SEED_SALT, regionZ));
        int r = REGION_CHUNKS;
        int margin = Math.max(1, r / 4);
        int span = Math.max(1, r - 2 * margin);
        int baseChunkX = regionX * r + margin + rnd.nextInt(span);
        int baseChunkZ = regionZ * r + margin + rnd.nextInt(span);
        out.centerX = baseChunkX * 16 + rnd.nextInt(16);
        out.centerZ = baseChunkZ * 16 + rnd.nextInt(16);
        out.centerY = 92 + rnd.nextInt(36);
        out.hr = 28 + rnd.nextInt(36) + Config.FLOATING_ISLAND_HORIZONTAL_RADIUS_BONUS.getAsInt();
        Long layoutSeed = FloatingIslandLayoutSeed.getOrNull();
        if (layoutSeed != null) {
            switch (IslandRegionSettlementRoll.controlledSettlementSizing(layoutSeed, regionX, regionZ)) {
                case VILLAGE -> out.hr += Config.FLOATING_ISLAND_HORIZONTAL_RADIUS_VILLAGE_EXTRA_BLOCKS.getAsInt();
                case OUTPOST -> out.hr += Config.FLOATING_ISLAND_HORIZONTAL_RADIUS_OUTPOST_EXTRA_BLOCKS.getAsInt();
                default -> {}
            }
        }
        out.vrTop = 5 + rnd.nextInt(7);
        out.vrBottom = 24 + rnd.nextInt(24);
        out.shapeSalt = rnd.nextLong();

        out.spikeCount = 0;
        if (Config.FLOATING_ISLAND_BOTTOM_SPIKES_ENABLED.getAsBoolean()) {
            int minK = Mth.clamp(Config.FLOATING_ISLAND_BOTTOM_SPIKE_COUNT_MIN.getAsInt(), 0, MAX_BOTTOM_SPIKES);
            int maxK = Mth.clamp(Config.FLOATING_ISLAND_BOTTOM_SPIKE_COUNT_MAX.getAsInt(), 0, MAX_BOTTOM_SPIKES);
            if (maxK < minK) {
                maxK = minK;
            }
            if (maxK > 0) {
                int k = (maxK > minK) ? (minK + rnd.nextInt(maxK - minK + 1)) : minK;
                double maxFrac = Mth.clamp(Config.FLOATING_ISLAND_BOTTOM_SPIKE_MAX_ANCHOR_HORIZ.getAsDouble(), 0.1d, 0.95d);
                double minFrac = Mth.clamp(Config.FLOATING_ISLAND_BOTTOM_SPIKE_MIN_ANCHOR_HORIZ.getAsDouble(), 0.0d, maxFrac);
                double maxR = Math.max(1.0d, out.hr * maxFrac);
                double minR = Math.max(0.0d, out.hr * minFrac);
                double jitterFrac = Mth.clamp(Config.FLOATING_ISLAND_BOTTOM_SPIKE_ANGLE_JITTER_FRACTION.getAsDouble(), 0.0d, 1.0d);
                double slot = (Math.PI * 2.0d) / Math.max(1, k);
                double phase = rnd.nextDouble() * (Math.PI * 2.0d);
                for (int i = 0; i < k; i++) {
                    double slotJitter = (rnd.nextDouble() - 0.5d) * slot * jitterFrac;
                    double angle = phase + i * slot + slotJitter;
                    double rFrac = Math.sqrt(rnd.nextDouble());
                    double radius = minR + (maxR - minR) * rFrac;
                    out.spikeOffsetX[out.spikeCount] = (float) (Math.cos(angle) * radius);
                    out.spikeOffsetZ[out.spikeCount] = (float) (Math.sin(angle) * radius);
                    out.spikeStrength[out.spikeCount] = (float) (0.55d + 0.45d * rnd.nextDouble());
                    out.spikeCount++;
                }
            }
        }
    }

    /**
     * Which island region “owns” the surface at this column: the neighbor cell whose ellipsoid produces the highest top Y.
     * Empty when the column is void.
     */
    public static Optional<FloatingIslandKey> islandOwningSurface(int wx, int wz, int minY, int maxY) {
        int chunkX = Mth.floorDiv(wx, 16);
        int chunkZ = Mth.floorDiv(wz, 16);
        int rcx = Mth.floorDiv(chunkX, REGION_CHUNKS);
        int rcz = Mth.floorDiv(chunkZ, REGION_CHUNKS);

        int best = Integer.MIN_VALUE;
        int bestRx = 0;
        int bestRz = 0;
        IslandParams params = new IslandParams();

        for (int drx = -1; drx <= 1; drx++) {
            for (int drz = -1; drz <= 1; drz++) {
                int rx = rcx + drx;
                int rz = rcz + drz;
                if (!regionHasIsland(rx, rz)) {
                    continue;
                }
                regionIsland(rx, rz, params);

                double dx = (wx + 0.5d) - params.centerX;
                double dz = (wz + 0.5d) - params.centerZ;
                double hrEff = params.hr * hrSmoothScale(wx, wz, params.shapeSalt);
                double horiz = (dx * dx + dz * dz) / (hrEff * hrEff);
                if (horiz >= 1.0d) {
                    continue;
                }

                double hTop = horizForTop(horiz);
                double cyEff = params.centerY + verticalHill(wx, wz, params.shapeSalt);
                double cap = Math.sqrt(Math.max(0.0d, 1.0d - hTop));
                int topY = Mth.floor(cyEff + params.vrTop * cap);
                if (topY > best || (topY == best && (rx < bestRx || (rx == bestRx && rz < bestRz)))) {
                    best = topY;
                    bestRx = rx;
                    bestRz = rz;
                }
            }
        }

        if (best == Integer.MIN_VALUE || best <= minY) {
            return Optional.empty();
        }
        return Optional.of(new FloatingIslandKey(bestRx, bestRz));
    }

    /**
     * Smallest horizontal ellipsoid ratio {@code (dx²+dz²)/hr²} among procedural islands in the 3×3 region
     * neighborhood. Empty when no {@link #regionHasIsland} exists in that neighborhood. Values below {@code 1.0}
     * lie inside an island’s horizontal footprint; values above {@code 1.0} are open void relative to the nearest mass.
     */
    public static Optional<Double> closestProceduralIslandHoriz(int wx, int wz) {
        int chunkX = Mth.floorDiv(wx, 16);
        int chunkZ = Mth.floorDiv(wz, 16);
        int rcx = Mth.floorDiv(chunkX, REGION_CHUNKS);
        int rcz = Mth.floorDiv(chunkZ, REGION_CHUNKS);

        double min = Double.MAX_VALUE;
        IslandParams params = new IslandParams();

        for (int drx = -1; drx <= 1; drx++) {
            for (int drz = -1; drz <= 1; drz++) {
                int rx = rcx + drx;
                int rz = rcz + drz;
                if (!regionHasIsland(rx, rz)) {
                    continue;
                }
                regionIsland(rx, rz, params);

                double dx = (wx + 0.5d) - params.centerX;
                double dz = (wz + 0.5d) - params.centerZ;
                double hrEff = params.hr * hrSmoothScale(wx, wz, params.shapeSalt);
                double horiz = (dx * dx + dz * dz) / (hrEff * hrEff);
                min = Math.min(min, horiz);
            }
        }

        if (min == Double.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(min);
    }

    public static int columnTopY(int wx, int wz, int minY, int maxY) {
        int chunkX = Mth.floorDiv(wx, 16);
        int chunkZ = Mth.floorDiv(wz, 16);
        int rcx = Mth.floorDiv(chunkX, REGION_CHUNKS);
        int rcz = Mth.floorDiv(chunkZ, REGION_CHUNKS);

        int best = Integer.MIN_VALUE;
        IslandParams params = new IslandParams();

        for (int drx = -1; drx <= 1; drx++) {
            for (int drz = -1; drz <= 1; drz++) {
                int rx = rcx + drx;
                int rz = rcz + drz;
                if (!regionHasIsland(rx, rz)) {
                    continue;
                }
                regionIsland(rx, rz, params);

                double dx = (wx + 0.5d) - params.centerX;
                double dz = (wz + 0.5d) - params.centerZ;
                double hrEff = params.hr * hrSmoothScale(wx, wz, params.shapeSalt);
                double horiz = (dx * dx + dz * dz) / (hrEff * hrEff);
                if (horiz >= 1.0d) {
                    continue;
                }

                double hTop = horizForTop(horiz);
                double cyEff = params.centerY + verticalHill(wx, wz, params.shapeSalt);
                double cap = Math.sqrt(Math.max(0.0d, 1.0d - hTop));
                int topY = Mth.floor(cyEff + params.vrTop * cap);
                best = Math.max(best, topY);
            }
        }

        if (best == Integer.MIN_VALUE) {
            return minY;
        }
        return Mth.clamp(best, minY, maxY - 1);
    }

    public static int columnBottomY(int wx, int wz, int minY, int maxY) {
        int chunkX = Mth.floorDiv(wx, 16);
        int chunkZ = Mth.floorDiv(wz, 16);
        int rcx = Mth.floorDiv(chunkX, REGION_CHUNKS);
        int rcz = Mth.floorDiv(chunkZ, REGION_CHUNKS);

        int best = Integer.MAX_VALUE;
        IslandParams params = new IslandParams();

        for (int drx = -1; drx <= 1; drx++) {
            for (int drz = -1; drz <= 1; drz++) {
                int rx = rcx + drx;
                int rz = rcz + drz;
                if (!regionHasIsland(rx, rz)) {
                    continue;
                }
                regionIsland(rx, rz, params);

                double dx = (wx + 0.5d) - params.centerX;
                double dz = (wz + 0.5d) - params.centerZ;
                double hrEff = params.hr * hrSmoothScale(wx, wz, params.shapeSalt);
                double horiz = (dx * dx + dz * dz) / (hrEff * hrEff);
                if (horiz >= 1.0d) {
                    continue;
                }

                double cyEff = params.centerY + verticalHill(wx, wz, params.shapeSalt);
                double denom = effectiveBottomVerticalRadius(horiz, wx, wz, params);
                int bottomY = Mth.floor(cyEff - denom * Math.sqrt(Math.max(0.0d, 1.0d - horiz)));
                best = Math.min(best, bottomY);
            }
        }

        if (best == Integer.MAX_VALUE) {
            return maxY;
        }
        return Mth.clamp(best, minY, maxY - 1);
    }

    public static boolean columnContains(int wx, int wz, int y, int minY, int maxY) {
        int chunkX = Mth.floorDiv(wx, 16);
        int chunkZ = Mth.floorDiv(wz, 16);
        int rcx = Mth.floorDiv(chunkX, REGION_CHUNKS);
        int rcz = Mth.floorDiv(chunkZ, REGION_CHUNKS);

        IslandParams params = new IslandParams();
        for (int drx = -1; drx <= 1; drx++) {
            for (int drz = -1; drz <= 1; drz++) {
                int rx = rcx + drx;
                int rz = rcz + drz;
                if (!regionHasIsland(rx, rz)) {
                    continue;
                }
                regionIsland(rx, rz, params);

                double dx = (wx + 0.5d) - params.centerX;
                double dz = (wz + 0.5d) - params.centerZ;
                double hrEff = params.hr * hrSmoothScale(wx, wz, params.shapeSalt);
                double horiz = (dx * dx + dz * dz) / (hrEff * hrEff);
                if (horiz > 1.0d) {
                    continue;
                }

                double cyEff = params.centerY + verticalHill(wx, wz, params.shapeSalt);
                if (y >= cyEff) {
                    double dy = y - cyEff;
                    double vt = Math.max(1.0d, params.vrTop);
                    double hTop = horizForTop(horiz);
                    if ((dy * dy) / (vt * vt) + hTop <= 1.0d) {
                        return true;
                    }
                } else {
                    double dy = cyEff - y;
                    double denom = Math.max(1.0d, effectiveBottomVerticalRadius(horiz, wx, wz, params));
                    if ((dy * dy) / (denom * denom) + horiz <= 1.0d) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double hrSmoothScale(int wx, int wz, long shapeSalt) {
        double ph = (shapeSalt & 0xffffL) * 0.00015d;
        double s = Math.sin(wx * 0.055d + ph) * Math.cos(wz * 0.048d - ph * 0.7d);
        return 0.86d + 0.14d * s;
    }

    private static double verticalHill(int wx, int wz, long shapeSalt) {
        double ph = (shapeSalt >>> 16 & 0xffffL) * 0.00012d;
        return 2.1d * Math.sin(wx * 0.019d + ph) * Math.cos(wz * 0.017d - ph);
    }

    private static double edgeBottomWarp(double horiz) {
        return 1.0d + 0.72d * horiz * horiz;
    }

    private static double horizForTop(double horiz) {
        return Math.pow(Math.max(0.0d, horiz), TOP_HORIZ_POWER);
    }

    /**
     * Per-column scalar (>= 1) that stretches the bottom ellipsoid into "stalactite roots" near each
     * spike anchor stored on {@code params}. Returns 1.0 when spikes are disabled or absent so the
     * legacy smooth silhouette is preserved. Output is clamped so {@code vrBottom * multiplier} stays
     * within {@code floatingIslandBottomSpikeMaxDepthBelowCenterBlocks}, keeping void rescue safe.
     */
    private static double bottomDepthMultiplier(int wx, int wz, IslandParams p) {
        if (p.spikeCount <= 0) {
            return 1.0d;
        }
        double maxMult = Math.max(1.0d, Config.FLOATING_ISLAND_BOTTOM_SPIKE_MAX_MULTIPLIER.getAsDouble());
        if (maxMult <= 1.0d) {
            return 1.0d;
        }
        double sigma = Math.max(1.0d, Config.FLOATING_ISLAND_BOTTOM_SPIKE_FALLOFF_BLOCKS.getAsDouble());
        double twoSigmaSq = 2.0d * sigma * sigma;
        double sampleX = (wx + 0.5d) - p.centerX;
        double sampleZ = (wz + 0.5d) - p.centerZ;
        double m = 1.0d;
        for (int i = 0; i < p.spikeCount; i++) {
            double dx = sampleX - p.spikeOffsetX[i];
            double dz = sampleZ - p.spikeOffsetZ[i];
            double distSq = dx * dx + dz * dz;
            double factor = Math.exp(-distSq / twoSigmaSq);
            double boost = 1.0d + (maxMult - 1.0d) * p.spikeStrength[i] * factor;
            if (boost > m) {
                m = boost;
            }
        }
        int maxDepth = Math.max(1, Config.FLOATING_ISLAND_BOTTOM_SPIKE_MAX_DEPTH_BELOW_CENTER_BLOCKS.getAsInt());
        double cap = maxDepth / Math.max(1.0d, p.vrBottom);
        if (m > cap) {
            m = cap;
        }
        return m < 1.0d ? 1.0d : m;
    }

    /**
     * Single source of truth for the bottom-ellipsoid vertical radius in both {@link #columnContains}
     * (interior test) and {@link #columnBottomY} (gameplay queries) so worldgen, structures, and void
     * rescue agree on where each column ends.
     */
    private static double effectiveBottomVerticalRadius(double horiz, int wx, int wz, IslandParams p) {
        double base = Math.max(1.0d, p.vrBottom) * edgeBottomWarp(horiz);
        return base * bottomDepthMultiplier(wx, wz, p);
    }

    public static final class IslandParams {
        public int centerX;
        public int centerZ;
        public int centerY;
        public int hr;
        public int vrTop;
        public int vrBottom;
        public long shapeSalt;
        public int spikeCount;
        public final float[] spikeOffsetX = new float[MAX_BOTTOM_SPIKES];
        public final float[] spikeOffsetZ = new float[MAX_BOTTOM_SPIKES];
        public final float[] spikeStrength = new float[MAX_BOTTOM_SPIKES];
    }
}
