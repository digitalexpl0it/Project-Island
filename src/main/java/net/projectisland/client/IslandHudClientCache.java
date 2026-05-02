package net.projectisland.client;

import java.util.List;

import net.projectisland.network.IslandHudSyncPayload.IslandHudBeacon;

public final class IslandHudClientCache {
    private static volatile List<IslandHudBeacon> beacons = List.of();
    private static volatile List<Long> waystoneVisitedRegionKeys = List.of();

    private IslandHudClientCache() {}

    public static void replace(List<IslandHudBeacon> nextBeacons, List<Long> nextVisitedRegionKeys) {
        beacons = List.copyOf(nextBeacons);
        waystoneVisitedRegionKeys = List.copyOf(nextVisitedRegionKeys);
    }

    public static List<IslandHudBeacon> beacons() {
        return beacons;
    }

    /** Packed region keys ({@code islandRegionGridKey}) where the server recorded a waystone use — Xaero gold tint. */
    public static List<Long> waystoneVisitedRegionKeys() {
        return waystoneVisitedRegionKeys;
    }
}
