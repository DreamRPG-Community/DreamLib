package cn.mythicland.dreamlib.bootstrap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an injected {@code DreamLibPluginLifecycle} and declares its bootstrap ordering metadata.
 *
 * <p>Lifecycle components are enabled after their declared dependencies and before services,
 * listeners, and commands are registered. The annotation itself is optional for a lifecycle
 * component; it is useful when a plugin needs explicit ordering or wants to make the role clear
 * at the class declaration.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LifecycleComponent {

    /**
     * Returns the relative order among otherwise independent lifecycle components.
     *
     * @return lower values first
     */
    int order() default 0;

    /**
     * Returns lifecycle components that must be enabled first.
     *
     * @return lifecycle dependencies
     */
    Class<?>[] dependsOn() default {};
}
