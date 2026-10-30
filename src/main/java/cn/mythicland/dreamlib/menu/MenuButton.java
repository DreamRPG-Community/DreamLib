package cn.mythicland.dreamlib.menu;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method that renders one button in an {@link AnnotatedMenuView}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MenuButton {

    /**
     * Returns the top-inventory slot occupied by the button.
     *
     * @return button slot
     */
    int slot();
}
