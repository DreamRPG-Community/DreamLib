package cn.mythicland.dreamlib.text;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/**
 * Shared client-side floating-text service.
 *
 * <p>The implementation creates no persistent Bukkit entities and never adds an entity to a
 * world. The returned handle must be closed when the owning feature or location is removed.</p>
 */
public interface FloatingTextService extends AutoCloseable {

    /**
     * Verifies that the running server exposes the supported packet bridge.
     *
     * @throws IllegalStateException when the server is not compatible
     */
    void verifyCompatibility();

    /**
     * Shows text at a world location for nearby players.
     *
     * @param location      anchor location; it is defensively copied
     * @param specification text and display parameters
     * @return lifecycle handle
     * @throws NullPointerException     if {@code location} or {@code specification} is null
     * @throws IllegalArgumentException if the location has no world
     * @throws IllegalStateException    if called off the Bukkit primary thread or after close
     */
    FloatingTextHandle show(Location location, FloatingTextSpec specification);

    /**
     * Shows text only to nearby players accepted by a dynamically evaluated viewer filter.
     * The filter is evaluated on the Bukkit primary thread while the handle is active.
     *
     * @param location anchor location; it is defensively copied
     * @param specification text and display parameters
     * @param viewerFilter player eligibility, rechecked as viewers are refreshed
     * @return lifecycle handle
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the location has no world
     * @throws IllegalStateException if called off the primary thread or after close
     */
    FloatingTextHandle show(Location location, FloatingTextSpec specification, Predicate<Player> viewerFilter);

    /**
     * Closes every display and unregisters the service's runtime state.
     *
     * <p>Closing an already closed service is safe and has no effect.</p>
     *
     * @throws IllegalStateException if called off the Bukkit primary thread
     */
    @Override
    void close();
}
