package cn.mythicland.dreamlib.bootstrap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * Restores Bukkit's previous unqualified command mapping after a plugin is disabled.
 */
final class GlobalCommandTakeover {

    private final String commandName;
    private final Map<String, Command> knownCommands;
    private Command pluginCommand;
    private Command previousCommand;
    private boolean installed;

    GlobalCommandTakeover(JavaPlugin plugin, String commandName) {
        Objects.requireNonNull(plugin, "plugin");
        this.commandName = Objects.requireNonNull(commandName, "commandName");
        this.knownCommands = resolveKnownCommands(resolveCommandMap(plugin));
    }

    private static CommandMap resolveCommandMap(JavaPlugin plugin) {
        try {
            Method method = plugin.getServer().getClass().getMethod("getCommandMap");
            Object value = method.invoke(plugin.getServer());
            if (!(value instanceof CommandMap commandMap)) {
                throw new IllegalStateException("Bukkit getCommandMap returned an incompatible value");
            }
            return commandMap;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalStateException("Cannot access Bukkit command map", exception);
        }
    }

    private static Map<String, Command> resolveKnownCommands(CommandMap commandMap) {
        Class<?> type = commandMap.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField("knownCommands");
                if (!field.trySetAccessible()) {
                    throw new IllegalStateException("Cannot access Bukkit knownCommands field");
                }
                Object value = field.get(commandMap);
                if (!(value instanceof Map<?, ?> rawMap)) {
                    throw new IllegalStateException("Bukkit knownCommands has an incompatible type");
                }
                @SuppressWarnings("unchecked")
                Map<String, Command> commands = (Map<String, Command>) rawMap;
                return commands;
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                throw new IllegalStateException("Cannot access Bukkit known command mappings", exception);
            }
        }
        throw new IllegalStateException("Bukkit command map does not expose knownCommands");
    }

    void install(PluginCommand command) {
        Objects.requireNonNull(command, "command");
        if (!installed) previousCommand = knownCommands.get(commandName);
        knownCommands.put(commandName, command);
        pluginCommand = command;
        installed = true;
    }

    void restore() {
        if (!installed) return;
        if (knownCommands.get(commandName) == pluginCommand) {
            if (previousCommand == null) knownCommands.remove(commandName);
            else knownCommands.put(commandName, previousCommand);
        }
        installed = false;
        pluginCommand = null;
        previousCommand = null;
    }
}
