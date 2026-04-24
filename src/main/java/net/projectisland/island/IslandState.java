package net.projectisland.island;

/**
 * Claim lifecycle for a floating island region. {@link #CONTESTED} is reserved for future siege rules.
 */
public enum IslandState {
    AVAILABLE,
    CLAIMED,
    CONTESTED
}
