package cn.mythicland.dreamlib.command;

import java.io.Serial;

/**
 * Signals that a command invocation has invalid arguments and should display its usage line.
 */
public final class CommandUsageException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String usage;

    /**
     * Creates a command usage failure.
     *
     * @param usage the usage line that should be shown to the sender
     */
    public CommandUsageException(String usage) {
        super("Invalid command usage: " + usage);
        this.usage = usage;
    }

    /**
     * Returns the usage line associated with this failure.
     *
     * @return the command usage line
     */
    public String usage() {
        return usage;
    }
}
