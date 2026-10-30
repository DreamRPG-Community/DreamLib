package cn.mythicland.dreamlib.menu;

import cn.mythicland.dreamlib.text.LegacyText;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Objects;

/**
 * Shared item templates for Lib-managed inventory menus.
 */
public final class MenuItems {

    private MenuItems() {
    }

    /**
     * Creates a plain menu button from an existing icon without mutating the supplied item.
     *
     * @param icon  item shown by the menu
     * @param title item display name in legacy format
     * @param lore  optional lore in legacy format
     * @return a configured item stack
     */
    public static ItemStack button(
            ItemStack icon,
            String title,
            List<String> lore
    ) {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lore, "lore");
        ItemStack result = icon.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("Menu icon does not support item metadata: " + result.getType());
        }
        meta.setDisplayName(LegacyText.colorize(title));
        meta.setLore(lore.isEmpty() ? null : LegacyText.colorize(lore));
        meta.addItemFlags(ItemFlag.values());
        result.setItemMeta(meta);
        return result;
    }

    /**
     * Creates a detached preview with vanilla attribute, enchantment and other tooltip sections hidden.
     * Client-controlled advanced tooltips cannot be hidden by legacy server item flags.
     *
     * @param icon source item, which is not mutated
     * @return a preview retaining its configured name, lore and identity
     */
    public static ItemStack icon(ItemStack icon) {
        ItemStack result = Objects.requireNonNull(icon, "icon").clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("Menu icon does not support item metadata: " + result.getType());
        meta.addItemFlags(ItemFlag.values());
        result.setItemMeta(meta);
        return result;
    }

    /**
     * Creates a plain menu button from a material.
     *
     * @param material item material
     * @param title    item display name in legacy format
     * @param lore     optional lore in legacy format
     * @return a configured item stack
     */
    public static ItemStack button(
            Material material,
            String title,
            List<String> lore
    ) {
        return button(new ItemStack(Objects.requireNonNull(material, "material")), title, lore);
    }

    /**
     * Creates a plain menu button from a legacy material data value.
     *
     * @param material   item material
     * @param durability legacy material data value
     * @param title      item display name in legacy format
     * @param lore       optional lore in legacy format
     * @return a configured item stack
     */
    public static ItemStack button(
            Material material,
            short durability,
            String title,
            List<String> lore
    ) {
        return button(new ItemStack(Objects.requireNonNull(material, "material"), 1, durability), title, lore);
    }
}
