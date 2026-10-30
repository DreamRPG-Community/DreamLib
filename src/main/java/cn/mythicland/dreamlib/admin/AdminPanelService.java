package cn.mythicland.dreamlib.admin;

import cn.mythicland.dreamlib.container.ContainerAnimationHandle;
import cn.mythicland.dreamlib.container.ContainerAnimationService;
import cn.mythicland.dreamlib.container.ContainerAnimationSpec;
import cn.mythicland.dreamlib.menu.MenuItems;
import cn.mythicland.dreamlib.menu.MenuService;
import cn.mythicland.dreamlib.menu.MenuView;
import cn.mythicland.dreamlib.text.LegacyText;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

/**
 * Shared administrator panel registry and Shift+right-click entry point.
 *
 * <p>Dependent plugins register providers during their lifecycle. When one applicable provider is
 * available, Lib opens it directly. When more than one is applicable, Lib opens a small selection
 * menu first. The same selection menu is used by panel return buttons, so dependent plugins do not
 * need to know about one another.</p>
 */
public final class AdminPanelService implements Listener, AutoCloseable {

    private static final int[] BUTTON_SLOTS = {10, 11, 19, 20, 28, 29, 37, 38};

    private final JavaPlugin owner;
    private final MenuService menus;
    private final ContainerAnimationService animations;
    private final Map<String, AdminPanelProvider> providers = new LinkedHashMap<>();
    private final Map<java.util.UUID, AnimationSession> animationsByViewer = new LinkedHashMap<>();
    private boolean closed;

