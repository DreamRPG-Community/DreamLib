package cn.mythicland.dreamlib.bootstrap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an injected Bukkit listener that Lib registers during plugin bootstrap.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ListenerComponent {
}
