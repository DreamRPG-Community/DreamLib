package cn.mythicland.dreamlib.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Optional menu lifecycle contract for views that persist mutable inventory state.
 */
@SuppressWarnings("EmptyMethod")
public interface StatefulMenuView extends MenuView {

    /**
     * Handles a drag after Lib has identified the menu.
     *
     * @param player      menu viewer
     * @param event       drag event
     * @param menuService owning menu service
     */
    void handleDrag(Player player, InventoryDragEvent event, MenuService menuService);

    /**
     * Called once when a managed menu closes normally or is forcibly closed.
     *
     * @param player    menu viewer
     * @param inventory closed inventory
     */
    void onClose(Player player, Inventory inventory);

    /**
     * Called once when the viewer quits before Bukkit emits a reliable close event.
     *
     * @param player    departing viewer
     * @param inventory last managed inventory
     */
    void onQuit(Player player, Inventory inventory);
}