    /**
     * Creates and registers the Lib-owned administrator panel listener.
     *
     * @param owner      the Lib plugin owning the listener
     * @param menus      shared menu lifecycle
     * @param animations shared container animation lifecycle
     */
    public AdminPanelService(
            JavaPlugin owner,
            MenuService menus,
            ContainerAnimationService animations
    ) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.animations = Objects.requireNonNull(animations, "animations");
        owner.getServer().getPluginManager().registerEvents(this, owner);
    }

    private static String requireText(String value, String field) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return text;
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Administrator panel operations must run on Bukkit's primary thread");
        }
    }

    /**
     * Registers one provider.
     *
     * @param provider provider to register
     * @return a handle that unregisters this exact provider
     */
    public AdminPanelRegistration register(AdminPanelProvider provider) {
        Objects.requireNonNull(provider, "provider");
        requirePrimaryThread();
        if (closed) throw new IllegalStateException("Admin panel service is closed");
        String id = requireText(provider.id(), "provider.id");
        requireText(provider.displayName(), "provider.displayName");
        Objects.requireNonNull(provider.icon(), "provider.icon");
        List<String> description = Objects.requireNonNull(provider.description(), "provider.description");
        for (String line : description) Objects.requireNonNull(line, "provider.description line");
        if (providers.putIfAbsent(id, provider) != null) {
            throw new IllegalStateException("Duplicate administrator panel provider: " + id);
        }
        return new Registration(this, id, provider);
    }

    /**
     * Checks whether a player can choose between multiple panels for a block.
     *
     * @param player administrator
     * @param block block being managed
     * @return true when at least two providers apply
     * @throws IllegalStateException when called outside the primary thread
     * @throws NullPointerException when an argument is null
     */
    public boolean hasMultiplePanels(Player player, Block block) {
        requirePrimaryThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(block, "block");
        return applicable(player, block).size() > 1;
    }

    /**
     * Opens the panel appropriate for a Shift+right-click interaction.
     *
     * @param player administrator
     * @param block  clicked block
     * @return true when at least one provider accepted the interaction
     */
    public boolean openForInteraction(Player player, Block block) {
        requirePrimaryThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(block, "block");
        List<AdminPanelProvider> applicable = applicable(player, block);
        if (applicable.isEmpty()) return false;
        if (applicable.size() == 1) {
            openProvider(player, block, applicable.getFirst().id());
        } else {
            menus.open(player, new SelectionMenu(this, block, applicable));
            if (menus.hasOpenMenu(player.getUniqueId())) ensureAnimation(player, block);
        }
        return true;
    }

    /**
     * Opens the shared root menu for a panel's return button.
     *
     * <p>A root menu is meaningful when at least two providers are installed. With one provider,
     * returning simply closes the current setting panel.</p>
     *
     * @param player administrator
     * @param block  block whose panels are being managed
     */
    public void openOverview(Player player, Block block) {
        requirePrimaryThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(block, "block");
        if (providers.size() < 2) {
            menus.close(player);
            return;
        }
        List<AdminPanelProvider> applicable = applicable(player, block);
        if (applicable.isEmpty()) {
            menus.close(player);
            return;
        }
        menus.open(player, new SelectionMenu(this, block, applicable));
    }

    /**
     * Opens a provider selected from the shared menu, rechecking that it is still applicable.
     */
    public void openProvider(Player player, Block block, String providerId) {
        requirePrimaryThread();
        AdminPanelProvider provider = providers.get(providerId);
        if (provider == null || !provider.supports(player, block)) {
            openForInteraction(player, block);
            return;
        }
        try {
            provider.open(player, block);
            if (menus.hasOpenMenu(player.getUniqueId())) ensureAnimation(player, block);
        } catch (RuntimeException exception) {
            menus.close(player);
            owner.getLogger().log(
                    Level.SEVERE,
                    "Failed to open administrator panel: " + provider.id(),
                    exception
            );
            player.sendMessage(LegacyText.colorize("&c管理面板打开失败, 请查看服务端日志。"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        Block block = event.getClickedBlock();
        if (block == null || !openForInteraction(player, block)) return;
        event.setCancelled(true);
    }

    /**
     * Delays animation cleanup by one tick so a menu transition can replace the closed view
     * without producing a close/open packet pair.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        java.util.UUID viewer = player.getUniqueId();
        if (!animationsByViewer.containsKey(viewer)) return;
        owner.getServer().getScheduler().runTask(owner, () -> {
            if (closed || menus.hasOpenMenu(viewer)) return;
            closeAnimation(viewer);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        closeAnimation(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        closeAnimation(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        closeAnimation(event.getPlayer().getUniqueId());
    }

    /**
     * Unregisters the listener and drops all provider references.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (java.util.UUID viewer : List.copyOf(animationsByViewer.keySet())) closeAnimation(viewer);
        providers.clear();
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    private void ensureAnimation(Player player, Block block) {
        java.util.UUID viewer = player.getUniqueId();
        AnimationSession current = animationsByViewer.get(viewer);
        if (current != null && current.matches(block)) return;
        if (current != null) closeAnimation(viewer);
        ContainerAnimationHandle handle = animations.open(
                block,
                player,
                ContainerAnimationSpec.enderChest()
        );
        animationsByViewer.put(viewer, new AnimationSession(block, handle));
    }

    private void closeAnimation(java.util.UUID viewer) {
        AnimationSession session = animationsByViewer.remove(viewer);
        if (session != null) session.handle().close();
    }

    private List<AdminPanelProvider> applicable(Player player, Block block) {
        List<AdminPanelProvider> result = new ArrayList<>();
        for (AdminPanelProvider provider : providers.values()) {
            if (provider.supports(player, block)) result.add(provider);
        }
        result.sort(Comparator.comparing(AdminPanelProvider::id));
        return List.copyOf(result);
    }

    private void unregister(String id, AdminPanelProvider provider) {
        requirePrimaryThread();
        providers.remove(id, provider);
    }

    private static final class Registration implements AdminPanelRegistration {
        private final AdminPanelService service;
        private final String id;
        private final AdminPanelProvider provider;
        private boolean closed;

        private Registration(AdminPanelService service, String id, AdminPanelProvider provider) {
            this.service = service;
            this.id = id;
            this.provider = provider;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            service.unregister(id, provider);
        }
    }

    private record AnimationSession(
            java.util.UUID worldId,
            int x,
            int y,
            int z,
            ContainerAnimationHandle handle
    ) {

        private AnimationSession(Block block, ContainerAnimationHandle handle) {
            this(
                    Objects.requireNonNull(block, "block").getWorld().getUID(),
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    Objects.requireNonNull(handle, "handle")
            );
        }

        private boolean matches(Block block) {
            return block != null
                    && block.getWorld() != null
                    && worldId.equals(block.getWorld().getUID())
                    && x == block.getX()
                    && y == block.getY()
                    && z == block.getZ();
        }
    }

    private record SelectionMenu(AdminPanelService service, Block block,
                                 List<AdminPanelProvider> providers) implements MenuView {
        private SelectionMenu(
                AdminPanelService service,
                Block block,
                List<AdminPanelProvider> providers
        ) {
            this.service = service;
            this.block = block;
            this.providers = providers;
            if (providers.size() > BUTTON_SLOTS.length) {
                throw new IllegalArgumentException("Too many administrator panel providers: " + providers.size());
            }
        }

        @Override
        public String title(Player player) {
            return LegacyText.colorize("&8总管理面板");
        }

        @Override
        public int size(Player player) {
            return providers.size() > 4 ? 54 : 27;
        }

        @Override
        public void render(Player player, Inventory inventory) {
            inventory.clear();
            for (int index = 0; index < providers.size(); index++) {
                AdminPanelProvider provider = providers.get(index);
                inventory.setItem(
                        BUTTON_SLOTS[index],
                        MenuItems.button(
                                provider.icon(),
                                provider.displayName(),
                                provider.description()
                        )
                );
            }
        }

        @Override
        public void handleClick(Player player, InventoryClickEvent event, MenuService menuService) {
            int slot = event.getRawSlot();
            for (int index = 0; index < providers.size(); index++) {
                if (BUTTON_SLOTS[index] != slot) continue;
                service.openProvider(player, block, providers.get(index).id());
                return;
            }
        }
    }
}
