package cn.mythicland.dreamlib.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Safely reads legacy lore from Bukkit item stacks.
 */
public final class ItemLoreReader {

    private ItemLoreReader() {
    }

    /**
     * Returns a detached immutable lore list, or an empty list when the item has no lore.
     *
     * @param itemStack the item to inspect, or null
     * @return immutable lore lines
     */
    public static List<String> read(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return List.of();
        if (!itemStack.hasItemMeta()) return List.of();
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) return List.of();
        return List.copyOf(meta.getLore());
    }
}
