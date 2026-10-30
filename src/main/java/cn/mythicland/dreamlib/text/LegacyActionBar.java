package cn.mythicland.dreamlib.text;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * Sends legacy-coloured text through the Bukkit action bar API.
 */
public final class LegacyActionBar {

    private LegacyActionBar() {
    }

    /**
     * Sends an action bar message to an online player.
     *
     * @param player  the recipient
     * @param message legacy text using {@code &} or {@code §} colour codes
     */
    @SuppressWarnings("deprecation")
    public static void send(Player player, String message) {
        if (player == null || !player.isOnline() || message == null) return;
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(LegacyText.colorize(message))
        );
    }
}
