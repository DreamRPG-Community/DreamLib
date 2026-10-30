package cn.mythicland.dreamlib.integration;

import org.bukkit.entity.Player;

/**
 * Optional PlayerPoints bridge, implemented without a compile-time PlayerPoints dependency.
 */
public interface PlayerPointsService {

    /**
     * Returns whether PlayerPoints is installed and its public API is usable.
     *
     * @return true when point reads are supported
     */
    boolean isAvailable();

    /**
     * Reads one player's formatted point balance.
     *
     * @param player player whose points are requested
     * @return formatted point balance
     */
    String formattedPoints(Player player);
}
