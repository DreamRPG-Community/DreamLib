package cn.mythicland.dreamlib.bootstrap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one method as a command action discovered by Lib.
 *
 * <p>The method must have the shape {@code void method(CommandContext)}. An empty value marks the
 * root action invoked when the command has no arguments; other values may use a whitespace
 * separated nested path such as {@code "cmd add"}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandHandler {

    /**
     * Returns the command path, or an empty value for the root action.
     *
     * @return command name
     */
    String value() default "";

    /**
     * Returns the usage line. Lib generates a root-command usage when this is blank.
     *
     * @return usage line
     */
    String usage() default "";

    /**
     * Returns a method-specific permission. A blank value inherits the component permission when
     * {@link #inheritPermission()} is true.
     *
     * @return permission node
     */
    String permission() default "";

    /**
     * Indicates whether a blank method permission inherits {@code @CommandComponent.permission}.
     *
     * @return true to inherit the component permission
     */
    boolean inheritPermission() default true;

    /**
     * Returns an optional no-argument component method that supplies a dynamic permission.
     *
     * @return permission method name, or blank for a static permission
     */
    String permissionMethod() default "";

    /**
     * Returns aliases for this subcommand.
     *
     * @return command aliases
     */
    String[] aliases() default {};

    /**
     * Returns a short description displayed by grouped help.
     *
     * @return command description, or blank when omitted
     */
    String description() default "";

    /**
     * Returns the help group key when the command component enables grouped help.
     *
     * @return group key, or blank for an ungrouped command
     */
    String helpGroup() default "";

    /**
     * Indicates whether the command is shown in hierarchical help.
     *
     * @return false for compatibility-only command paths
     */
    boolean showInHelp() default true;

    /**
     * Returns the stable order among annotated handlers.
     *
     * @return lower values first
     */
    int order() default 0;
}
