package net.projectisland;

import java.util.List;

/**
 * Thread-safe snapshot of reloadable {@linkplain FloatingIslandPackSpawnReloader pack spawn rules}.
 */
public final class FloatingIslandPackSpawnRules {
    private static volatile List<FloatingIslandPackSpawnRule> rules = List.of();

    private FloatingIslandPackSpawnRules() {}

    public static List<FloatingIslandPackSpawnRule> rules() {
        return rules;
    }

    public static void replace(List<FloatingIslandPackSpawnRule> next) {
        rules = List.copyOf(next);
    }
}
