package cn.mythicland.dreamlib.integration;

import org.bukkit.entity.Player;

/**
 * Optional player balance bridge, implemented without a compile-time Vault dependency.
 */
public interface PlayerBalanceService {

    /**
     * Returns whether a usable economy provider is currently available.
     *
     * @return true when balance reads are supported
     */
    boolean isAvailable();

    /**
     * Reads one player's current balance.
     *
     * @param player player whose balance is requested
     * @return current balance
     */
    double balance(Player player);
}
