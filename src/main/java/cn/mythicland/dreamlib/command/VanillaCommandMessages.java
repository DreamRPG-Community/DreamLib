package cn.mythicland.dreamlib.command;

import org.bukkit.ChatColor;

public final class VanillaCommandMessages {

    private VanillaCommandMessages() {
    }

    /**
     * Formats a message as a red error or denial.
     *
     * @param message the message body
     * @return the colored message
     */
    public static String red(String message) {
        return ChatColor.RED + message;
    }

    /**
     * Formats a message as a yellow informational prompt.
     *
     * @param message the message body
     * @return the colored message
     */
    public static String yellow(String message) {
        return ChatColor.YELLOW + message;
    }

    /**
     * Formats a message as a green success prompt.
     *
     * @param message the message body
     * @return the colored message
     */
    public static String green(String message) {
        return ChatColor.GREEN + message;
    }

    /**
     * Formats a message with the default white chat color.
     *
     * @param message message body
     * @return the colored message
     */
    public static String white(String message) {
        return ChatColor.WHITE + message;
    }

    /**
     * Formats every line of a usage block with a dark-red arrow and red command.
     *
     * @param usage the command usage block without localized usage prefixes
     * @return the colored usage block
     */
    public static String usage(String usage) {
        return usage.lines()
                .map(line -> ChatColor.DARK_RED + "> " + ChatColor.RED + line)
                .reduce((first, second) -> first + "\n" + second)
                .orElseGet(() -> ChatColor.DARK_RED + "> " + ChatColor.RED);
    }
}
