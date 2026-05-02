package net.projectisland.worldgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.projectisland.Config;

/**
 * Deterministic per-region rolls shared with {@link IslandRegionControlledSettlementPlacement} so gameplay (starter
 * assignment, island sizing) matches structure placement.
 */
public final class IslandRegionSettlementRoll {
    /** Must match {@link IslandRegionControlledSettlementPlacement} settlement roll salt. */
    public static final int SALT_REGION_SETTLEMENT_ROLL = 991_871;

    public static final int SALT_CONTROLLED_SETTLEMENT_TYPE = 338_011;
    public static final int SALT_CONTROLLED_PLACE_TRY = 551_903;

    /** Matches controlled-settlement type roll + place-try gate (RNG order matches placement). */
    public enum ControlledSettlementSizing {
        NONE,
        VILLAGE,
        OUTPOST
    }

    private IslandRegionSettlementRoll() {}

    public static RandomSource regionRandom(long levelSeed, int rcx, int rcz, int salt) {
        return RandomSource.create(Mth.getSeed(rcx, salt, rcz) ^ levelSeed ^ (levelSeed >>> 32));
    }

    /**
     * Settlement bucket for widening island horizontal radius: same consumes as
     * {@link IslandRegionControlledSettlementPlacement#tryPlaceControlledSettlement} for settlement allow → kind roll
     * → (place try only if village or outpost).
     */
    public static ControlledSettlementSizing controlledSettlementSizing(long levelSeed, int rcx, int rcz) {
        if (!Config.FLOATING_ISLANDS_CONTROLLED_SETTLEMENT_PLACEMENT.getAsBoolean()) {
            return ControlledSettlementSizing.NONE;
        }
        if (!FloatingIslandLayout.regionHasIsland(rcx, rcz)) {
            return ControlledSettlementSizing.NONE;
        }
        RandomSource settleRnd = regionRandom(levelSeed, rcx, rcz, SALT_REGION_SETTLEMENT_ROLL);
        if (!IslandRegionStructurePicker.rollSettlementAllowed(settleRnd)) {
            return ControlledSettlementSizing.NONE;
        }

        RandomSource kindRnd = regionRandom(levelSeed, rcx, rcz, SALT_CONTROLLED_SETTLEMENT_TYPE);
        int wV = Config.CONTROLLED_SETTLEMENT_WEIGHT_VILLAGE.getAsInt();
        int wO = Config.CONTROLLED_SETTLEMENT_WEIGHT_OUTPOST.getAsInt();
        int wN = Config.CONTROLLED_SETTLEMENT_WEIGHT_NONE.getAsInt();
        int total = wV + wO + wN;
        if (total <= 0) {
            return ControlledSettlementSizing.NONE;
        }
        int roll = kindRnd.nextInt(total);
        if (roll >= wV + wO) {
            return ControlledSettlementSizing.NONE;
        }

        double placeChance = Config.CONTROLLED_SETTLEMENT_PLACE_TRY_CHANCE.getAsDouble();
        if (placeChance <= 0.0d) {
            return ControlledSettlementSizing.NONE;
        }
        if (placeChance < 1.0d) {
            RandomSource placeTryRnd = regionRandom(levelSeed, rcx, rcz, SALT_CONTROLLED_PLACE_TRY);
            if (placeTryRnd.nextDouble() > placeChance) {
                return ControlledSettlementSizing.NONE;
            }
        }

        return roll < wV ? ControlledSettlementSizing.VILLAGE : ControlledSettlementSizing.OUTPOST;
    }

    /**
     * {@code true} when controlled settlement placement is enabled and this region’s RNG commits to the pillager
     * outpost weight bucket (including the place-try chance gate), matching {@link IslandRegionControlledSettlementPlacement}
     * before structure resolution.
     */
    public static boolean commitsControlledPillagerSettlement(long levelSeed, int rcx, int rcz) {
        return controlledSettlementSizing(levelSeed, rcx, rcz) == ControlledSettlementSizing.OUTPOST;
    }
}
