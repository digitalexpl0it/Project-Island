package net.projectisland.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.projectisland.Config;

/**
 * Pulls vanilla dungeon / trial / mineshaft starts toward procedural island stone (UNDER_BOTTOM or INTERIOR),
 * optionally shifting horizontally to the nearest valid column. Multi-column sampling avoids center-in-void cases.
 */
public final class FloatingIslandRareStructureVerticalSnap {
    private static final ResourceLocation MONSTER_ROOM = ResourceLocation.withDefaultNamespace("monster_room");
    private static final ResourceLocation TRIAL_CHAMBERS = ResourceLocation.withDefaultNamespace("trial_chambers");
    private static final ResourceLocation MINESHAFT = ResourceLocation.withDefaultNamespace("mineshaft");

    private FloatingIslandRareStructureVerticalSnap() {}

    public enum PlacementMode {
        UNDER_BOTTOM,
        INTERIOR;

        public static PlacementMode fromConfig(String raw) {
            if (raw == null) {
                return UNDER_BOTTOM;
            }
            String s = raw.trim().toLowerCase(Locale.ROOT);
            if ("interior".equals(s)) {
                return INTERIOR;
            }
            return UNDER_BOTTOM;
        }
    }

    public static void snapRareStructuresVertically(RegistryAccess registryAccess, ChunkAccess chunk) {
        if (!Config.FLOATING_ISLANDS_SNAP_RARE_STRUCTURES_TO_ISLAND_COLUMN.getAsBoolean()) {
            return;
        }
        int maxAbsDy = Config.FLOATING_ISLANDS_SNAP_RARE_STRUCTURE_MAX_VERTICAL_BLOCKS.getAsInt();
        int maxManhattan = Config.FLOATING_ISLANDS_SNAP_RARE_STRUCTURE_MAX_HORIZONTAL_MANHATTAN_BLOCKS.getAsInt();
        int gridStep = Config.FLOATING_ISLANDS_SNAP_RARE_STRUCTURE_ANCHOR_GRID_STEP_BLOCKS.getAsInt();
        boolean invalidateOnFail = Config.FLOATING_ISLANDS_SNAP_RARE_STRUCTURE_INVALIDATE_ON_FAIL.getAsBoolean();
        PlacementMode mode = PlacementMode.fromConfig(Config.FLOATING_ISLANDS_RARE_STRUCTURE_PLACEMENT_MODE.get());
        double minFit = Config.FLOATING_ISLANDS_RARE_STRUCTURE_INTERIOR_MIN_COLUMN_FIT_FRACTION.getAsDouble();

        var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        for (var entry : new ArrayList<>(chunk.getAllStarts().entrySet())) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            ResourceLocation id = structureRegistry.getKey(structure);
            if (!isSnapTarget(id)) {
                continue;
            }
            BoundingBox bb = start.getBoundingBox();
            int midX = (bb.minX() + bb.maxX()) >> 1;
            int midZ = (bb.minZ() + bb.maxZ()) >> 1;

            AnchorPick anchor = pickAnchorColumn(bb, gridStep, midX, midZ, minY, maxY);
            if (anchor == null) {
                if (invalidateOnFail) {
                    invalidateStart(chunk, structure, start);
                }
                continue;
            }

            int top = FloatingIslandLayout.columnTopY(anchor.x, anchor.z, minY, maxY);
            int bottom = FloatingIslandLayout.columnBottomY(anchor.x, anchor.z, minY, maxY);
            if (top <= minY || bottom >= maxY) {
                if (invalidateOnFail) {
                    invalidateStart(chunk, structure, start);
                }
                continue;
            }

            if (bbIntersectsIslandFootprint(bb, gridStep, minY, maxY)) {
                continue;
            }

            int dx = anchor.x - midX;
            int dz = anchor.z - midZ;
            int manhattan = Math.abs(dx) + Math.abs(dz);
            if (manhattan > maxManhattan) {
                if (invalidateOnFail) {
                    invalidateStart(chunk, structure, start);
                }
                continue;
            }

            Integer dyObj = chooseVerticalShift(mode, bb, top, bottom, anchor.x, anchor.z, minY, maxY, maxAbsDy);
            PlacementMode usedMode = mode;
            if (dyObj == null && mode == PlacementMode.INTERIOR) {
                dyObj = chooseVerticalShift(
                        PlacementMode.UNDER_BOTTOM, bb, top, bottom, anchor.x, anchor.z, minY, maxY, maxAbsDy);
                usedMode = PlacementMode.UNDER_BOTTOM;
            }
            if (dyObj == null) {
                if (invalidateOnFail) {
                    invalidateStart(chunk, structure, start);
                }
                continue;
            }
            int dy = dyObj;

            if (dy == 0 && dx == 0 && dz == 0) {
                continue;
            }

            if (Math.abs(dy) > maxAbsDy) {
                if (invalidateOnFail) {
                    invalidateStart(chunk, structure, start);
                }
                continue;
            }

            if ((dx != 0 || dz != 0 || dy != 0)
                    && !passesFitFraction(bb, dx, dy, dz, minY, maxY, minFit)) {
                if (usedMode == PlacementMode.INTERIOR) {
                    Integer dy2 = chooseVerticalShift(
                            PlacementMode.UNDER_BOTTOM, bb, top, bottom, anchor.x, anchor.z, minY, maxY, maxAbsDy);
                    if (dy2 == null || Math.abs(dy2) > maxAbsDy) {
                        if (invalidateOnFail) {
                            invalidateStart(chunk, structure, start);
                        }
                        continue;
                    }
                    dy = dy2;
                    if ((dx != 0 || dz != 0 || dy != 0)
                            && !passesFitFraction(bb, dx, dy, dz, minY, maxY, minFit)) {
                        if (invalidateOnFail) {
                            invalidateStart(chunk, structure, start);
                        }
                        continue;
                    }
                } else {
                    if (invalidateOnFail) {
                        invalidateStart(chunk, structure, start);
                    }
                    continue;
                }
            }

            for (StructurePiece piece : start.getPieces()) {
                piece.move(dx, dy, dz);
            }
            start.cachedBoundingBox = null;
        }
    }

    private static void invalidateStart(ChunkAccess chunk, Structure structure, StructureStart start) {
        FloatingIslandsChunkGenerator.wipeStructureBlocksInChunk(chunk, start.getBoundingBox());
        chunk.setStartForStructure(structure, StructureStart.INVALID_START);
    }

    /** @return null if this mode cannot produce a vertical shift. */
    private static Integer chooseVerticalShift(
            PlacementMode mode,
            BoundingBox bb,
            int top,
            int bottom,
            int anchorX,
            int anchorZ,
            int minY,
            int maxY,
            int maxAbsDy) {
        if (mode == PlacementMode.UNDER_BOTTOM) {
            int targetMaxY = bottom - 1;
            return targetMaxY - bb.maxY();
        }

        int midStone = (top + bottom) / 2;
        int bbMidY = (bb.minY() + bb.maxY()) >> 1;
        int dy = midStone - bbMidY;
        if (Math.abs(dy) > maxAbsDy) {
            return null;
        }
        BoundingBox shifted = new BoundingBox(
                bb.minX(),
                bb.minY() + dy,
                bb.minZ(),
                bb.maxX(),
                bb.maxY() + dy,
                bb.maxZ());
        if (!verticalRangeMostlyInsideColumn(shifted, anchorX, anchorZ, minY, maxY)) {
            return null;
        }
        return dy;
    }

    /**
     * True if enough samples along the vertical span at ({@code wx},{@code wz}) fall inside {@link
     * FloatingIslandLayout#columnContains}.
     */
    private static boolean verticalRangeMostlyInsideColumn(
            BoundingBox bb, int wx, int wz, int minY, int maxY) {
        double minFraction = Config.FLOATING_ISLANDS_RARE_STRUCTURE_INTERIOR_MIN_COLUMN_FIT_FRACTION.getAsDouble();
        int y0 = bb.minY();
        int y1 = bb.maxY();
        int span = y1 - y0 + 1;
        int step = Math.max(1, span / 8);
        int hits = 0;
        int total = 0;
        for (int y = y0; y <= y1; y += step) {
            total++;
            if (FloatingIslandLayout.columnContains(wx, wz, y, minY, maxY)) {
                hits++;
            }
        }
        if (total == 0) {
            return false;
        }
        return (double) hits / (double) total >= minFraction;
    }

    private static boolean passesFitFraction(
            BoundingBox bb, int dx, int dy, int dz, int minY, int maxY, double minFraction) {
        int x0 = bb.minX() + dx;
        int x1 = bb.maxX() + dx;
        int z0 = bb.minZ() + dz;
        int z1 = bb.maxZ() + dz;
        int y0 = bb.minY() + dy;
        int y1 = bb.maxY() + dy;

        int[] xs = {x0, x1, (x0 + x1) >> 1};
        int[] zs = {z0, z1, (z0 + z1) >> 1};
        int[] ys = {y0, y1, (y0 + y1) >> 1};

        int hits = 0;
        int total = 0;
        for (int x : xs) {
            for (int z : zs) {
                for (int y : ys) {
                    if (y < minY || y >= maxY) {
                        continue;
                    }
                    total++;
                    if (FloatingIslandLayout.columnContains(x, z, y, minY, maxY)) {
                        hits++;
                    }
                }
            }
        }
        if (total == 0) {
            return false;
        }
        return (double) hits / (double) total >= minFraction;
    }

    private record AnchorPick(int x, int z) {}

    /**
     * Valid columns have land; pick the valid sample closest (Manhattan) to {@code (prefX, prefZ)} for deterministic
     * behavior.
     */
    private static AnchorPick pickAnchorColumn(BoundingBox bb, int gridStep, int prefX, int prefZ, int minY, int maxY) {
        Set<long[]> samples = sampleFootprint(bb, gridStep);
        AnchorPick best = null;
        int bestDist = Integer.MAX_VALUE;
        for (long[] p : samples) {
            int x = (int) p[0];
            int z = (int) p[1];
            if (FloatingIslandLayout.columnTopY(x, z, minY, maxY) <= minY) {
                continue;
            }
            int d = Math.abs(x - prefX) + Math.abs(z - prefZ);
            if (d < bestDist || (d == bestDist && best != null && (x < best.x || (x == best.x && z < best.z)))) {
                bestDist = d;
                best = new AnchorPick(x, z);
            }
        }
        return best;
    }

    private static Set<long[]> sampleFootprint(BoundingBox bb, int step) {
        int s = Math.max(2, step);
        Set<long[]> out = new LinkedHashSet<>();
        int x0 = bb.minX();
        int x1 = bb.maxX();
        int z0 = bb.minZ();
        int z1 = bb.maxZ();
        for (int x = x0; x <= x1; x += s) {
            for (int z = z0; z <= z1; z += s) {
                out.add(new long[] {x, z});
            }
        }
        out.add(new long[] {x0, z0});
        out.add(new long[] {x1, z0});
        out.add(new long[] {x0, z1});
        out.add(new long[] {x1, z1});
        out.add(new long[] {(x0 + x1) >> 1, (z0 + z1) >> 1});
        return out;
    }

    /** True if any footprint sample's vertical span overlaps island stone [bottom, top]. */
    private static boolean bbIntersectsIslandFootprint(BoundingBox bb, int gridStep, int minY, int maxY) {
        int lo = bb.minY();
        int hi = bb.maxY();
        for (long[] p : sampleFootprint(bb, gridStep)) {
            int x = (int) p[0];
            int z = (int) p[1];
            int top = FloatingIslandLayout.columnTopY(x, z, minY, maxY);
            if (top <= minY) {
                continue;
            }
            int bottom = FloatingIslandLayout.columnBottomY(x, z, minY, maxY);
            if (hi >= bottom && lo <= top) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSnapTarget(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        return MONSTER_ROOM.equals(id) || TRIAL_CHAMBERS.equals(id) || MINESHAFT.equals(id);
    }
}
