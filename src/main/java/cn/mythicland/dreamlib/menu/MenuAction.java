package cn.mythicland.dreamlib.menu;

import org.bukkit.event.inventory.ClickType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method that handles clicks on one button in an {@link AnnotatedMenuView}.
 *
 * <p>An empty {@link #clicks()} value accepts every click type. Handler methods may accept any
 * combination of {@code Player}, {@code ClickType}, {@code InventoryClickEvent}, and
 * {@link MenuService} parameters. They may return {@code void} or {@code boolean}; a false boolean
 * result means that no success feedback should be played.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MenuAction {

    /**
     * Returns the top-inventory slot handled by the action.
     *
     * @return button slot
     */
    int slot();

    /**
     * Returns the accepted click types. An empty array accepts all click types.
     *
     * @return accepted click types
     */
    ClickType[] clicks() default {};

    /**
     * Returns whether the shared selection click sound should play after a successful action.
     *
     * @return true to play the shared selection sound
     */
    boolean playClickSound() default false;
}
