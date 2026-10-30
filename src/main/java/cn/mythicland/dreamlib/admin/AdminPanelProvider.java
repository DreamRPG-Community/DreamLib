package cn.mythicland.dreamlib.admin;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Provides one administrator panel for the shared Lib management menu.
 *
 * <p>Providers are deliberately expressed in terms of Bukkit primitives. Lib can therefore own
 * the entry-point menu without depending on any one business plugin. Implementations must keep
 * {@link #supports(Player, Block)} and {@link #open(Player, Block)} on Bukkit's primary thread.</p>
 */
public interface AdminPanelProvider {

    /**
     * Returns the stable provider identifier.
     *
     * @return non-blank ASCII-like identifier
     */
    String id();

    /**
     * Returns the button name in legacy color format.
     *
     * @return display name
     */
    String displayName();

    /**
     * Returns the legacy Bukkit material used as the button icon.
     *
     * @return button icon material
     */
    Material icon();

    /**
     * Returns optional legacy-format description lines for the button.
     *
     * @return immutable or mutable description lines; never {@code null}
     */
    List<String> description();

    /**
     * Returns whether this panel can manage the clicked block for the player.
     *
     * @param player administrator opening the panel
     * @param block  clicked block
     * @return true when this provider should be shown
     */
    boolean supports(Player player, Block block);

    /**
     * Opens this provider's setting panel.
     *
     * @param player administrator opening the panel
     * @param block  clicked block
     */
    void open(Player player, Block block);
}
