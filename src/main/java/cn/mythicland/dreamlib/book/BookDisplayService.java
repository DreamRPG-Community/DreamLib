package cn.mythicland.dreamlib.book;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Displays a written book without changing the player's actual inventory.
 */
public interface BookDisplayService {
    /**
     * Opens a detached written book for one player on the main thread.
     *
     * @param player online recipient
     * @param book   a written book containing the title, author and pages
     * @throws IllegalArgumentException if the item is not a written book
     * @throws IllegalStateException    if the server version or current thread is unsupported
     */
    void open(Player player, ItemStack book);
}
