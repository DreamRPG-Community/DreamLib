package cn.mythicland.dreamlib.bootstrap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an injected Bukkit command binding that Lib registers during plugin bootstrap.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandComponent {

    /**
     * Returns the command key declared in plugin.yml.
     *
     * @return command key declared in plugin.yml
     */
    String value();

    /**
     * Returns the default permission inherited by annotated handlers.
     *
     * @return default permission node
     */
    String permission() default "";

    /**
     * Defines ordered help groups as {@code key:display name}; empty keeps the standard help.
     *
     * @return help groups in display order
     */
    String[] helpGroups() default {};

    /**
     * Enables command-tree help and shows it for invalid usage.
     *
     * @return true for hierarchical help
     */
    boolean treeHelp() default false;

    /**
     * Indicates whether Lib should replace the unqualified global command mapping.
     *
     * @return true when the command must take over an existing global command
     */
    boolean takeOverGlobalMapping() default false;
}
