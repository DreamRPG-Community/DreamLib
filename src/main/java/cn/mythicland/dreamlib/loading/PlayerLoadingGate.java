package cn.mythicland.dreamlib.loading;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared loading-state restriction and visual-effect lifecycle for dependent plugins.
 */
public final class PlayerLoadingGate implements Listener, AutoCloseable {

    private static final int JUMP_AMPLIFIER = 128;
    private static final int SLOW_AMPLIFIER = 4;
    private static final int EFFECT_DURATION_TICKS = Integer.MAX_VALUE;
    private static final long EFFECT_REFRESH_TICKS = 10L;

    private final Map<UUID, Map<PotionEffectType, PotionEffect>> previousEffects = new HashMap<>();
    private final Map<UUID, Boolean> loadingPlayers = new HashMap<>();
    private final BukkitTask effectTask;
    private boolean closed;

    /**
     * Creates and registers the shared loading gate.
     *
     * @param owner Lib plugin owning the task and listener
     */
    public PlayerLoadingGate(JavaPlugin owner) {
        Objects.requireNonNull(owner, "owner");
        owner.getServer().getPluginManager().registerEvents(this, owner);
        this.effectTask = owner.getServer().getScheduler().runTaskTimer(
                owner,
                this::refreshEffects,
                0L,
                EFFECT_REFRESH_TICKS
        );
    }

    private static void rememberEffect(
            Player player,
            PotionEffectType type,
            Map<PotionEffectType, PotionEffect> originals
    ) {
        PotionEffect existing = player.getPotionEffect(type);
        if (existing != null) originals.put(type, existing);
    }

    private static void applyEffects(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                EFFECT_DURATION_TICKS,
                0,
                false,
                false
        ), true);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP,
                EFFECT_DURATION_TICKS,
                JUMP_AMPLIFIER,
                false,
                false
        ), true);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW,
                EFFECT_DURATION_TICKS,
                SLOW_AMPLIFIER,
                false,
                false
        ), true);
    }

    private static void ensurePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Loading gate requires the main thread");
    }

    /**
     * Begins loading restrictions for one player.
     *
     * @param player player entering the loading state
     */
    public void begin(Player player) {
        begin(player, true);
    }

    /**
     * Restricts interactions during an asynchronous inventory commit without changing potion effects.
     * Must be paired with ready or cancel on the main thread. The player is restricted but isLoading
     * remains false, so ordinary loading presentation is not applied.
     *
     * @param player player whose inventory commit is pending
     */
    public void beginQuietly(Player player) {
        begin(player, false);
    }

    private void begin(Player player, boolean visualEffects) {
        ensurePrimaryThread();
        if (closed) throw new IllegalStateException("Player loading gate is closed");
        Player target = Objects.requireNonNull(player, "player");
        UUID uniqueId = target.getUniqueId();
        if (loadingPlayers.containsKey(uniqueId)) {
            throw new IllegalStateException("Player is already loading: " + uniqueId);
        }
        loadingPlayers.put(uniqueId, visualEffects);
        if (visualEffects) {
            Map<PotionEffectType, PotionEffect> originals = new HashMap<>();
            rememberEffect(target, PotionEffectType.BLINDNESS, originals);
            rememberEffect(target, PotionEffectType.JUMP, originals);
            rememberEffect(target, PotionEffectType.SLOW, originals);
            previousEffects.put(uniqueId, originals);
            applyEffects(target);
        }
    }

    /**
     * Marks a player ready and restores the effects that existed before loading.
     *
     * @param player player leaving the loading state
     */
    public void ready(Player player) {
        finish(Objects.requireNonNull(player, "player"));
    }

    /**
     * Cancels loading and restores the previous player effects.
     *
     * @param player player leaving the server or failing to load
     */
    public void cancel(Player player) {
        finish(Objects.requireNonNull(player, "player"));
    }

    /**
     * Returns whether a player is currently locked by loading.
     *
     * @param uniqueId player UUID
     * @return true when player actions are restricted
     */
    public boolean isLoading(UUID uniqueId) {
        return uniqueId != null && Boolean.TRUE.equals(loadingPlayers.get(uniqueId));
    }

    /**
     * Checks both visual loading and quiet commit restrictions on the main thread.
     *
     * @param player player to inspect
     * @return whether inventory and gameplay interactions are currently restricted
     */
    public boolean isRestricted(Player player) {
        return player != null && loadingPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Returns whether a player is currently locked by loading.
     *
     * @param player player to inspect
     * @return true when player actions are restricted
     */
    public boolean isLoading(Player player) {
        return player != null && isLoading(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!isRestricted(event.getPlayer())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        Location frozen = from.clone();
        frozen.setYaw(to.getYaw());
        frozen.setPitch(to.getPitch());
        event.setTo(frozen);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isRestricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isRestricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isRestricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeldItem(PlayerItemHeldEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isRestricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player && isRestricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        effectTask.cancel();
        for (UUID uniqueId : Map.copyOf(loadingPlayers).keySet()) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player != null) finish(player);
        }
        loadingPlayers.clear();
        previousEffects.clear();
        HandlerList.unregisterAll(this);
    }

    private void refreshEffects() {
        if (closed) return;
        for (UUID uniqueId : Map.copyOf(loadingPlayers).keySet()) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player != null && player.isOnline() && isLoading(uniqueId)) applyEffects(player);
        }
    }

    private void finish(Player player) {
        ensurePrimaryThread();
        UUID uniqueId = player.getUniqueId();
        if (!loadingPlayers.containsKey(uniqueId)) return;
        boolean visualEffects = Boolean.TRUE.equals(loadingPlayers.remove(uniqueId));
        if (!visualEffects) return;
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.JUMP);
        player.removePotionEffect(PotionEffectType.SLOW);
        Map<PotionEffectType, PotionEffect> originals = previousEffects.remove(uniqueId);
        if (originals == null) return;
        for (PotionEffect effect : originals.values()) player.addPotionEffect(effect, true);
    }
}
