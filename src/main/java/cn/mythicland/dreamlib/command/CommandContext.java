package cn.mythicland.dreamlib.command;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;

/**
 * Immutable input for an annotation-driven command handler or completer.
 */
public final class CommandContext {

    private final CommandSender sender;
    private final String rootCommand;
    private final String label;
    private final String usage;
    private final List<String> arguments;

    CommandContext(
            CommandSender sender,
            String rootCommand,
            String label,
            String usage,
            List<String> arguments
    ) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.rootCommand = Objects.requireNonNull(rootCommand, "rootCommand");
        this.label = Objects.requireNonNull(label, "label");
        this.usage = Objects.requireNonNull(usage, "usage");
        this.arguments = List.copyOf(arguments);
    }

    /**
     * Returns the command sender.
     *
     * @return sender
     */
    public CommandSender sender() {
        return sender;
    }

    /**
     * Returns the declared root command.
     *
     * @return root command
     */
    public String rootCommand() {
        return rootCommand;
    }

    /**
     * Returns the label used for this invocation.
     *
     * @return command label
     */
    public String label() {
        return label;
    }

    /**
     * Returns the usage line associated with the current handler.
     *
     * @return usage line
     */
    public String usage() {
        return usage;
    }

    /**
     * Returns immutable arguments after the current command name.
     *
     * @return command arguments
     */
    public List<String> arguments() {
        return arguments;
    }

    /**
     * Returns one argument.
     *
     * @param index zero-based argument index
     * @return argument value
     * @throws CommandUsageException when the argument is absent
     */
    public String argument(int index) {
        if (index < 0 || index >= arguments.size()) throw invalidUsage();
        return arguments.get(index);
    }

    /**
     * Requires an exact argument count.
     *
     * @param expected expected count
     * @throws CommandUsageException when the count does not match
     */
    public void requireArguments(int expected) {
        if (expected < 0 || arguments.size() != expected) throw invalidUsage();
    }

    /**
     * Requires at least a number of arguments.
     *
     * @param minimum minimum count
     * @throws CommandUsageException when too few arguments are present
     */
    public void requireAtLeast(int minimum) {
        if (minimum < 0 || arguments.size() < minimum) throw invalidUsage();
    }

    /**
     * Creates a usage failure for this handler.
     *
     * @return usage failure
     */
    public CommandUsageException invalidUsage() {
        return new CommandUsageException(usage);
    }
}
