package net.projectisland.client;

import java.util.List;

import net.projectisland.network.RopeLinkSyncPayload.RopeLinkSegment;

public final class RopeLinkClientCache {
    private static volatile List<RopeLinkSegment> segments = List.of();

    private RopeLinkClientCache() {}

    public static void replace(List<RopeLinkSegment> next) {
        segments = List.copyOf(next);
    }

    public static List<RopeLinkSegment> segments() {
        return segments;
    }
}
