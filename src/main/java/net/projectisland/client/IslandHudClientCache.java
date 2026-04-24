package net.projectisland.client;

import java.util.List;

import net.projectisland.network.IslandHudSyncPayload.IslandHudBeacon;

public final class IslandHudClientCache {
    private static volatile List<IslandHudBeacon> beacons = List.of();

    private IslandHudClientCache() {}

    public static void replace(List<IslandHudBeacon> next) {
        beacons = List.copyOf(next);
    }

    public static List<IslandHudBeacon> beacons() {
        return beacons;
    }
}
