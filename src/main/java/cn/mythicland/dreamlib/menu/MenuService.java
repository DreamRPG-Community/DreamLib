package cn.mythicland.dreamlib.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Shared single-view inventory lifecycle and click protection for dependent plugins.
 */
public final class MenuService implements Listener, AutoCloseable {

    private final JavaPlugin owner;
    private final Map<UUID, MenuHolder> openMenus = new HashMap<>();

    /**
     * Creates and registers the shared menu listener.
     *
     * @param owner the plugin owning the listener lifecycle
     */
    public MenuService(JavaPlugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        owner.getServer().getPluginManager().registerEvents(this, owner);
    }

    /**
     * Opens a view for a player. Calls from an asynchronous thread are moved to the primary thread.
     * When the current managed inventory has the same size and title, the view is replaced in
     * place. A changed title opens a new window because Paper 1.12.2 cannot update it in place.
     *
     * @param player the viewer
     * @param view   the view to open
     */
    public void open(Player player, MenuView view) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(view, "view");
        if (!Bukkit.isPrimaryThread()) {
            owner.getServer().getScheduler().runTask(owner, () -> openNow(player, view));
            return;
        }
        openNow(player, view);
    }

    /**
     * Refreshes the current view without allowing inventory transfer operations.
     *
     * @param player the viewer
     */
    public void refresh(Player player) {
        if (player == null) return;
        if (!Bukkit.isPrimaryThread()) {
            owner.getServer().getScheduler().runTask(owner, () -> refresh(player));
            return;
        }
        MenuHolder holder = openMenus.get(player.getUniqueId());
        if (holder == null) return;
        refreshNow(player, holder);
    }

    /**
     * Closes the current menu for a player.
     *
     * @param player the viewer
     */
    public void close(Player player) {
        if (player == null) return;
        if (!Bukkit.isPrimaryThread()) {
            owner.getServer().getScheduler().runTask(owner, () -> close(player));
            return;
        }
        MenuHolder holder = openMenus.remove(player.getUniqueId());
        if (holder != null) notifyClose(player, holder);
        player.closeInventory();
    }

    /**
     * Returns whether the player currently has a Lib-managed menu open.
     *
     * @param playerUniqueId the player UUID
     * @return true when a managed menu is open
     */
    public boolean hasOpenMenu(UUID playerUniqueId) {
        return playerUniqueId != null && openMenus.containsKey(playerUniqueId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MenuHolder holder = holder(event.getView().getTopInventory());
        if (holder == null || !holder.viewerUniqueId.equals(player.getUniqueId())) return;
        if (event.isCancelled()) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0) return;
        holder.view.handleClick(player, event, this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MenuHolder holder = holder(event.getView().getTopInventory());
        if (holder == null || event.isCancelled()) return;
        event.setCancelled(true);
        if (holder.view instanceof StatefulMenuView statefulView) {
            statefulView.handleDrag(player, event, this);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        MenuHolder holder = holder(event.getInventory());
        if (holder == null) return;
        if (openMenus.remove(player.getUniqueId(), holder)) notifyClose(player, holder);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handleQuit(PlayerQuitEvent event) {
        MenuHolder holder = openMenus.remove(event.getPlayer().getUniqueId());
        if (holder != null && holder.view instanceof StatefulMenuView statefulView) {
            statefulView.onQuit(event.getPlayer(), holder.inventory);
        }
    }

    /**
     * Closes all managed views and releases the session registry.
     */
    @Override
    public void close() {
        if (!Bukkit.isPrimaryThread()) {
            owner.getServer().getScheduler().runTask(owner, this::close);
            return;
        }
        for (UUID playerUniqueId : Set.copyOf(openMenus.keySet())) {
            Player player = Bukkit.getPlayer(playerUniqueId);
            if (player != null) player.closeInventory();
        }
        openMenus.clear();
    }

    private void openNow(Player player, MenuView view) {
        MenuHolder current = openMenus.get(player.getUniqueId());
        if (current != null
                && current.isVisibleTo(player)
                && view.size(player) == current.inventory.getSize()
                && view.title(player).equals(current.inventory.getTitle())) {
            replaceNow(player, current, view);
            return;
        }
        close(player);

        int size = view.size(player);
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Menu size must be a multiple of nine between 9 and 54");
        }
        MenuHolder holder = new MenuHolder(player.getUniqueId(), view);
        Inventory inventory = Bukkit.createInventory(holder, size, view.title(player));
        holder.inventory = inventory;
        openMenus.put(player.getUniqueId(), holder);
        view.render(player, inventory);
        player.openInventory(inventory);
    }

    private void replaceNow(Player player, MenuHolder holder, MenuView view) {
        MenuView previousView = holder.view;
        holder.view = view;
        try {
            renderAtomically(player, view, holder.inventory);
            // Paper 1.12.2 has no supported in-place inventory-title update. Keeping this window
            // open is intentional: it preserves the client-side mouse position during navigation.
            player.updateInventory();
            notifyClose(player, previousView, holder.inventory);
        } catch (RuntimeException exception) {
            holder.view = previousView;
            openMenus.remove(player.getUniqueId(), holder);
            player.closeInventory();
            throw exception;
        }
    }

    private void refreshNow(Player player, MenuHolder holder) {
        int size = holder.view.size(player);
        if (size != holder.inventory.getSize()) {
            openNow(player, holder.view);
            return;
        }
        renderAtomically(player, holder.view, holder.inventory);
        player.updateInventory();
    }

    /**
     * Renders into a detached inventory before publishing the complete slot array to the visible
     * inventory. Calling {@code clear()} on the visible inventory first causes Paper 1.12.2 to
     * send a blank intermediate state; the client can display that state as a short gap while a
     * management panel is switching.
     */
    private void renderAtomically(Player player, MenuView view, Inventory target) {
        Inventory staging = Bukkit.createInventory(null, target.getSize(), view.title(player));
        view.render(player, staging);
        target.setContents(Arrays.copyOf(staging.getContents(), staging.getSize()));
    }

    private MenuHolder holder(Inventory inventory) {
        if (inventory == null) return null;
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof MenuHolder menuHolder ? menuHolder : null;
    }

    private void notifyClose(Player player, MenuHolder holder) {
        notifyClose(player, holder.view, holder.inventory);
    }

    private void notifyClose(Player player, MenuView view, Inventory inventory) {
        if (view instanceof StatefulMenuView statefulView) statefulView.onClose(player, inventory);
    }

    private static final class MenuHolder implements InventoryHolder {
        private final UUID viewerUniqueId;
        private MenuView view;
        private Inventory inventory;

        private MenuHolder(UUID viewerUniqueId, MenuView view) {
            this.viewerUniqueId = viewerUniqueId;
            this.view = view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private boolean isVisibleTo(Player player) {
            Inventory top = player.getOpenInventory().getTopInventory();
            return top == inventory || (top != null && top.getHolder() == this);
        }
    }
}
