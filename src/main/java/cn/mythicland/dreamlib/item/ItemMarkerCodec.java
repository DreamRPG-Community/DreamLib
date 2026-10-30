package cn.mythicland.dreamlib.item;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Reads and writes plugin-neutral hidden item markers.
 *
 * <p>Implementations must not mutate caller-owned item stacks. Implementations may require the
 * Bukkit primary thread when they use a server-specific item representation.</p>
 */
public interface ItemMarkerCodec {

    /**
     * Returns a detached copy containing the marker.
     *
     * @param source item stack to copy
     * @param marker marker to write
     * @return detached marked item stack
     * @throws IllegalStateException if the server item bridge is unavailable
     */
    ItemStack write(ItemStack source, ItemMarker marker);

    /**
     * Reads one namespace from an item stack.
     *
     * @param source    item stack to inspect; null means no marker
     * @param namespace namespace to inspect
     * @return the marker when present and valid
     * @throws IllegalStateException if the server item bridge is unavailable
     */
    Optional<ItemMarker> read(ItemStack source, String namespace);
}
