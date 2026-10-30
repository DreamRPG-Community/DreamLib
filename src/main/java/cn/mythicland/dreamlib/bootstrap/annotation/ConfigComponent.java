package cn.mythicland.dreamlib.bootstrap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a component whose configuration is managed by Lib.
 *
 * <p>The component must implement {@code ConfigurableComponent}. Lib loads all annotated
 * components before lifecycle components and reloads them before forwarding a plugin reload to
 * lifecycle components.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigComponent {
}
