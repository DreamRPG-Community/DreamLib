package cn.mythicland.dreamlib.integration;

import org.bukkit.entity.Player;

/**
 * Optional PlaceholderAPI rendering boundary.
 */
public interface PlaceholderService {

    /**
     * Returns whether PlaceholderAPI's public rendering method is usable.
     *
     * @return true when PlaceholderAPI is available
     */
    boolean isAvailable();

    /**
     * Replaces third-party placeholders in one rendered text.
     *
     * @param player player context
     * @param text   text containing PlaceholderAPI tokens
     * @return rendered text
     */
    String render(Player player, String text);
}
