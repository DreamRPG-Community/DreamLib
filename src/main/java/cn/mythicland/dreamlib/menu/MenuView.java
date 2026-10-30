package cn.mythicland.dreamlib.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * A player-specific inventory view managed by {@link MenuService}.
 */
public interface MenuView {

    /**
     * Builds the title shown to one player.
     *
     * @param player the viewer
     * @return the inventory title
     */
    @SuppressWarnings("SameReturnValue")
    String title(Player player);

    /**
     * Returns the inventory size for one player.
     *
     * @param player the viewer
     * @return a valid Bukkit inventory size
     */
    @SuppressWarnings("SameReturnValue")
    int size(Player player);

    /**
     * Renders the current state into an already-created inventory.
     *
     * @param player    the viewer
     * @param inventory the inventory to populate
     */
    void render(Player player, Inventory inventory);

    /**
     * Handles a click after the shared service has cancelled the event.
     *
     * @param player      the viewer
     * @param event       the cancelled click event
     * @param menuService the service that owns this view
     */
    void handleClick(Player player, InventoryClickEvent event, MenuService menuService);
}
