package cn.mythicland.dreamlib.policy;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, generic blacklist or whitelist matcher.
 *
 * @param <T> the value type stored in the list
 */
public record ListPolicy<T>(ListMode mode, Set<T> entries) {

    /**
     * Creates a list policy with a defensive immutable copy of its entries.
     *
     * @param mode    the matching mode
     * @param entries the values used by the mode
     * @throws NullPointerException if the mode, collection, or an entry is null
     */
    public ListPolicy(ListMode mode, Collection<? extends T> entries) {
        this(
                Objects.requireNonNull(mode, "mode"),
                Set.copyOf(Objects.requireNonNull(entries, "entries"))
        );
    }

    /**
     * Tests whether a value should be blocked by this policy.
     *
     * <p>A disabled policy never blocks. A blacklist blocks listed values. A
     * whitelist blocks values that are not listed.</p>
     *
     * @param value the value to test
     * @return true when the value should be blocked
     */
    public boolean blocks(T value) {
        if (mode == ListMode.DISABLED) return false;
        boolean listed = value != null && entries.contains(value);
        return (mode == ListMode.BLACKLIST) == listed;
    }

    /**
     * Tests whether a value is allowed by this policy.
     *
     * @param value the value to test
     * @return true when the value is allowed
     */
    public boolean allows(T value) {
        return !blocks(value);
    }

    /**
     * Returns the configured matching mode.
     *
     * @return the immutable mode
     */
    @Override
    public ListMode mode() {
        return mode;
    }

    /**
     * Returns the immutable values used by this policy.
     *
     * @return an immutable set of configured values
     */
    @Override
    public Set<T> entries() {
        return entries;
    }
}
