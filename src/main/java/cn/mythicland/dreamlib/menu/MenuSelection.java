package cn.mythicland.dreamlib.menu;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reusable lore-based selection control for legacy inventory menus.
 *
 * <p>The control follows the MythicThePit settings convention: the selected option is green and
 * starts with a triangle, while unselected options are gray. Plain left and right clicks move to
 * the next and previous option respectively.</p>
 */
public final class MenuSelection {

    /**
     * Paper 1.12.2 does not expose the legacy {@code Sound.CLICK} enum. This stone-button sound
     * is the short legacy click and remains translatable for 1.8 clients through ViaVersion.
     */
    private static final Sound CLICK_SOUND = Sound.BLOCK_STONE_BUTTON_CLICK_ON;
    private static final float CLICK_VOLUME = 1.0F;
    private static final float CLICK_PITCH = 2.0F;

    private MenuSelection() {
    }

    /**
     * Creates a selection item from an existing icon without mutating the supplied item.
     *
     * @param icon          item shown by the menu
     * @param title         item display name in legacy format
     * @param description   description shown before the options
     * @param options       displayed option names
     * @param selectedIndex selected option index
     * @param details       optional detail lines shown after the options
     * @return a configured item stack
     */
    public static ItemStack item(
            ItemStack icon,
            String title,
            String description,
            List<String> options,
            int selectedIndex,
            List<String> details
    ) {
        Objects.requireNonNull(icon, "icon");
        List<String> lore = lore(description, options, selectedIndex, details);
        return MenuItems.button(icon, title, lore);
    }

    /**
     * Creates a selection item with a material icon.
     *
     * @param material      item material
     * @param title         item display name in legacy format
     * @param description   description shown before the options
     * @param options       displayed option names
     * @param selectedIndex selected option index
     * @param details       optional detail lines shown after the options
     * @return a configured item stack
     */
    public static ItemStack item(
            Material material,
            String title,
            String description,
            List<String> options,
            int selectedIndex,
            List<String> details
    ) {
        return item(new ItemStack(Objects.requireNonNull(material, "material")), title, description,
                options, selectedIndex, details);
    }

    /**
     * Creates a selection item with a legacy material data value.
     *
     * @param material      item material
     * @param durability    legacy material data value
     * @param title         item display name in legacy format
     * @param description   description shown before the options
     * @param options       displayed option names
     * @param selectedIndex selected option index
     * @param details       optional detail lines shown after the options
     * @return a configured item stack
     */
    public static ItemStack item(
            Material material,
            short durability,
            String title,
            String description,
            List<String> options,
            int selectedIndex,
            List<String> details
    ) {
        return item(new ItemStack(Objects.requireNonNull(material, "material"), 1, durability), title, description,
                options, selectedIndex, details);
    }

    /**
     * Builds the complete lore for a selection item.
     *
     * @param description   description shown before the options
     * @param options       displayed option names
     * @param selectedIndex selected option index
     * @param details       optional detail lines shown after the options
     * @return immutable legacy-format lore lines
     */
    public static List<String> lore(
            String description,
            List<String> options,
            int selectedIndex,
            List<String> details
    ) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(details, "details");
        if (options.isEmpty()) throw new IllegalArgumentException("Selection options cannot be empty");
        if (selectedIndex < 0 || selectedIndex >= options.size()) {
            throw new IndexOutOfBoundsException("Selection index is outside the options: " + selectedIndex);
        }
        List<String> lore = new ArrayList<>(options.size() + details.size() + 4);
        lore.add("&7" + description);
        lore.add("");
        for (int index = 0; index < options.size(); index++) {
            lore.add(optionLine(Objects.requireNonNull(options.get(index), "option"), index == selectedIndex));
        }
        if (!details.isEmpty()) {
            lore.add("");
            for (String detail : details) lore.add(Objects.requireNonNull(detail, "detail"));
        }
        lore.add("");
        lore.add("&e点击切换!");
        return List.copyOf(lore);
    }

    /**
     * Formats one selected or unselected option line.
     *
     * @param option   option display name
     * @param selected whether the option is selected
     * @return legacy-format option line
     */
    public static String optionLine(String option, boolean selected) {
        Objects.requireNonNull(option, "option");
        return selected ? "&a▶ &a" + option : "&7  &7" + option;
    }

    /**
     * Returns whether a click is a plain selection click.
     *
     * @param click inventory click type
     * @return true for plain left and right clicks
     */
    public static boolean isCycleClick(ClickType click) {
        return direction(click) != 0;
    }

    /**
     * Returns the selection direction for a click.
     *
     * @param click inventory click type
     * @return {@code 1} for left, {@code -1} for right, or {@code 0} otherwise
     */
    public static int direction(ClickType click) {
        if (click == null) return 0;
        return switch (click) {
            case LEFT -> 1;
            case RIGHT -> -1;
            default -> 0;
        };
    }

    /**
     * Plays the shared legacy-compatible selection feedback sound at twice the normal pitch.
     *
     * <p>The sound is played only after the caller has successfully changed its setting.</p>
     *
     * @param player player receiving the sound
     */
    public static void playClickSound(Player player) {
        Objects.requireNonNull(player, "player");
        player.playSound(player.getLocation(), CLICK_SOUND, CLICK_VOLUME, CLICK_PITCH);
    }
}
