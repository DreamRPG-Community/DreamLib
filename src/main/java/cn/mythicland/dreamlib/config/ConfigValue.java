package cn.mythicland.dreamlib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps one record component to a path in a plugin's {@code config.yml}.
 *
 * <p>The annotation-driven binder supports strings, booleans, numeric primitives and wrappers,
 * enums, and parameterized lists of those scalar values. The declared default is used in memory
 * when a value is missing or invalid; Lib logs invalid configured values and never writes a reload
 * snapshot back to disk. String values are trimmed by default, with {@link #trim()} available for
 * passwords and formatted text.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
public @interface ConfigValue {

    /**
     * Returns the YAML path.
     *
     * @return configuration path
     */
    String path();

    /**
     * Returns the textual default converted according to the component type.
     *
     * @return textual default value
     */
    String defaultValue() default "";

    /**
     * Requires a non-blank string value.
     *
     * @return true when blank strings are invalid
     */
    boolean nonBlank() default false;

    /**
     * Trims string values before validation and publication.
     *
     * @return true when string values should be trimmed
     */
    boolean trim() default true;

    /**
     * Requires numeric values to be greater than zero.
     *
     * @return true when zero and negative values are invalid
     */
    boolean positive() default false;

    /**
     * Requires numeric values to be zero or greater.
     *
     * @return true when negative values are invalid
     */
    boolean nonNegative() default false;

    /**
     * Requires floating-point values to be finite.
     *
     * @return true when NaN and infinity are invalid
     */
    boolean finite() default true;
}
